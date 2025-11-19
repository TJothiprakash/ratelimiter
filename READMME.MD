
# ⚡️ Rate Limiter Using Redis (Java)

A production-grade rate limiter implemented in **Java + Redis**, supporting three popular algorithms:

* **Fixed Window Counter**
* **Sliding Window Log**
* **Token Bucket (Leaky Bucket variant — recommended)**

This project demonstrates the design, implementation, and trade-offs of different rate limiting techniques used in real-world distributed systems (API gateways, microservices, proxies, load balancers, etc.).

---

## 🚀 Features

* Lightweight and fast
* No additional service hops — rate limiter is used as a **library**
* Redis-backed, atomic, and scalable
* Configurable limits per key (user-id, IP, token, etc.)
* Clean interface to plug into any Java/Spring service

---

## 📁 Project Structure

```
src/main/java/com/jp/ratelimiter/
 ├── RateLimiter.java
 ├── FixedWindowRateLimiter.java
 ├── SlidingWindowLogRateLimiter.java
 ├── LeakyBucketRateLimiter.java
 ├── RedisClientFactory.java
 └── DemoApp.java
```

---

## 🧠 Algorithms Implemented

### 1️⃣ Fixed Window Counter

A simple counter reset every fixed time window.

**Pros**

* Very fast (`INCR` + `EXPIRE`)
* Scales to millions of RPS
* Works well for distributed systems

**Cons**

* Boundary burst problem
  (Requests at the end + beginning of consecutive windows)

---

### 2️⃣ Sliding Window Log

Uses Redis **sorted sets** to store timestamps of requests.

**Pros**

* Very accurate (true rolling window)
* Suitable for fine-grained rate limits

**Cons**

* More expensive operations (`ZADD`, `ZCARD`, `ZREMRANGEBYSCORE`)
* Not ideal for extremely high RPS

---

### 3️⃣ Token Bucket (Leaky Bucket Variant) — ⭐ Recommended

Adds tokens at a fixed rate, consumes 1 token per request.

**Pros**

* Smooth traffic flow
* Prevents bursts elegantly
* Only **one Lua script** per request → O(1)
* Used by Cloudflare, AWS, Google API Gateway, Stripe

**Cons**

* Slightly more complex than fixed window

---

## 🛠️ Redis Setup (Local)

Run Redis using Docker:

```bash
docker run -p 6379:6379 redis:6
```

Or install locally on Windows/Mac/Linux.

---

## 🔌 Jedis Setup

Add to `pom.xml`:

```xml
<dependency>
    <groupId>redis.clients</groupId>
    <artifactId>jedis</artifactId>
    <version>5.1.0</version>
</dependency>
```

---

## 🧪 Testing the Rate Limiter

Use the provided `DemoApp`:

```java
public class DemoApp {
    public static void main(String[] args) throws Exception {
        JedisPool pool = RedisClientFactory.getPool();

        RateLimiter limiter = new FixedWindowRateLimiter(pool, 5, 10);

        for (int i = 1; i <= 10; i++) {
            boolean allowed = limiter.allow("user123");
            System.out.println("Request " + i + " => " + (allowed ? "ALLOWED" : "BLOCKED"));
            Thread.sleep(300);
        }
    }
}
```

Run:

```
mvn clean package
java -jar target/ratelimiter-*.jar
```

---

## 📊 Redis Keys

### Fixed Window Counter

```
rl:fixed:{user}:{windowStart}
Value: request count
TTL: windowSeconds
```

### Sliding Window Log

```
rl:sliding:{user}
Value: sorted timestamps
```

### Token Bucket (Leaky Bucket)

```
rl:lb:{user}:tokens
rl:lb:{user}:ts
```

---

## 📈 Performance Notes

| Algorithm    | Redis Ops / Request | Speed     | Accuracy | Scalability |
| ------------ | ------------------- | --------- | -------- | ----------- |
| Fixed Window | 1–2                 | ⭐ Fastest | Medium   | ⭐⭐⭐ High    |
| Sliding Log  | 3–4                 | Medium    | ⭐⭐⭐ Best | Low–Medium  |
| Token Bucket | 1 (Lua)             | ⭐⭐ Fast   | ⭐⭐ Good  | ⭐⭐ High     |

**For production systems → Use Token Bucket or Fixed Window.**

---

## 🧩 Integrating With Spring Boot

Add a filter:

```java
@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private final RateLimiter limiter;

    public RateLimitingFilter() {
        this.limiter = new FixedWindowRateLimiter(
                RedisClientFactory.getPool(), 100, 60
        );
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req,
                                    HttpServletResponse res,
                                    FilterChain chain)
            throws IOException, ServletException {

        String userId = req.getHeader("X-User-Id");

        if (!limiter.allow(userId)) {
            res.setStatus(429);
            res.getWriter().write("Too Many Requests");
            return;
        }

        chain.doFilter(req, res);
    }
}
```

---

## 🧭 Trade-Offs & System Design Notes

* Rate limiter should run at the **edge** → before your service does any work.
* Scaling limiters = scaling Redis, not Java.
* Avoid read replicas — rate limiting is write-heavy.
* Shard keys by user-ID to scale beyond single Redis instance.

---

## 📝 License

MIT – Free to use, modify, and contribute.

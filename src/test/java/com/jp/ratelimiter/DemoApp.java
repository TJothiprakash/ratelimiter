package com.jp.ratelimiter;

import com.jp.ratelimiter.config.RedisClientFactory;
import redis.clients.jedis.JedisPool;

public class DemoApp {

    public static void main(String[] args) throws Exception {

        JedisPool pool = RedisClientFactory.getPool();

        // Choose one limiter to test
//        RateLimiter limiter = new FixedWindowRateLimiter(
//                pool,
//                5,    // limit: 5 requests
//                10    // window: 10 seconds
//        );
        RateLimiter limiter = new SlidingWindowLogRateLimiter(
                pool,
                5,
                10,
                java.util.concurrent.TimeUnit.SECONDS
        );

      /*  RateLimiter limiter = new LeakyBucketRateLimiter(
                pool,
                3,    // capacity
                2.0    // refill rate: 2 tokens/sec
        );*/

        System.out.println("Testing rate limiter...");

        for (int i = 1; i <= 10; i++) {
            boolean allowed = limiter.allow("user123");
            System.out.println("Request " + i + " -> " + (allowed ? "ALLOWED" : "BLOCKED"));
            Thread.sleep(100); // small gap so logs are readable
        }
    }
}

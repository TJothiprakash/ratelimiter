package com.jp.ratelimiter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

public class FixedWindowRateLimiter implements RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(FixedWindowRateLimiter.class);

    private final JedisPool jedisPool;
    private final int limit;
    private final long windowSeconds;
    private final String prefix;

    public FixedWindowRateLimiter(JedisPool jedisPool, int limit, long windowSeconds) {
        this(jedisPool, limit, windowSeconds, "rl:fixed");
    }

    public FixedWindowRateLimiter(JedisPool jedisPool, int limit, long windowSeconds, String prefix) {
        this.jedisPool = jedisPool;
        this.limit = limit;
        this.windowSeconds = windowSeconds;
        this.prefix = prefix;
    }

    @Override
    public boolean allow(String key) {
        long nowSeconds = System.currentTimeMillis() / 1000;

        // Compute the window start by flooring the current time into a fixed-sized bucket
        long windowStart = (nowSeconds / windowSeconds) * windowSeconds;

        String redisKey = String.format("%s:%s:%d", prefix, key, windowStart);

        try (Jedis jedis = jedisPool.getResource()) {
            Long count = jedis.incr(redisKey);

            if (count == 1L) {
                // First request in this window → we set TTL only once.
                jedis.expire(redisKey, (int) windowSeconds);
                log.info("New window created. Key={} Count={} ExpiresIn={}s", redisKey, count, windowSeconds);
            } else {
                log.info("Window hit. Key={} Count={}", redisKey, count);
            }

            boolean allowed = count <= limit;

            if (!allowed) {
                log.warn("Rate limit exceeded for key={} Count={} Limit={}", key, count, limit);
            }

            return allowed;
        }
    }
}

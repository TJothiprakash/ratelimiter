package com.jp.ratelimiter;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.concurrent.TimeUnit;

public class SlidingWindowLogRateLimiter implements RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(SlidingWindowLogRateLimiter.class);

    private final JedisPool jedisPool;
    private final int limit;
    private final long windowMillis;
    private final String prefix;

    public SlidingWindowLogRateLimiter(JedisPool jedisPool, int limit, long windowDuration, TimeUnit unit) {
        this(jedisPool, limit, unit.toMillis(windowDuration), "rl:sliding");
    }

    public SlidingWindowLogRateLimiter(JedisPool jedisPool, int limit, long windowMillis, String prefix) {
        this.jedisPool = jedisPool;
        this.limit = limit;
        this.windowMillis = windowMillis;
        this.prefix = prefix;
    }

    @Override
    public boolean allow(String key) {
        long now = System.currentTimeMillis();
        long windowStart = now - windowMillis;

        String redisKey = String.format("%s:%s", prefix, key);

        try (Jedis jedis = jedisPool.getResource()) {

            // 1) Remove old entries
            long removed = jedis.zremrangeByScore(redisKey, 0, windowStart);
            if (removed > 0) {
                log.info("Sliding window cleanup: removed {} old entries for key={}", removed, redisKey);
            }

            // 2) Count remaining entries
            long currentCount = jedis.zcard(redisKey);
            log.info("Current window count={} for key={}", currentCount, redisKey);

            if (currentCount >= limit) {
                log.warn("Rate limit exceeded for key={} Count={} Limit={}", key, currentCount, limit);
                return false;
            }

            // 3) Add new timestamp
            jedis.zadd(redisKey, now, String.valueOf(now));
            log.info("Added timestamp {} to sliding window for key={}", now, redisKey);

            // 4) Set TTL so Redis won’t grow unbounded
            jedis.pexpire(redisKey, windowMillis * 2);

            return true;
        }
    }
}

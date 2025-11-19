package com.jp.ratelimiter;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.List;

public class LeakyBucketRateLimiter implements RateLimiter {

    private final JedisPool jedisPool;
    private final int capacity;
    private final double refillTokensPerSecond;
    private final String prefix;
    private final String scriptSha;

    private static final String LUA_SCRIPT =
            "local capacity = tonumber(ARGV[1])\n" +
                    "local refill_rate = tonumber(ARGV[2])\n" +
                    "local now = tonumber(ARGV[3])\n" +
                    "local tokens = tonumber(redis.call('GET', KEYS[1]))\n" +
                    "local last_refill = tonumber(redis.call('GET', KEYS[2]))\n" +
                    "if tokens == nil then tokens = capacity end\n" +
                    "if last_refill == nil then last_refill = now end\n" +
                    "local delta = now - last_refill\n" +
                    "if delta < 0 then delta = 0 end\n" +
                    "local filled_tokens = math.min(capacity, tokens + (delta * refill_rate))\n" +
                    "local allowed = 0\n" +
                    "if filled_tokens >= 1 then\n" +
                    "  filled_tokens = filled_tokens - 1\n" +
                    "  allowed = 1\n" +
                    "end\n" +
                    "redis.call('SET', KEYS[1], filled_tokens)\n" +
                    "redis.call('SET', KEYS[2], now)\n" +
                    "return allowed\n";

    public LeakyBucketRateLimiter(JedisPool jedisPool, int capacity, double refillTokensPerSecond) {
        this(jedisPool, capacity, refillTokensPerSecond, "rl:lb");
    }

    public LeakyBucketRateLimiter(JedisPool jedisPool, int capacity, double refillTokensPerSecond, String prefix) {
        this.jedisPool = jedisPool;
        this.capacity = capacity;
        this.refillTokensPerSecond = refillTokensPerSecond;
        this.prefix = prefix;

        // Load script once and cache SHA
        try (Jedis jedis = jedisPool.getResource()) {
            this.scriptSha = jedis.scriptLoad(LUA_SCRIPT);
        }
    }

    @Override
    public boolean allow(String key) {
        long now = System.currentTimeMillis();
        double refillRatePerMillis = refillTokensPerSecond / 1000.0;

        String tokensKey = String.format("%s:%s:tokens", prefix, key);
        String tsKey = String.format("%s:%s:ts", prefix, key);

        try (Jedis jedis = jedisPool.getResource()) {
            Object result = jedis.evalsha(
                    scriptSha,
                    List.of(tokensKey, tsKey),
                    List.of(
                            String.valueOf(capacity),
                            String.valueOf(refillRatePerMillis),
                            String.valueOf(now)
                    )
            );
            long allowed = ((Number) result).longValue();
            return allowed == 1L;
        }
    }
}

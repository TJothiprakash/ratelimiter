package com.jp.ratelimiter.config;


import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

public class RedisClientFactory {

    private static final JedisPool POOL = new JedisPool(
            new JedisPoolConfig(),
            "localhost", 6379
    );

    public static JedisPool getPool() {
        return POOL;
    }
}

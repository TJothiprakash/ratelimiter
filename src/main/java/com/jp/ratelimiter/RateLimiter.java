package com.jp.ratelimiter;


public interface RateLimiter {
    /**
     * @param key something like userId/ip/token
     * @return true if request is allowed, false if should be blocked (429)
     */
    boolean allow(String key);
}

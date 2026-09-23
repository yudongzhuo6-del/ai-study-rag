package com.yudong.aistudy.service;

import java.time.Duration;

public interface RedisCacheService {

    <T> T get(String key, Class<T> type);

    void set(String key, Object value, Duration ttl);

    long deleteByPrefix(String prefix);
}

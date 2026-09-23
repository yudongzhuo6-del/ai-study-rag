package com.yudong.aistudy.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yudong.aistudy.service.RedisCacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Service
public class RedisCacheServiceImpl implements RedisCacheService {

    private static final Logger log = LoggerFactory.getLogger(RedisCacheServiceImpl.class);

    private static final long SCAN_COUNT = 1000L;

    private final StringRedisTemplate stringRedisTemplate;

    private final ObjectMapper objectMapper;

    public RedisCacheServiceImpl(StringRedisTemplate stringRedisTemplate, ObjectMapper objectMapper) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public <T> T get(String key, Class<T> type) {
        if (key == null || key.trim().isEmpty()) {
            return null;
        }

        try {
            String value = stringRedisTemplate.opsForValue().get(key);
            if (value == null || value.trim().isEmpty()) {
                return null;
            }

            return objectMapper.readValue(value, type);
        } catch (Exception e) {
            log.warn("Read redis cache failed, key={}", key, e);
            return null;
        }
    }

    @Override
    public void set(String key, Object value, Duration ttl) {
        if (key == null || key.trim().isEmpty() || value == null || ttl == null || ttl.isZero() || ttl.isNegative()) {
            return;
        }

        try {
            String json = objectMapper.writeValueAsString(value);
            stringRedisTemplate.opsForValue().set(key, json, ttl);
        } catch (Exception e) {
            log.warn("Write redis cache failed, key={}", key, e);
        }
    }

    @Override
    public long deleteByPrefix(String prefix) {
        if (prefix == null || prefix.trim().isEmpty()) {
            return 0L;
        }

        try {
            Long deletedCount = stringRedisTemplate.execute((RedisConnection connection) -> {
                ScanOptions options = ScanOptions.scanOptions()
                        .match(prefix + "*")
                        .count(SCAN_COUNT)
                        .build();
                List<byte[]> keys = new ArrayList<>();
                try (Cursor<byte[]> cursor = connection.scan(options)) {
                    while (cursor.hasNext()) {
                        keys.add(cursor.next());
                    }
                }

                if (keys.isEmpty()) {
                    return 0L;
                }

                return connection.del(keys.toArray(new byte[0][]));
            });
            return deletedCount == null ? 0L : deletedCount;
        } catch (Exception e) {
            log.warn("Delete redis cache by prefix failed, prefix={}", prefix, e);
            return 0L;
        }
    }
}

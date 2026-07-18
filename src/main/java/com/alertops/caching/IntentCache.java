
package com.alertops.caching;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

@Component
public class IntentCache {

    private static final String KEY_PREFIX = "alertops:intent:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    public IntentCache(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            @Value("${alertops.intent.ttl:10m}") Duration ttl
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.ttl = ttl;
    }

    public void put(UUID intentId, Intent intent) {
        Objects.requireNonNull(intentId, "intentId must not be null");
        Objects.requireNonNull(intent, "intent must not be null");
        redisTemplate.opsForValue().set(key(intentId), serialize(intent), ttl);
    }

    public Intent get(UUID intentId) {
        if (intentId == null) {
            return null;
        }
        return deserialize(redisTemplate.opsForValue().get(key(intentId)));
    }

    /**
     * Atomically reads and deletes an intent so only one application replica can
     * consume a one-time workflow token.
     */
    public Intent consume(UUID intentId) {
        if (intentId == null) {
            return null;
        }
        return deserialize(redisTemplate.opsForValue().getAndDelete(key(intentId)));
    }

    private String key(UUID intentId) {
        return KEY_PREFIX + intentId;
    }

    private String serialize(Intent intent) {
        try {
            return objectMapper.writeValueAsString(intent);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize intent", e);
        }
    }

    private Intent deserialize(String value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.readValue(value, Intent.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not deserialize intent", e);
        }
    }
}


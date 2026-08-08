package com.alertops.caching;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IntentCacheTest {

    private static final Duration TTL = Duration.ofMinutes(10);

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private IntentCache intentCache;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        intentCache = new IntentCache(redisTemplate, new ObjectMapper(), TTL);
    }

    @Test
    void storesJsonWithNamespacedKeyAndTtl() {
        UUID intentId = UUID.randomUUID();

        intentCache.put(intentId, new Intent(IntentType.JOIN_TEAM));

        verify(valueOperations).set(
                "alertops:intent:" + intentId,
                "{\"type\":\"JOIN_TEAM\"}",
                TTL
        );
    }

    @Test
    void readsIntentWithoutDeletingIt() {
        UUID intentId = UUID.randomUUID();
        when(valueOperations.get(anyString())).thenReturn("{\"type\":\"JOIN_TEAM\"}");

        Intent intent = intentCache.get(intentId);

        assertThat(intent).isEqualTo(new Intent(IntentType.JOIN_TEAM));
        verify(valueOperations).get("alertops:intent:" + intentId);
    }

    @Test
    void consumesIntentWithAtomicGetAndDelete() {
        UUID intentId = UUID.randomUUID();
        when(valueOperations.getAndDelete(anyString())).thenReturn("{\"type\":\"JOIN_TEAM\"}");

        Intent intent = intentCache.consume(intentId);

        assertThat(intent).isEqualTo(new Intent(IntentType.JOIN_TEAM));
        verify(valueOperations).getAndDelete("alertops:intent:" + intentId);
    }

    @Test
    void returnsNullForMissingIntent() {
        assertThat(intentCache.consume(UUID.randomUUID())).isNull();
    }
}

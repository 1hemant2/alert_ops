package com.alertops.caching;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "REDIS_INTEGRATION_TEST", matches = "true")
class IntentCacheRedisIntegrationTest {

    private LettuceConnectionFactory firstConnectionFactory;
    private LettuceConnectionFactory secondConnectionFactory;
    private StringRedisTemplate firstTemplate;

    @BeforeEach
    void setUp() {
        firstConnectionFactory = connectionFactory();
        secondConnectionFactory = connectionFactory();
        firstTemplate = template(firstConnectionFactory);
    }

    @AfterEach
    void tearDown() {
        if (firstConnectionFactory != null) {
            firstConnectionFactory.destroy();
        }
        if (secondConnectionFactory != null) {
            secondConnectionFactory.destroy();
        }
    }

    @Test
    void sharesAndAtomicallyConsumesIntentAcrossIndependentClients() {
        Duration ttl = Duration.ofMinutes(10);
        IntentCache firstReplica = new IntentCache(firstTemplate, new ObjectMapper(), ttl);
        IntentCache secondReplica = new IntentCache(
                template(secondConnectionFactory),
                new ObjectMapper(),
                ttl
        );
        UUID intentId = UUID.randomUUID();
        String key = "alertops:intent:" + intentId;

        try {
            firstReplica.put(intentId, new Intent(IntentType.JOIN_TEAM));

            assertThat(secondReplica.get(intentId)).isEqualTo(new Intent(IntentType.JOIN_TEAM));
            assertThat(firstTemplate.getExpire(key))
                    .isPositive()
                    .isLessThanOrEqualTo(ttl.getSeconds());
            assertThat(secondReplica.consume(intentId)).isEqualTo(new Intent(IntentType.JOIN_TEAM));
            assertThat(firstReplica.consume(intentId)).isNull();
        } finally {
            firstTemplate.delete(key);
        }
    }

    private LettuceConnectionFactory connectionFactory() {
        String host = environmentOrDefault("REDIS_INTEGRATION_HOST", "localhost");
        int port = Integer.parseInt(environmentOrDefault("REDIS_INTEGRATION_PORT", "6379"));
        String password = System.getenv("REDIS_INTEGRATION_PASSWORD");
        if (password == null || password.isBlank()) {
            throw new IllegalStateException("REDIS_INTEGRATION_PASSWORD must be set");
        }

        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration(host, port);
        configuration.setPassword(RedisPassword.of(password));
        LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(configuration);
        connectionFactory.afterPropertiesSet();
        return connectionFactory;
    }

    private StringRedisTemplate template(LettuceConnectionFactory connectionFactory) {
        StringRedisTemplate template = new StringRedisTemplate(connectionFactory);
        template.afterPropertiesSet();
        return template;
    }

    private String environmentOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}

package com.alertops.security;

import io.jsonwebtoken.security.WeakKeyException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtUtilTest {

    private static final String TEST_SECRET = Base64.getEncoder().encodeToString(
            "test-only-secret-that-is-at-least-32-bytes".getBytes(StandardCharsets.UTF_8)
    );

    @Test
    void generatedTokenCanBeParsedWithItsClaims() {
        JwtUtil jwtUtil = new JwtUtil(TEST_SECRET);
        UUID userId = UUID.randomUUID();

        String token = jwtUtil.generateToken(
                userId.toString(),
                Map.of("email", "engineer@example.com", "roles", "ADMIN"),
                60
        );

        assertEquals(userId.toString(), jwtUtil.parse(token).getSubject());
        assertEquals("engineer@example.com", jwtUtil.parse(token).get("email", String.class));
        assertEquals("ADMIN", jwtUtil.parse(token).get("roles", String.class));
    }

    @Test
    void rejectsASecretThatIsTooShortForHs256() {
        String weakSecret = Base64.getEncoder().encodeToString(
                "too-short".getBytes(StandardCharsets.UTF_8)
        );

        assertThrows(WeakKeyException.class, () -> new JwtUtil(weakSecret));
    }
}

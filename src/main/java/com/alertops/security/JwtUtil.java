package com.alertops.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;
import java.util.Map;

@Component
public class JwtUtil {

    // 10 minutes
    private static final long ACCESS_TOKEN_TTL =  1000;

    // In real apps: load from application.yml / env
    private static final String SECRET =
            "dGhpcy1pcy1hLXZlcnktc2VjdXJlLTI1Ni1iaXQtc2VjcmV0LWtleQ==";

    private final Key signingKey;

    public JwtUtil() {
        this.signingKey = Keys.hmacShaKeyFor(
                java.util.Base64.getDecoder().decode(SECRET)
        );
    }

    /**
     * @param email        unique user identifier stored as JWT subject
     * @param claims       additional JWT claims (may be empty, never null, or any additional details like role, permission, etc)
     * @param expiryTime   second after ticket expire
     * @return             compact JWT string to be used in Authorization header
     */
    public String generateToken(String userId, Map<String, Object> claims, int expiryTime) {
        return Jwts.builder()
                .setSubject(userId)
                .addClaims(claims)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + ACCESS_TOKEN_TTL * expiryTime))
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser()
                .setSigningKey(SECRET)
                .parseClaimsJws(token)
                .getBody();
    }

}

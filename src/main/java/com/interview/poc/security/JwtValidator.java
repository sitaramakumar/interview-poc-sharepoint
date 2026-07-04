package com.interview.poc.security;

import com.interview.poc.config.AppConfig;
import com.interview.poc.model.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

public class JwtValidator {

    private static final String SECRET = AppConfig.get("jwt.secret", "YOUR-JWT-SECRET");
    private static final SecretKey SIGNING_KEY = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

    public static Claims validateToken(String token) {
        if (token == null || token.isBlank()) {
            throw new SecurityException("Missing Authorization header");
        }
        if (token.trim().startsWith("Bearer ")) {
            token = token.trim().substring(7).trim();
        }
        return Jwts.parser()
                .verifyWith(SIGNING_KEY)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public static UserRole extractRole(Claims claims) {
        String clientId = claims.getSubject();
        if (clientId != null) {
            return UserRole.fromClientId(clientId);
        }
        Object roleClaim = claims.get("role");
        if (roleClaim != null) {
            return UserRole.valueOf(roleClaim.toString().toUpperCase());
        }
        return UserRole.READ;
    }

    public static String generateToken(String clientId, UserRole role, long expirySeconds) {
        return Jwts.builder()
                .subject(clientId)
                .claim("role", role.getName())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirySeconds * 1000))
                .signWith(SIGNING_KEY)
                .compact();
    }
}

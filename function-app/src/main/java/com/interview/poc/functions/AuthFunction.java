package com.interview.poc.functions;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class AuthFunction {

    private static final String DEFAULT_SECRET = "YOUR-JWT-SECRET";

    public static class TokenResponse {
        public String read;
        public String write;
        public String admin;
    }

    public static TokenResponse getTokensStatic() {
        TokenResponse response = new TokenResponse();
        response.read = issueToken("read-client-poc-001", "read", 3600);
        response.write = issueToken("write-client-poc-002", "write", 3600);
        response.admin = issueToken("admin-client-poc-003", "admin", 3600);
        return response;
    }

    private static String issueToken(String clientId, String role, long expirySeconds) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", role);
        return Jwts.builder()
                .setSubject(clientId)
                .addClaims(claims)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expirySeconds * 1000))
                .signWith(Keys.hmacShaKeyFor(secret().getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    private static String secret() {
        String value = System.getenv("JWT_SECRET");
        return value == null || value.isBlank() ? DEFAULT_SECRET : value;
    }
}

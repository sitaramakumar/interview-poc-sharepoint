package com.interview.poc.security;

import com.interview.poc.config.AppConfig;
import com.interview.poc.model.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.WeakKeyException;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

public class JwtValidator {

    /** RFC 7518 §3.2: HMAC-SHA keys must be at least 256 bits (32 bytes). */
    private static final int MIN_SECRET_BYTES = 32;

    private static final String SECRET = AppConfig.get("jwt.secret", "YOUR-JWT-SECRET");
    private static final SecretKey SIGNING_KEY = buildSigningKey(SECRET);

    private static SecretKey buildSigningKey(String secret) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        try {
            return Keys.hmacShaKeyFor(bytes);
        } catch (WeakKeyException e) {
            // jjwt's own message reports bit length but not which config key is at fault;
            // surface that immediately instead of letting every request fail downstream
            // with an opaque ExceptionInInitializerError.
            throw new IllegalStateException(
                    "jwt.secret is " + bytes.length + " bytes (" + (bytes.length * 8) + " bits); "
                            + "HMAC-SHA signing requires at least " + MIN_SECRET_BYTES + " bytes (256 bits). "
                            + "Set a longer jwt.secret in application.properties or via the JWT_SECRET env var.",
                    e);
        }
    }

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
        // The explicit "role" claim is authoritative: generateToken() below always sets
        // one, so checking the clientId heuristic first (as this method previously did)
        // meant the role a token was actually issued with was silently discarded in
        // favor of a guess based on substrings in the client ID. Fall back to that
        // guess only for tokens that don't carry a role claim at all (e.g. tokens
        // issued by an external identity provider), and to an unrecognized value.
        Object roleClaim = claims.get("role");
        if (roleClaim != null) {
            try {
                return UserRole.valueOf(roleClaim.toString().toUpperCase());
            } catch (IllegalArgumentException ignored) {
                // unrecognized role value; fall through to the clientId heuristic below
            }
        }
        String clientId = claims.getSubject();
        if (clientId != null) {
            return UserRole.fromClientId(clientId);
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

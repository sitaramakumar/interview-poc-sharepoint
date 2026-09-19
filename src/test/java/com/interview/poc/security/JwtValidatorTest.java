package com.interview.poc.security;

import com.interview.poc.model.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtValidatorTest {

    @Test
    void generateThenValidateRoundTripsTheSameClientIdAndRole() {
        String token = JwtValidator.generateToken("client-42", UserRole.WRITE, 300);

        Claims claims = JwtValidator.validateToken(token);

        assertEquals("client-42", claims.getSubject());
        assertEquals("write", claims.get("role"));
    }

    @Test
    void validateTokenAcceptsARawBearerHeaderValue() {
        String token = JwtValidator.generateToken("client-1", UserRole.READ, 300);

        // DocumentApi passes the raw "Authorization" header value through, "Bearer "
        // prefix and all — confirm that's stripped correctly rather than only working
        // for a bare token.
        Claims claims = JwtValidator.validateToken("Bearer " + token);

        assertEquals("client-1", claims.getSubject());
    }

    @Test
    void validateTokenRejectsAMissingToken() {
        assertThrows(SecurityException.class, () -> JwtValidator.validateToken(null));
        assertThrows(SecurityException.class, () -> JwtValidator.validateToken("   "));
    }

    @Test
    void validateTokenRejectsATokenSignedWithADifferentKey() {
        // A 32+ byte key so key construction itself succeeds — the point of this test
        // is an independently-signed token being rejected, not a weak-key failure.
        var otherKey = io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                "a-completely-different-32-byte-plus-signing-key".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String tokenFromAnotherIssuer = Jwts.builder()
                .subject("client-1")
                .signWith(otherKey)
                .compact();

        assertThrows(io.jsonwebtoken.security.SignatureException.class,
                () -> JwtValidator.validateToken(tokenFromAnotherIssuer));
    }

    @Test
    void extractRoleHonorsTheExplicitRoleClaimOverTheClientIdHeuristic() {
        // Regression test for the bug this change fixes: a token whose clientId does
        // NOT contain "admin"/"write", but whose role claim explicitly says ADMIN,
        // must resolve to ADMIN — not silently fall back to the READ default the old
        // clientId-first ordering produced.
        String token = JwtValidator.generateToken("service-account-7", UserRole.ADMIN, 300);
        Claims claims = JwtValidator.validateToken(token);

        assertEquals(UserRole.ADMIN, JwtValidator.extractRole(claims));
    }

    @Test
    void extractRoleFallsBackToTheClientIdHeuristicWhenThereIsNoRoleClaim() {
        // Tokens without an explicit role claim (e.g. from an external identity
        // provider) must still resolve via the clientId heuristic, unchanged.
        String token = Jwts.builder()
                .subject("admin-service")
                .signWith(signingKeyForTestsOnly())
                .compact();

        Claims claims = JwtValidator.validateToken(token);

        assertEquals(UserRole.ADMIN, JwtValidator.extractRole(claims));
    }

    @Test
    void extractRoleDefaultsToReadWhenNeitherClaimNorClientIdIsPresent() {
        // A registered claim (issuedAt) is required here to put the builder into
        // Claims mode at all: a builder with no claims-related calls produces a
        // bodiless "content JWS" rather than a Claims JWS, which validateToken's
        // parseSignedClaims() correctly refuses to parse. No subject or role claim
        // is set, which is the actual condition this test exercises.
        String token = Jwts.builder()
                .issuedAt(new java.util.Date())
                .signWith(signingKeyForTestsOnly())
                .compact();

        Claims claims = JwtValidator.validateToken(token);

        assertEquals(UserRole.READ, JwtValidator.extractRole(claims));
    }

    /**
     * Mirrors the real jwt.secret configured for tests via application.properties,
     * so a token built here verifies against JwtValidator's own signing key.
     */
    private static javax.crypto.SecretKey signingKeyForTestsOnly() {
        return io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                "CHANGE-ME-LOCAL-DEV-JWT-SECRET-32BYTES-MIN".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}

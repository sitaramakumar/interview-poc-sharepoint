package com.interview.poc.auth;

import com.interview.poc.config.AppConfig;
import com.interview.poc.model.UserRole;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

/**
 * Simulates Azure AD / ForgeRock identity provider operations.
 *
 * Production migration:
 *   - Azure AD:  Use Azure.Identity DefaultAzureCredentialBuilder
 *                 with ClientCertificateCredential for cert-based auth.
 *   - ForgeRock: Call ForgeRock AM / OpenAM REST endpoints to
 *                 validate JWT or exchange client cert for access token.
 */
public class IdentityProvider {

    private static final String SECRET = AppConfig.get("jwt.secret", "poc-secret-key-change-me");

    public static String issueToken(String clientId, UserRole role, long expirySeconds) {
        var claims = new java.util.HashMap<String, Object>();
        claims.put("role", role.getName());
        return io.jsonwebtoken.Jwts.builder()
                .setSubject(clientId)
                .addClaims(claims)
                .setIssuedAt(new java.util.Date())
                .setExpiration(new java.util.Date(System.currentTimeMillis() + expirySeconds * 1000))
                .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(SECRET.getBytes()))
                .compact();
    }

    public static String getClientId() {
        return AppConfig.get("vault.client.id");
    }

    public static String getTenantId() {
        return AppConfig.get("vault.tenant.id");
    }
}

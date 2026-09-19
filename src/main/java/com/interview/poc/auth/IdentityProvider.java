package com.interview.poc.auth;

import com.interview.poc.config.AppConfig;
import com.interview.poc.model.UserRole;
import com.interview.poc.security.JwtValidator;

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

    /**
     * Used to mint the same access token JwtValidator/AuthApi issue elsewhere —
     * this class previously built its own JJWT token with its own fallback
     * secret and the deprecated 0.11-style builder API, an independent path
     * that only agreed with JwtValidator's tokens because both happened to
     * resolve the same jwt.secret config value. Adding refresh tokens forced
     * the question of which path owns tokenType, so this got consolidated
     * onto the one path that already has the weak-key check.
     */
    public static String issueToken(String clientId, UserRole role, long expirySeconds) {
        return JwtValidator.generateAccessToken(clientId, role, expirySeconds);
    }

    public static String getClientId() {
        return AppConfig.get("vault.client.id");
    }

    public static String getTenantId() {
        return AppConfig.get("vault.tenant.id");
    }
}

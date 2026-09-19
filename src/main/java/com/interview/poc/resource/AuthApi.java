package com.interview.poc.resource;

import com.interview.poc.auth.PasswordHasher;
import com.interview.poc.auth.UserAccount;
import com.interview.poc.auth.UserStore;
import com.interview.poc.model.UserRole;
import com.interview.poc.security.AuthException;
import com.interview.poc.security.JwtValidator;
import io.jsonwebtoken.Claims;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Real username/password login and refresh-token exchange, on top of the
 * pre-baked demo tokens DocumentApi.getTokens() has always issued. Neither
 * existed before this: the only way to get a token used to be the no-auth
 * /api/tokens diagnostic endpoint.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthApi {

    private static final long ACCESS_TOKEN_TTL_SECONDS = 15 * 60;      // 15 minutes
    private static final long REFRESH_TOKEN_TTL_SECONDS = 7 * 24 * 3600; // 7 days

    private final UserStore userStore = new UserStore();

    public record LoginRequest(String clientId, String password) {}
    public record TokenPair(String accessToken, String refreshToken, String role) {}
    public record RefreshRequest(String refreshToken) {}
    public record AccessTokenResponse(String accessToken) {}

    @PostMapping("/login")
    public ResponseEntity<TokenPair> login(@RequestBody LoginRequest request) {
        UserAccount account = userStore.find(request.clientId())
                .filter(a -> PasswordHasher.verify(request.password(), a.getPasswordSalt(), a.getPasswordHash()))
                .orElseThrow(() -> new AuthException("Invalid clientId or password"));

        UserRole role = UserRole.valueOf(account.getRole());
        String accessToken = JwtValidator.generateAccessToken(account.getClientId(), role, ACCESS_TOKEN_TTL_SECONDS);
        String refreshToken = JwtValidator.generateRefreshToken(account.getClientId(), role, REFRESH_TOKEN_TTL_SECONDS);

        return ResponseEntity.ok(new TokenPair(accessToken, refreshToken, role.getName()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AccessTokenResponse> refresh(@RequestBody RefreshRequest request) {
        Claims claims;
        try {
            claims = JwtValidator.validateToken(request.refreshToken());
        } catch (Exception e) {
            throw new AuthException("Invalid refresh token: " + e.getMessage());
        }
        if (!JwtValidator.isRefreshToken(claims)) {
            throw new AuthException("Not a refresh token");
        }

        UserRole role = JwtValidator.extractRole(claims);
        String accessToken = JwtValidator.generateAccessToken(claims.getSubject(), role, ACCESS_TOKEN_TTL_SECONDS);
        return ResponseEntity.ok(new AccessTokenResponse(accessToken));
    }
}

package com.interview.poc.resource;

import com.interview.poc.security.AuthException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuthApiTest {

    private final AuthApi authApi = new AuthApi();

    @Test
    void loginWithCorrectCredentialsReturnsAnAccessAndRefreshTokenForTheAccountsRole() {
        var response = authApi.login(new AuthApi.LoginRequest("admin@poc.local", "admin-demo-pass"));

        AuthApi.TokenPair body = response.getBody();
        assertNotNull(body);
        assertEquals("admin", body.role());
        assertNotNull(body.accessToken());
        assertNotNull(body.refreshToken());
    }

    @Test
    void loginWithTheWrongPasswordIsRejected() {
        assertThrows(AuthException.class,
                () -> authApi.login(new AuthApi.LoginRequest("admin@poc.local", "wrong-password")));
    }

    @Test
    void loginWithAnUnknownClientIdIsRejected() {
        assertThrows(AuthException.class,
                () -> authApi.login(new AuthApi.LoginRequest("nobody@poc.local", "anything")));
    }

    @Test
    void refreshWithAValidRefreshTokenReturnsAFreshAccessToken() {
        var loginResponse = authApi.login(new AuthApi.LoginRequest("write@poc.local", "write-demo-pass"));
        String refreshToken = loginResponse.getBody().refreshToken();

        var refreshResponse = authApi.refresh(new AuthApi.RefreshRequest(refreshToken));

        assertNotNull(refreshResponse.getBody());
        assertNotNull(refreshResponse.getBody().accessToken());
    }

    @Test
    void refreshRejectsAnAccessTokenUsedInPlaceOfARefreshToken() {
        var loginResponse = authApi.login(new AuthApi.LoginRequest("read@poc.local", "read-demo-pass"));
        String accessToken = loginResponse.getBody().accessToken();

        assertThrows(AuthException.class, () -> authApi.refresh(new AuthApi.RefreshRequest(accessToken)));
    }

    @Test
    void refreshRejectsAGarbageToken() {
        assertThrows(AuthException.class, () -> authApi.refresh(new AuthApi.RefreshRequest("not-a-real-jwt")));
    }
}

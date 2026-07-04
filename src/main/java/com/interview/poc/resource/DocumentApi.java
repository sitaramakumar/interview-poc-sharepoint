package com.interview.poc.resource;

import com.interview.poc.auth.IdentityProvider;
import com.interview.poc.model.Document;
import com.interview.poc.model.UserRole;
import com.interview.poc.security.JwtValidator;
import com.interview.poc.sharepoint.SharePointClient;
import com.interview.poc.vault.VaultService;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class DocumentApi {

    private static final Logger log = LoggerFactory.getLogger(DocumentApi.class);
    private final SharePointClient spClient = new SharePointClient();
    private final VaultService vault = new VaultService();

    // ─── Diagnostic (no auth required) ───────────────────────────────────────

    @GetMapping("/tokens")
    public ResponseEntity<Map<String, String>> getTokens() {
        Map<String, String> tokens = Map.of(
                "read",  IdentityProvider.issueToken("read-client-poc-001",  UserRole.READ,  3600),
                "write", IdentityProvider.issueToken("write-client-poc-002", UserRole.WRITE, 3600),
                "admin", IdentityProvider.issueToken("admin-client-poc-003", UserRole.ADMIN, 3600)
        );
        return ResponseEntity.ok(tokens);
    }

    @GetMapping("/vault/credentials")
    public ResponseEntity<Map<String, Object>> getVaultInfo() {
        var spCreds = vault.readCredentials();
        Map<String, Object> masked = Map.of(
                "siteUrl",        spCreds.getSiteUrl(),
                "clientId",       spCreds.getClientId(),
                "tenantId",       spCreds.getTenantId(),
                "clientSecret",   spCreds.getClientSecret() == null ? "***masked***" : "***masked***",
                "libraryPath",    spCreds.getLibraryPath(),
                "certificatePath", spCreds.getCertificatePath(),
                "keyPath",        spCreds.getKeyPath(),
                "username",       spCreds.getUsername(),
                "password",       "***masked***"
        );
        return ResponseEntity.ok(masked);
    }

    // ─── Document CRUD (auth required) ───────────────────────────────────────

    @GetMapping("/document")
    public ResponseEntity<?> list(@RequestHeader("Authorization") String authHeader) {
        UserRole role = authorize(authHeader, "GET");
        List<Document> docs = spClient.listDocuments();
        return ResponseEntity.ok(Map.of(
                "documents", docs,
                "count",     docs.size(),
                "role",      role.getName()
        ));
    }

    @GetMapping("/document/{id}")
    public ResponseEntity<?> get(@RequestHeader("Authorization") String authHeader,
                                 @PathVariable String id) {
        UserRole role = authorize(authHeader, "GET");
        Document doc = spClient.getDocument(id);
        if (doc == null) {
            return ResponseEntity.status(404).body(Map.of(
                    "error", "Not found", "id", id));
        }
        return ResponseEntity.ok(doc);
    }

    @PutMapping("/document/upload")
    public ResponseEntity<?> upload(@RequestHeader("Authorization") String authHeader,
                                    @RequestBody Document doc) {
        UserRole role = authorize(authHeader, "PUT");
        if (doc.getModifiedBy() == null) {
            doc.setModifiedBy(role.getName() + "-user");
        }
        Document saved = spClient.createOrUpdateDocument(doc);
        var spCreds = vault.readCredentials();
        return ResponseEntity.ok(Map.of(
                "message",   "Document uploaded successfully",
                "document",  saved,
                "siteUrl",   spCreds.getSiteUrl(),
                "role",      role.getName()
        ));
    }

    @DeleteMapping("/document/{id}")
    public ResponseEntity<?> delete(@RequestHeader("Authorization") String authHeader,
                                    @PathVariable String id) {
        UserRole role = authorize(authHeader, "DELETE");
        boolean deleted = spClient.deleteDocument(id);
        if (!deleted) {
            return ResponseEntity.status(404).body(Map.of(
                    "error", "Not found", "id", id));
        }
        var spCreds = vault.readCredentials();
        return ResponseEntity.ok(Map.of(
                "message",  "Document deleted",
                "id",       id,
                "siteUrl",  spCreds.getSiteUrl(),
                "role",     role.getName()
        ));
    }

    // ─── Auth helper ──────────────────────────────────────────────────────────

    private UserRole authorize(String authHeader, String method) {
        if (authHeader == null || authHeader.isBlank()) {
            throw new AuthException("Missing Authorization header");
        }
        Claims claims;
        try {
            claims = JwtValidator.validateToken(authHeader);
        } catch (Exception e) {
            throw new AuthException("Invalid token: " + e.getMessage());
        }
        UserRole role = JwtValidator.extractRole(claims);
        if (!role.allows(method)) {
            throw new ForbiddenException("Role '" + role.getName() + "' does not allow " + method);
        }
        log.info("Auth OK: clientId={}, role={}, method={}",
                claims.getSubject(), role.getName(), method);
        return role;
    }

    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public static class AuthException extends RuntimeException {
        public AuthException(String msg) { super(msg); }
    }

    @ResponseStatus(HttpStatus.FORBIDDEN)
    public static class ForbiddenException extends RuntimeException {
        public ForbiddenException(String msg) { super(msg); }
    }
}

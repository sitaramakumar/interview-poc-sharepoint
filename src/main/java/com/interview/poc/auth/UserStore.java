package com.interview.poc.auth;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.poc.config.AppConfig;
import com.interview.poc.model.UserRole;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Local file-based user store backing /api/auth/login.
 *
 * LOCAL POC MODE:
 *   One JSON file, clientId -> UserAccount (PBKDF2 hash + per-account salt,
 *   never a plaintext password), seeded with three demo accounts — one per
 *   role — on first run. Demo passwords are logged once at seed time so the
 *   PoC is usable without reading source; they are not secrets.
 *
 * PRODUCTION MIGRATION:
 *   A real identity provider (Azure AD / ForgeRock — see IdentityProvider's
 *   own header comment) rather than a password file; this store exists only
 *   so /api/auth/login has something real to check credentials against
 *   without standing up a database for a PoC.
 */
public class UserStore {
    private static final Path STORE_FILE = Paths.get(
            AppConfig.get("users.local.path", "data/users/users.json"));
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, UserAccount>> MAP_TYPE = new TypeReference<>() {};

    public UserStore() {
        try {
            Files.createDirectories(STORE_FILE.getParent());
            if (Files.notExists(STORE_FILE)) {
                seed();
            }
        } catch (IOException e) {
            throw new RuntimeException("Cannot initialize local user store at " + STORE_FILE, e);
        }
    }

    private void seed() throws IOException {
        Map<String, UserAccount> accounts = new LinkedHashMap<>();
        accounts.put("read@poc.local", demoAccount("read@poc.local", "read-demo-pass", UserRole.READ));
        accounts.put("write@poc.local", demoAccount("write@poc.local", "write-demo-pass", UserRole.WRITE));
        accounts.put("admin@poc.local", demoAccount("admin@poc.local", "admin-demo-pass", UserRole.ADMIN));
        Files.writeString(STORE_FILE, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(accounts));
    }

    private static UserAccount demoAccount(String clientId, String plaintextDemoPassword, UserRole role) {
        String salt = PasswordHasher.randomSalt();
        return new UserAccount(clientId, salt, PasswordHasher.hash(plaintextDemoPassword, salt), role.name());
    }

    public Optional<UserAccount> find(String clientId) {
        try {
            String content = Files.readString(STORE_FILE);
            Map<String, UserAccount> all = MAPPER.readValue(content, MAP_TYPE);
            return Optional.ofNullable(all.get(clientId));
        } catch (IOException e) {
            throw new RuntimeException("Failed to read user store from " + STORE_FILE, e);
        }
    }
}

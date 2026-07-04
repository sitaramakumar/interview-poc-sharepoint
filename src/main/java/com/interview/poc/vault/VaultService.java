package com.interview.poc.vault;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.poc.config.AppConfig;
import com.interview.poc.model.SharePointCredentials;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Vault abstraction for Azure Key Vault.
 *
 * LOCAL POC MODE:
 *   Reads/writes credentials from a JSON file on disk at data/vault/credentials.json.
 *   Simulates how Azure Key Vault would expose sharepoint-credentials as a secret.
 *
 * PRODUCTION MIGRATION (Azure Key Vault):
 *
 *   import com.azure.identity.DefaultAzureCredentialBuilder;
 *   import com.azure.security.keyvault.secrets.SecretClient;
 *   import com.azure.security.keyvault.secrets.SecretClientBuilder;
 *   import com.azure.security.keyvault.secrets.models.KeyVaultSecret;
 *
 *   SecretClient client = new SecretClientBuilder()
 *       .vaultUrl("https://<vault-name>.vault.azure.net/")
 *       .credential(new DefaultAzureCredentialBuilder()
 *           .clientId(AppConfig.get("vault.client.id"))
 *           .clientCertificate(certPath, keyPath)
 *           .tenantId(AppConfig.get("vault.tenant.id"))
 *           .build())
 *       .buildClient();
 *
 *   // READ:
 *   KeyVaultSecret secret = client.getSecret("sharepoint-credentials");
 *   SharePointCredentials creds = MAPPER.readValue(secret.getValue(), SharePointCredentials.class);
 *
 *   // WRITE:
 *   client.setSecret(new KeyVaultSecret("sharepoint-credentials",
 *           MAPPER.writeValueAsString(creds)));
 *
 *   // CERTIFICATE:
 *   KeyVaultCertificate cert = client.getCertificate("client-cert");
 *   // Use cert to authenticate against SharePoint Online / Microsoft Graph
 */
public class VaultService {
    private static final Path VAULT_FILE = Paths.get(
            AppConfig.get("vault.local.path", "data/vault/credentials.json"));
    private static final ObjectMapper MAPPER = new ObjectMapper();

    static {
        try {
            Files.createDirectories(VAULT_FILE.getParent());
            if (Files.notExists(VAULT_FILE)) {
                String json = MAPPER.writeValueAsString(new SharePointCredentials(
                        "https://YOUR-TENANT.sharepoint.com",
                        "",
                        "YOUR-TENANT.onmicrosoft.com",
                        "",
                        "/Shared Documents",
                        AppConfig.get("vault.cert.path", "data/certificates/client-cert.pem"),
                        AppConfig.get("vault.key.path", "data/certificates/client-key.pem"),
                        "",
                        ""
                ));
                Files.writeString(VAULT_FILE, json);
            }
        } catch (IOException e) {
            throw new RuntimeException("Cannot initialize local vault at " + VAULT_FILE, e);
        }
    }

    public SharePointCredentials readCredentials() {
        try {
            String content = Files.readString(VAULT_FILE);
            SharePointCredentials creds = MAPPER.readValue(content, SharePointCredentials.class);
            return creds;
        } catch (IOException e) {
            throw new RuntimeException("Failed to read vault from " + VAULT_FILE, e);
        }
    }

    public void writeCredentials(SharePointCredentials creds) {
        try {
            String json = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(creds);
            Files.writeString(VAULT_FILE, json);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write vault to " + VAULT_FILE, e);
        }
    }
}

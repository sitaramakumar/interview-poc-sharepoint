package com.interview.poc.sharepoint;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.poc.config.AppConfig;
import com.interview.poc.model.Document;
import com.interview.poc.model.SharePointCredentials;
import com.interview.poc.vault.VaultService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class SharePointClient {

    private static final Logger log = LoggerFactory.getLogger(SharePointClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String GRAPH_BASE_URL = "https://graph.microsoft.com/v1.0";

    private final HttpClient http;
    private final String siteUrl;
    private final String tenantId;
    private final String clientId;
    private final String clientSecret;
    private final String libraryPath;
    private final String siteId;

    private String accessToken;
    private Instant tokenExpiresAt = Instant.EPOCH;

    public SharePointClient() {
        SharePointCredentials creds = new VaultService().readCredentials();
        this.siteUrl = valueFromEnvOrConfig("SHAREPOINT_SITE_URL", "sharepoint.site.url", creds.getSiteUrl(), "https://YOUR-TENANT.sharepoint.com");
        this.tenantId = valueFromEnvOrConfig("SHAREPOINT_TENANT_ID", "sharepoint.tenant.id", creds.getTenantId(), "YOUR-TENANT.onmicrosoft.com");
        this.clientId = valueFromEnvOrConfig("SHAREPOINT_CLIENT_ID", "sharepoint.client.id", creds.getClientId(), null);
        this.clientSecret = valueFromEnvOrConfig("SHAREPOINT_CLIENT_SECRET", "sharepoint.client.secret", creds.getClientSecret(), null);
        this.libraryPath = normalizeLibraryPath(valueFromEnvOrConfig("SHAREPOINT_LIBRARY_PATH", "sharepoint.library.path", creds.getLibraryPath(), "/Shared Documents"));
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();

        requireNotBlank(clientId, "SHAREPOINT_CLIENT_ID or sharepoint.client.id is required");
        requireNotBlank(clientSecret, "SHAREPOINT_CLIENT_SECRET or sharepoint.client.secret is required");

        this.siteId = resolveSiteId().asText();
        log.info("Connected to SharePoint site {} using library {}", siteUrl, libraryPath);
    }

    public List<Document> listDocuments() {
        JsonNode root = getJson(graphUrl("/sites/" + siteId + "/drive/root/children"));
        List<Document> documents = new ArrayList<>();

        for (JsonNode item : root.path("value")) {
            if (!item.path("file").isObject()) {
                continue;
            }
            String id = item.path("id").asText();
            String content = "";
            try {
                content = downloadText(id);
            } catch (Exception e) {
                log.warn("Could not read content for SharePoint file {} : {}", item.path("name").asText(), e.getMessage());
            }
            documents.add(toDocument(item, content));
        }

        return documents.stream()
                .sorted(Comparator.comparing(Document::getTitle, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());
    }

    public Document getDocument(String id) {
        requireNotBlank(id, "Document id is required");
        JsonNode item = getJson(graphUrl("/sites/" + siteId + "/drive/items/" + encodePathSegment(id)));
        return toDocument(item, downloadText(id));
    }

    public Document createOrUpdateDocument(Document doc) {
        if (doc == null) {
            throw new IllegalArgumentException("Document body is required");
        }
        String id = isBlank(doc.getId()) ? UUID.randomUUID().toString() : doc.getId();
        String fileName = buildFileName(id, doc.getTitle());
        byte[] contentBytes = (doc.getContent() == null ? "" : doc.getContent()).getBytes(StandardCharsets.UTF_8);
        String uploadUrl = graphUrl("/sites/" + siteId + "/drive/root:/" + encodePathSegment(fileName) + ":/content");

        JsonNode uploaded = putJson(uploadUrl, contentBytes, "text/plain");
        Document saved = toDocument(uploaded, doc.getContent() == null ? "" : doc.getContent());
        saved.setId(uploaded.path("id").asText(id));
        saved.setTitle(doc.getTitle());
        log.info("Uploaded SharePoint document {} as {}", saved.getId(), fileName);
        return saved;
    }

    public boolean deleteDocument(String id) {
        requireNotBlank(id, "Document id is required");
        String url = graphUrl("/sites/" + siteId + "/drive/items/" + encodePathSegment(id));
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(60))
                .header("Authorization", "Bearer " + getAccessToken())
                .DELETE()
                .build();

        try {
            HttpResponse<Void> response = http.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() == 200 || response.statusCode() == 204) {
                log.info("Deleted SharePoint document {}", id);
                return true;
            }
            if (response.statusCode() == 404) {
                log.warn("SharePoint document not found for delete {}", id);
                return false;
            }
            throw sharePointException(response.statusCode(), response.headers().map().toString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while deleting SharePoint document " + id, e);
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete SharePoint document " + id, e);
        }
    }

    private JsonNode resolveSiteId() {
        String graphSitePath = toGraphSitePath(siteUrl);
        JsonNode site = getJson(GRAPH_BASE_URL + "/sites/" + graphSitePath);
        return site.get("id");
    }

    private JsonNode getJson(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(60))
                    .header("Authorization", "Bearer " + getAccessToken())
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw sharePointException(response.statusCode(), response.body());
            }
            return MAPPER.readTree(response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while calling SharePoint", e);
        } catch (IOException e) {
            throw new RuntimeException("Failed to call SharePoint", e);
        }
    }

    private JsonNode putJson(String url, byte[] body, String contentType) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(120))
                    .header("Authorization", "Bearer " + getAccessToken())
                    .header("Accept", "application/json")
                    .header("Content-Type", contentType)
                    .PUT(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw sharePointException(response.statusCode(), response.body());
            }
            return MAPPER.readTree(response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while uploading to SharePoint", e);
        } catch (IOException e) {
            throw new RuntimeException("Failed to upload to SharePoint", e);
        }
    }

    private String downloadText(String id) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(graphUrl("/sites/" + siteId + "/drive/items/" + encodePathSegment(id) + "/content")))
                    .timeout(Duration.ofSeconds(60))
                    .header("Authorization", "Bearer " + getAccessToken())
                    .GET()
                    .build();
            HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw sharePointException(response.statusCode(), new String(response.body(), StandardCharsets.UTF_8));
            }
            return new String(response.body(), StandardCharsets.UTF_8);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while downloading SharePoint document", e);
        } catch (IOException e) {
            throw new RuntimeException("Failed to download SharePoint document", e);
        }
    }

    private synchronized String getAccessToken() {
        if (accessToken != null && Instant.now().isBefore(tokenExpiresAt.minusSeconds(60))) {
            return accessToken;
        }

        Map<String, String> body = new LinkedHashMap<>();
        body.put("grant_type", "client_credentials");
        body.put("client_id", clientId);
        body.put("client_secret", clientSecret);
        body.put("scope", "https://graph.microsoft.com/.default");

        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create("https://login.microsoftonline.com/" + tenantId + "/oauth2/v2.0/token"))
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(toFormBody(body)))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw sharePointException(response.statusCode(), response.body());
            }
            JsonNode token = MAPPER.readTree(response.body());
            accessToken = token.get("access_token").asText();
            tokenExpiresAt = Instant.now().plusSeconds(token.path("expires_in").asLong(3600));
            return accessToken;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while acquiring SharePoint token", e);
        } catch (IOException e) {
            throw new RuntimeException("Failed to acquire SharePoint token", e);
        }
    }

    private Document toDocument(JsonNode item, String content) {
        Document doc = new Document();
        doc.setId(item.path("id").asText());
        doc.setTitle(item.path("name").asText("Untitled"));
        doc.setContent(content);
        doc.setModifiedBy(readModifiedBy(item));
        doc.setModifiedAt(parseDate(item.path("lastModifiedDateTime").asText(null)));
        return doc;
    }

    private String readModifiedBy(JsonNode item) {
        JsonNode user = item.path("lastModifiedBy").path("user");
        if (user.has("displayName")) {
            return user.get("displayName").asText();
        }
        user = item.path("createdBy").path("user");
        if (user.has("displayName")) {
            return user.get("displayName").asText();
        }
        return "sharepoint";
    }

    private Date parseDate(String value) {
        if (isBlank(value)) {
            return new Date();
        }
        try {
            return Date.from(OffsetDateTime.parse(value).toInstant());
        } catch (DateTimeParseException e) {
            return new Date();
        }
    }

    private String buildFileName(String id, String title) {
        String safeTitle = sanitizeFileName(isBlank(title) ? "document" : title);
        return id + "__" + safeTitle + ".txt";
    }

    private String sanitizeFileName(String value) {
        String sanitized = value.replaceAll("[^a-zA-Z0-9._-]", "_");
        return sanitized.length() > 80 ? sanitized.substring(0, 80) : sanitized;
    }

    private String toGraphSitePath(String url) {
        URI uri = URI.create(url);
        String path = uri.getPath();
        if (isBlank(path) || "/".equals(path)) {
            return uri.getHost() + ":/";
        }
        return uri.getHost() + ":" + path;
    }

    private String graphUrl(String path) {
        return GRAPH_BASE_URL + path;
    }

    private String normalizeLibraryPath(String value) {
        String normalized = value == null ? "/Shared Documents" : value.trim();
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        while (normalized.endsWith("/") && normalized.length() > 1) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private String valueFromEnvOrConfig(String envName, String configKey, String credentialValue, String defaultValue) {
        String envValue = System.getenv(envName);
        if (!isBlank(envValue)) {
            return envValue;
        }
        if (!isBlank(credentialValue)) {
            return credentialValue;
        }
        String configValue = AppConfig.get(configKey);
        if (!isBlank(configValue)) {
            return configValue;
        }
        return defaultValue;
    }

    private String toFormBody(Map<String, String> values) {
        return values.entrySet().stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .collect(Collectors.joining("&"));
    }

    private String encodePathSegment(String value) {
        return encode(value);
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private void requireNotBlank(String value, String message) {
        if (isBlank(value)) {
            throw new IllegalArgumentException(message);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private RuntimeException sharePointException(int statusCode, String body) {
        return new RuntimeException("SharePoint request failed with HTTP " + statusCode + ": " + body);
    }
}

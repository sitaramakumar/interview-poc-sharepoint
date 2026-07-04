package com.interview.poc.functions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.*;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
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
import java.util.*;
import java.util.stream.Collectors;

public class DocumentFunction {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String GRAPH_BASE_URL = "https://graph.microsoft.com/v1.0";
    private static final String DEFAULT_JWT_SECRET = "YOUR-JWT-SECRET";
    private static final SecretKey JWT_SIGNING_KEY = Keys.hmacShaKeyFor(env("JWT_SECRET", DEFAULT_JWT_SECRET).getBytes(StandardCharsets.UTF_8));

    @FunctionName("getTokens")
    public HttpResponseMessage getTokens(
            @HttpTrigger(name = "req", methods = {HttpMethod.GET}, authLevel = AuthorizationLevel.ANONYMOUS, route = "tokens") HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {
        return jsonResponse(request, 200, AuthFunction.getTokensStatic());
    }

    @FunctionName("getVaultCredentials")
    public HttpResponseMessage getVaultCredentials(
            @HttpTrigger(name = "req", methods = {HttpMethod.GET}, authLevel = AuthorizationLevel.ANONYMOUS, route = "vault/credentials") HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {
        SharePointConfig config = SharePointConfig.fromEnvironment();
        Map<String, Object> masked = new LinkedHashMap<>();
        masked.put("siteUrl", config.siteUrl);
        masked.put("clientId", config.clientId);
        masked.put("tenantId", config.tenantId);
        masked.put("clientSecret", config.clientSecret == null || config.clientSecret.isBlank() ? "***masked***" : "***masked***");
        masked.put("libraryPath", config.libraryPath);
        return jsonResponse(request, 200, masked);
    }

    @FunctionName("listDocuments")
    public HttpResponseMessage listDocuments(
            @HttpTrigger(name = "req", methods = {HttpMethod.GET}, authLevel = AuthorizationLevel.FUNCTION, route = "document") HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {
        try {
            String role = authorize(request);
            requireRole(role, "GET");
            List<Document> documents = new SharePointClient().listDocuments();
            return jsonResponse(request, 200, new DocumentList(documents, role));
        } catch (AuthFailureException e) {
            return jsonResponse(request, 401, new ErrorResponse("Unauthorized", e.getMessage()));
        } catch (ForbiddenException e) {
            return jsonResponse(request, 403, new ErrorResponse("Forbidden", e.getMessage()));
        } catch (Exception e) {
            return jsonResponse(request, 500, new ErrorResponse("Internal server error", e.getMessage()));
        }
    }

    @FunctionName("getDocument")
    public HttpResponseMessage getDocument(
            @HttpTrigger(name = "req", methods = {HttpMethod.GET}, authLevel = AuthorizationLevel.FUNCTION, route = "document/{id}") HttpRequestMessage<Optional<String>> request,
            @BindingName("id") String id,
            final ExecutionContext context) {
        try {
            String role = authorize(request);
            requireRole(role, "GET");
            Document document = new SharePointClient().getDocument(id);
            return jsonResponse(request, 200, document);
        } catch (AuthFailureException e) {
            return jsonResponse(request, 401, new ErrorResponse("Unauthorized", e.getMessage()));
        } catch (ForbiddenException e) {
            return jsonResponse(request, 403, new ErrorResponse("Forbidden", e.getMessage()));
        } catch (Exception e) {
            return jsonResponse(request, 500, new ErrorResponse("Internal server error", e.getMessage()));
        }
    }

    @FunctionName("uploadDocument")
    public HttpResponseMessage uploadDocument(
            @HttpTrigger(name = "req", methods = {HttpMethod.PUT}, authLevel = AuthorizationLevel.FUNCTION, route = "document/upload") HttpRequestMessage<Document> request,
            final ExecutionContext context) {
        try {
            String role = authorize(request);
            requireRole(role, "PUT");
            Document input = request.getBody();
            if (input == null) {
                return jsonResponse(request, 400, new ErrorResponse("Bad request", "Document body is required"));
            }
            Document saved = new SharePointClient().createOrUpdateDocument(input);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("message", "Document uploaded successfully");
            body.put("document", saved);
            body.put("siteUrl", SharePointConfig.fromEnvironment().siteUrl);
            body.put("role", role);
            return jsonResponse(request, 200, body);
        } catch (AuthFailureException e) {
            return jsonResponse(request, 401, new ErrorResponse("Unauthorized", e.getMessage()));
        } catch (ForbiddenException e) {
            return jsonResponse(request, 403, new ErrorResponse("Forbidden", e.getMessage()));
        } catch (Exception e) {
            return jsonResponse(request, 500, new ErrorResponse("Internal server error", e.getMessage()));
        }
    }

    @FunctionName("deleteDocument")
    public HttpResponseMessage deleteDocument(
            @HttpTrigger(name = "req", methods = {HttpMethod.DELETE}, authLevel = AuthorizationLevel.FUNCTION, route = "document/{id}") HttpRequestMessage<Optional<String>> request,
            @BindingName("id") String id,
            final ExecutionContext context) {
        try {
            String role = authorize(request);
            requireRole(role, "DELETE");
            boolean deleted = new SharePointClient().deleteDocument(id);
            if (!deleted) {
                return jsonResponse(request, 404, new ErrorResponse("Not found", "Document not found"));
            }
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("message", "Document deleted");
            body.put("id", id);
            body.put("siteUrl", SharePointConfig.fromEnvironment().siteUrl);
            body.put("role", role);
            return jsonResponse(request, 200, body);
        } catch (AuthFailureException e) {
            return jsonResponse(request, 401, new ErrorResponse("Unauthorized", e.getMessage()));
        } catch (ForbiddenException e) {
            return jsonResponse(request, 403, new ErrorResponse("Forbidden", e.getMessage()));
        } catch (Exception e) {
            return jsonResponse(request, 500, new ErrorResponse("Internal server error", e.getMessage()));
        }
    }

    private static String authorize(HttpRequestMessage<?> request) {
        String authHeader = request.getHeaders().get("Authorization");
        if (authHeader == null || authHeader.isBlank()) {
            throw new AuthFailureException("Missing Authorization header");
        }
        String token = authHeader.trim().startsWith("Bearer ") ? authHeader.trim().substring(7).trim() : authHeader.trim();
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(JWT_SIGNING_KEY)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            String role = claims.get("role", String.class);
            if (role == null || role.isBlank()) {
                throw new AuthFailureException("Invalid token: role claim missing");
            }
            return role.toLowerCase(Locale.ROOT);
        } catch (Exception e) {
            throw new AuthFailureException("Invalid token: " + e.getMessage());
        }
    }

    private static void requireRole(String role, String method) {
        if (role == null) {
            throw new ForbiddenException("Role is required");
        }
        boolean allowed = switch (role) {
            case "read" -> method.equals("GET");
            case "write" -> method.equals("GET") || method.equals("PUT");
            case "admin" -> method.equals("GET") || method.equals("PUT") || method.equals("DELETE");
            default -> false;
        };
        if (!allowed) {
            throw new ForbiddenException("Role '" + role + "' does not allow " + method);
        }
    }

    private static HttpResponseMessage jsonResponse(HttpRequestMessage<?> request, int status, Object body) {
        return request.createResponseBuilder(HttpStatus.valueOf(status))
                .header("Content-Type", "application/json")
                .body(body)
                .build();
    }

    private static String env(String key, String defaultValue) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    public static class Document {
        public String id;
        public String title;
        public String content;
        public String modifiedBy;
        public Date modifiedAt;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public String getModifiedBy() { return modifiedBy; }
        public void setModifiedBy(String modifiedBy) { this.modifiedBy = modifiedBy; }
        public Date getModifiedAt() { return modifiedAt; }
        public void setModifiedAt(Date modifiedAt) { this.modifiedAt = modifiedAt; }
    }

    public static class DocumentList {
        public List<Document> documents;
        public int count;
        public String role;

        public DocumentList() {}
        public DocumentList(List<Document> documents, String role) {
            this.documents = documents;
            this.count = documents == null ? 0 : documents.size();
            this.role = role;
        }
    }

    public static class ErrorResponse {
        public String error;
        public String message;

        public ErrorResponse() {}
        public ErrorResponse(String error, String message) {
            this.error = error;
            this.message = message;
        }
    }

    public static class AuthFailureException extends RuntimeException {
        public AuthFailureException(String message) { super(message); }
    }

    public static class ForbiddenException extends RuntimeException {
        public ForbiddenException(String message) { super(message); }
    }

    public static class SharePointConfig {
        public String siteUrl;
        public String tenantId;
        public String clientId;
        public String clientSecret;
        public String libraryPath;

        public static SharePointConfig fromEnvironment() {
            SharePointConfig config = new SharePointConfig();
            config.siteUrl = env("SHAREPOINT_SITE_URL", "https://YOUR-TENANT.sharepoint.com");
            config.tenantId = env("SHAREPOINT_TENANT_ID", "YOUR-TENANT.onmicrosoft.com");
            config.clientId = env("SHAREPOINT_CLIENT_ID", null);
            config.clientSecret = env("SHAREPOINT_CLIENT_SECRET", null);
            config.libraryPath = normalizeLibraryPath(env("SHAREPOINT_LIBRARY_PATH", "/Shared Documents"));
            return config;
        }

        public void validate() {
            if (clientId == null || clientId.isBlank()) {
                throw new IllegalArgumentException("SHAREPOINT_CLIENT_ID is required");
            }
            if (clientSecret == null || clientSecret.isBlank()) {
                throw new IllegalArgumentException("SHAREPOINT_CLIENT_SECRET is required");
            }
        }
    }

    public static class SharePointClient {
        private final HttpClient http;
        private final SharePointConfig config;
        private final String siteId;
        private String accessToken;
        private Instant tokenExpiresAt = Instant.EPOCH;

        public SharePointClient() {
            this.config = SharePointConfig.fromEnvironment();
            config.validate();
            this.http = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(20))
                    .followRedirects(HttpClient.Redirect.ALWAYS)
                    .build();
            this.siteId = resolveSiteId();
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
                    content = "";
                }
                documents.add(toDocument(item, content));
            }
            return documents.stream()
                    .sorted(Comparator.comparing(document -> document.title == null ? "" : document.title, String.CASE_INSENSITIVE_ORDER))
                    .collect(Collectors.toList());
        }

        public Document getDocument(String id) {
            requireNotBlank(id, "Document id is required");
            JsonNode item = getJson(graphUrl("/sites/" + siteId + "/drive/items/" + encode(id)));
            return toDocument(item, downloadText(id));
        }

        public Document createOrUpdateDocument(Document doc) {
            if (doc == null) {
                throw new IllegalArgumentException("Document body is required");
            }
            String id = isBlank(doc.id) ? UUID.randomUUID().toString() : doc.id;
            String fileName = buildFileName(id, doc.title);
            byte[] contentBytes = (doc.content == null ? "" : doc.content).getBytes(StandardCharsets.UTF_8);
            String uploadUrl = graphUrl("/sites/" + siteId + "/drive/root:/" + encode(fileName) + ":/content");
            JsonNode uploaded = putJson(uploadUrl, contentBytes, "text/plain");
            Document saved = toDocument(uploaded, doc.content == null ? "" : doc.content);
            saved.id = uploaded.path("id").asText(id);
            saved.title = doc.title;
            saved.modifiedBy = doc.modifiedBy == null ? "sharepoint" : doc.modifiedBy;
            saved.modifiedAt = new Date();
            return saved;
        }

        public boolean deleteDocument(String id) {
            requireNotBlank(id, "Document id is required");
            String url = graphUrl("/sites/" + siteId + "/drive/items/" + encode(id));
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(60))
                    .header("Authorization", "Bearer " + getAccessToken())
                    .DELETE()
                    .build();
            try {
                HttpResponse<Void> response = http.send(request, HttpResponse.BodyHandlers.discarding());
                if (response.statusCode() == 200 || response.statusCode() == 204) {
                    return true;
                }
                if (response.statusCode() == 404) {
                    return false;
                }
                throw new RuntimeException("SharePoint delete failed with HTTP " + response.statusCode());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while deleting SharePoint document", e);
            } catch (IOException e) {
                throw new RuntimeException("Failed to delete SharePoint document", e);
            }
        }

        private String resolveSiteId() {
            String graphSitePath = toGraphSitePath(config.siteUrl);
            JsonNode site = getJson(GRAPH_BASE_URL + "/sites/" + graphSitePath);
            JsonNode id = site.get("id");
            if (id == null || id.asText().isBlank()) {
                throw new IllegalArgumentException("Could not resolve SharePoint site id for " + config.siteUrl);
            }
            return id.asText();
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
                    throw new RuntimeException("SharePoint request failed with HTTP " + response.statusCode() + ": " + response.body());
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
                    throw new RuntimeException("SharePoint upload failed with HTTP " + response.statusCode() + ": " + response.body());
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
                HttpRequest request = HttpRequest.newBuilder(URI.create(graphUrl("/sites/" + siteId + "/drive/items/" + encode(id) + "/content")))
                        .timeout(Duration.ofSeconds(60))
                        .header("Authorization", "Bearer " + getAccessToken())
                        .GET()
                        .build();
                HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw new RuntimeException("SharePoint download failed with HTTP " + response.statusCode());
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
            body.put("client_id", config.clientId);
            body.put("client_secret", config.clientSecret);
            body.put("scope", "https://graph.microsoft.com/.default");
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create("https://login.microsoftonline.com/" + config.tenantId + "/oauth2/v2.0/token"))
                        .timeout(Duration.ofSeconds(60))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(toFormBody(body)))
                        .build();
                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw new RuntimeException("Token request failed with HTTP " + response.statusCode() + ": " + response.body());
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
            doc.id = item.path("id").asText();
            doc.title = item.path("name").asText("Untitled");
            doc.content = content;
            doc.modifiedBy = readModifiedBy(item);
            doc.modifiedAt = parseDate(item.path("lastModifiedDateTime").asText(null));
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

        public static String normalizeLibraryPath(String value) {
            String normalized = value == null ? "/Shared Documents" : value.trim();
            if (!normalized.startsWith("/")) {
                normalized = "/" + normalized;
            }
            while (normalized.endsWith("/") && normalized.length() > 1) {
                normalized = normalized.substring(0, normalized.length() - 1);
            }
            return normalized;
        }

        private String toFormBody(Map<String, String> values) {
            return values.entrySet().stream()
                    .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                    .collect(Collectors.joining("&"));
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
    }
}

# Interview POC — Endpoint Reference

> **Server:** `http://localhost:8080` (Spring Boot + embedded Tomcat)
> **Time:** 2026-06-14T20:59:31+05:30
> **File:** `DocumentApi.java:19`

---

## Quick Reference Table

| Endpoint | Method | Auth | Role | Purpose |
|----------|:------:|:----:|:----:|---------|
| `/api/health` | GET | None | — | Health check |
| `/api/tokens` | GET | None | — | Generate READ / WRITE / ADMIN JWTs |
| `/api/vault/credentials` | GET | None | — | View vault config (password masked) |
| `/api/document` | GET | Bearer | READ | List all documents |
| `/api/document/{id}` | GET | Bearer | READ | Get single document by ID |
| `/api/document/upload` | PUT | Bearer | WRITE | Create or update a document |
| `/api/document/{id}` | DELETE | Bearer | ADMIN | Delete a document by ID |

---

## Authentication Model

All protected endpoints expect:

```
Authorization: {{authRead}}    ← full "Bearer eyJ..." value from environment
```

No separate `Bearer ` prefix — the variable already contains it. See `POSTMAN_GUIDE.md` for setup.

**How JWT works in this POC:**
- Tokens are **HS256** signed with `jwt.secret` from `application.properties`
- The `Authorization` header is read in `DocumentApi.authorize()` at line 116
- `JwtValidator.validateToken()` parses + verifies signature + expiry
- `JwtValidator.extractRole()` maps `clientId` pattern → `UserRole` enum
- `role.allows(method)` enforces RBAC

---

## 1. GET /api/health

| Property | Value |
|----------|-------|
| **Auth** | None |
| **Purpose** | Quick liveness check — confirms server is running |
| **Defined in** | `DocumentApi.java:29` handler in Spring |

**Request:**
```
GET http://localhost:8080/api/health
```

**Response 200:**
```json
{
  "status": "ok"
}
```

---

## 2. GET /api/tokens

| Property | Value |
|----------|-------|
| **Auth** | None |
| **Purpose** | Generate fresh JWTs for all three roles (1 hour expiry) |
| **Defined in** | `DocumentApi.java:29` |

**Request:**
```
GET http://localhost:8080/api/tokens
Headers: (none)
```

**Response 200:**
```json
{
  "read": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJyZWFkLWNsaWVudC1wb2MtMDAxIiwicm9sZSI6InJlYWQiLCJpYXQiOjE3ODE0MDU5NDMsImV4cCI6MTc4MTQwOTU0M30...",
  "write": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ3cml0ZS1jbGllbnQtcG9jLTAwMiIsInJvbGUiOiJ3cml0ZSIsImlhdCI6MTc4MTQwNTk0MywiZXhwIjoxNzgxNDA5NTQzfQ...",
  "admin": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbi1jbGllbnQtcG9jLTAwMyIsInJvbGUiOiJhZG1pbiIsImlhdCI6MTc4MTQwNTk0MywiZXhwIjoxNzgxNDA5NTQzfQ..."
}
```

**How tokens map to roles:**

| Token key | `sub` (clientId) | `role` claim | Resolved role | Allowed methods |
|-----------|-----------------|-------------|:-------------:|:----------------:|
| `read` | `read-client-poc-001` | `read` | **READ** | GET |
| `write` | `write-client-poc-002` | `write` | **WRITE** | GET, PUT |
| `admin` | `admin-client-poc-003` | `admin` | **ADMIN** | GET, PUT, DELETE |

**Code flow:**
```
getTokens()
  → IdentityProvider.issueToken("read-client-poc-001", UserRole.READ, 3600)
  → JwtValidator.generateToken(clientId, role, 3600)
  → Jwts.builder().subject(clientId).claim("role", role).signWith(SIGNING_KEY).compact()
```

**Tests tab script (auto-saves to environment):**
```javascript
const response = pm.response.json();
pm.environment.set("authRead", "Bearer " + response.read);
pm.environment.set("authWrite", "Bearer " + response.write);
pm.environment.set("authAdmin", "Bearer " + response.admin);
```

---

## 3. GET /api/vault/credentials

| Property | Value |
|----------|-------|
| **Auth** | None |
| **Purpose** | Diagnostic — show what the app reads from the vault (secrets masked) |
| **Defined in** | `DocumentApi.java:39` |

**Request:**
```
GET http://localhost:8080/api/vault/credentials
Headers: (none)
```

**Response 200:**
```json
{
  "siteUrl": "https://localhost:4567/sharepoint/sites/demo",
  "clientId": "00000000-0000-0000-0000-000000000000",
  "tenantId": "00000000-0000-0000-0000-000000000000",
  "certificatePath": "data/certificates/client-cert.pem",
  "keyPath": "data/certificates/client-key.pem",
  "username": "poc-user@tenant.onmicrosoft.com",
  "password": "***masked***"
}
```

**Code flow:**
```
getVaultInfo()
  → VaultService.readCredentials()
     → Files.readString("data/vault/credentials.json")
     → ObjectMapper.readValue() → SharePointCredentials object
  → masked Map built: password replaced with "***masked***"
  → ResponseEntity.ok(masked)
```

**What each field means:**

| Field | Meaning |
|-------|---------|
| `siteUrl` | SharePoint site root URL |
| `clientId` | Azure AD app registration ID |
| `tenantId` | Azure AD tenant GUID |
| `certificatePath` | X.509 cert PEM file path |
| `keyPath` | X.509 private key PEM file path |
| `username` | SharePoint service account |
| `password` | Always masked in API responses |

---

## 4. GET /api/document

| Property | Value |
|----------|-------|
| **Auth** | Required — `Authorization: {{authRead}}` |
| **Required role** | READ (or higher) |
| **Purpose** | List all documents in the SharePoint mock library |
| **Defined in** | `DocumentApi.java:56` |

**Request:**
```
GET http://localhost:8080/api/document
Headers:
  Authorization: {{authRead}}
```

**Response 200:**
```json
{
  "documents": [
    {
      "id": "ab469639-0af4-4242-97c6-9747956970ad",
      "title": "Project Plan",
      "content": "Q3 deliverables",
      "modifiedBy": "write-user",
      "modifiedAt": 1781358041599
    }
  ],
  "count": 1,
  "role": "read"
}
```

**Response 401 (no/expired token):**
```json
{
  "error": "Unauthorized",
  "message": "Token expired",
  "hint": "Pass token in Authorization: Bearer <JWT>"
}
```

**Response 403 (wrong role — e.g. using admin token, that actually works too since ADMIN allows GET):**
```json
{
  "error": "Forbidden",
  "message": "Role 'read' does not allow PUT on this resource",
  "hint": "Pass token in Authorization: Bearer <JWT>"
}
```

**Code flow:**
```
list(authHeader)
  → authorize(authHeader, "GET")
      1. Strip "Bearer " from header
      2. JwtValidator.validateToken() → verify HMAC-SHA256 + expiry
      3. JwtValidator.extractRole() → UserRole.fromClientId("read-client-poc-001") → READ
      4. READ.allows("GET") → true
  → SharePointClient.listDocuments()
      1. Files.list("data/documents/")
      2. Read each *.json → Document objects
      3. Sort by id
  → ResponseEntity.ok(Map.of("documents", docs, "count", size, "role", "read"))
```

**Tests tab script:**
```javascript
const data = pm.response.json();
pm.test("Status 200", function () {
    pm.expect(pm.response.code).to.eql(200);
});
pm.environment.set("docCount", data.count);
if (data.documents && data.documents.length > 0) {
    pm.environment.set("docId", data.documents[0].id);
    console.log("Saved docId:", data.documents[0].id);
}
```

---

## 5. GET /api/document/{id}

| Property | Value |
|----------|-------|
| **Auth** | Required — `Authorization: {{authRead}}` |
| **Required role** | READ (or higher) |
| **Purpose** | Fetch a single document by its UUID |
| **Defined in** | `DocumentApi.java:67` |

**Request:**
```
GET http://localhost:8080/api/document/ab469639-0af4-4242-97c6-9747956970ad
Headers:
  Authorization: {{authRead}}
```

> `{id}` must be a real document UUID. If `docId` is empty, Postman sends `/api/document/` which returns 400.

**Response 200:**
```json
{
  "id": "ab469639-0af4-4242-97c6-9747956970ad",
  "title": "Project Plan",
  "content": "Q3 deliverables",
  "modifiedBy": "write-user",
  "modifiedAt": 1781358041599
}
```

**Response 404:**
```json
{
  "error": "Not found",
  "id": "nonexistent-id"
}
```

**Code flow:**
```
get(authHeader, id)
  → authorize(authHeader, "GET")      ← same as list
  → SharePointClient.getDocument(id)
      1. Path = "data/documents/" + id + ".json"
      2. Files.exists(path)?
         yes → read + parse JSON → Document
         no  → return null
  → if doc == null → 404
  → else → 200 with Document object
```

---

## 6. PUT /api/document/upload

| Property | Value |
|----------|-------|
| **Auth** | Required — `Authorization: {{authWrite}}` |
| **Required role** | WRITE (or higher) |
| **Purpose** | Create a new document or update an existing one |
| **Defined in** | `DocumentApi.java:79` |

**Request:**
```
PUT http://localhost:8080/api/document/upload
Headers:
  Authorization: {{authWrite}}
  Content-Type: application/json

Body (raw JSON):
{
  "title": "Project Plan",
  "content": "Q3 deliverables - sharepoint integration"
}
```

**Request body fields:**

| Field | Required | Description |
|-------|:--------:|-------------|
| `title` | Yes | Document title |
| `content` | Yes | Document body / payload |
| `id` | No | If provided, updates that document. If omitted, a new UUID is generated |
| `modifiedBy` | No | Auto-set to `write-user` by the server if absent |

**Response 200 (created):**
```json
{
  "message": "Document uploaded successfully",
  "document": {
    "id": "a1b2c3d4e5f6g7h8i9j0",
    "title": "Project Plan",
    "content": "Q3 deliverables - sharepoint integration",
    "modifiedBy": "write-user",
    "modifiedAt": 1781405000000
  },
  "siteUrl": "https://localhost:4567/sharepoint/sites/demo",
  "role": "write"
}
```

**Response 401 (missing/invalid token):**
```json
{"error": "Unauthorized", "message": "Missing Authorization header", "hint": "Pass token in Authorization: Bearer <JWT>"}
```

**Response 403 (READ token trying to write):**
```json
{"error": "Forbidden", "message": "Role 'read' does not allow PUT on this resource", "hint": "..."}
```

**Code flow:**
```
upload(authHeader, doc)
  → authorize(authHeader, "PUT")
      → JWT valid? role = WRITE
      → WRITE.allows("PUT") → true
  → if doc.modifiedBy == null → set to "write-user"
  → SharePointClient.createOrUpdateDocument(doc)
      1. If doc.id == null → UUID.randomUUID().toString()
      2. doc.modifiedAt = new Date()
      3. Write JSON to data/documents/{id}.json
  → VaultService.readCredentials() → reads siteUrl for response
  → ResponseEntity.ok(Map.of("message", ..., "document", saved, "siteUrl", ..., "role", "write"))
```

**File written to disk:**
```
data/documents/a1b2c3d4e5f6g7h8i9j0.json
```

---

## 7. DELETE /api/document/{id}

| Property | Value |
|----------|-------|
| **Auth** | Required — `Authorization: {{authAdmin}}` |
| **Required role** | ADMIN only |
| **Purpose** | Permanently remove a document from the library |
| **Defined in** | `DocumentApi.java:96` |

**Request:**
```
DELETE http://localhost:8080/api/document/ab469639-0af4-4242-97c6-9747956970ad
Headers:
  Authorization: {{authAdmin}}
```

**Response 200:**
```json
{
  "message": "Document deleted",
  "id": "ab469639-0af4-4242-97c6-9747956970ad",
  "siteUrl": "https://localhost:4567/sharepoint/sites/demo",
  "role": "admin"
}
```

**Response 404 (document not found):**
```json
{
  "error": "Not found",
  "id": "nonexistent-id"
}
```

**Response 403 (non-admin trying to delete):**
```json
{"error": "Forbidden", "message": "Role 'write' does not allow DELETE on this resource"}
```

**Code flow:**
```
delete(authHeader, id)
  → authorize(authHeader, "DELETE")
      → JWT valid? role = ADMIN
      → ADMIN.allows("DELETE") → true
  → SharePointClient.deleteDocument(id)
      1. Path = "data/documents/" + id + ".json"
      2. Files.deleteIfExists(path) → boolean
  → if !deleted → 404
  → else → 200 with confirmation
```

**File removed from disk:**
```
data/documents/{id}.json  → deleted
```

---

## Role Enforcement Summary

```
Role    | GET /document | GET /document/{id} | PUT /upload | DELETE /{id}
--------|:-------------:|:------------------:|:-----------:|:-----------:
READ    |     ✅        |        ✅          |     ❌      |     ❌
WRITE   |     ✅        |        ✅          |     ✅      |     ❌
ADMIN   |     ✅        |        ✅          |     ✅      |     ✅
```

Enforced in `DocumentApi.authorize()` at line 116:
```java
private UserRole authorize(String authHeader, String method) {
    Claims claims = JwtValidator.validateToken(authHeader);  // ← 401 if bad
    UserRole role = JwtValidator.extractRole(claims);         // ← maps clientId → role
    if (!role.allows(method))                                // ← 403 if insufficient
        throw new AuthException("Role '" + role.getName() + "' does not allow " + method);
    return role;
}
```

---

## Error Responses

| Status | Condition | Body |
|--------|-----------|------|
| 400 | Missing `Authorization` header on protected endpoint | `{"error":"Unauthorized","message":"Missing Authorization header","hint":"Pass token..."}` |
| 401 | Invalid/expired JWT | `{"error":"Unauthorized","message":"Token expired","hint":"..."}` |
| 403 | Role doesn't allow the method | `{"error":"Forbidden","message":"Role 'read' does not allow PUT...","hint":"..."}` |
| 404 | Document doesn't exist (GET/DELETE) | `{"error":"Not found","id":"..."}` |

---

## Postman Setup Per Endpoint

| Request | Method | URL | Headers | Body | Tests |
|---------|--------|-----|---------|------|-------|
| Get All JWT Tokens | GET | `{{baseUrl}}/api/tokens` | (none) | — | Save `authRead`, `authWrite`, `authAdmin` |
| View Vault Config | GET | `{{baseUrl}}/api/vault/credentials` | (none) | — | — |
| List All Documents | GET | `{{baseUrl}}/api/document` | `Authorization: {{authRead}}` | — | Save first doc `id` → `docId` |
| Get Document by ID | GET | `{{baseUrl}}/api/document/{{docId}}` | `Authorization: {{authRead}}` | — | — |
| Upload / Create | PUT | `{{baseUrl}}/api/document/upload` | `Authorization: {{authWrite}}` `Content-Type: application/json` | raw JSON title+content | Save new `id` → `docId` |
| Delete Document | DELETE | `{{baseUrl}}/api/document/{{docId}}` | `Authorization: {{authAdmin}}` | — | — |

**Critical pre-requisite:** Always run **"Get All JWT Tokens"** first. Without it, all `authRead` / `authWrite` / `authAdmin` variables are empty and every protected request returns 401.

# Interview POC — SharePoint Document Management
## Spring Boot + Microsoft Graph API + JWT RBAC

A proof-of-concept Java application that demonstrates a **custom application layer on top of SharePoint Online**. The app exposes a REST API for document management, enforces role-based access control via JWT, and retrieves credentials from a local vault (simulating Azure Key Vault). All SharePoint operations are performed through the **Microsoft Graph API**.

---

## Architecture

```
┌────────────────┐     JWT (HS256)      ┌──────────────────────┐
│  Client        │ ─── Authorization ──▶│  DocumentApi.java    │
│ (curl/Postman) │                      │  REST Controller     │
└────────────────┘                      └────────┬─────────────┘
                                                 │
                          ┌──────────────────────┼──────────────────────┐
                          │                      │                      │
                          ▼                      ▼                      ▼
               ┌──────────────────┐  ┌───────────────────┐  ┌──────────────────┐
               │  JwtValidator    │  │  VaultService     │  │ SharePointClient │
               │  validateToken() │  │  reads creds from │  │ calls Graph API  │
               │  extractRole()   │  │  credentials.json │  │ with OAuth token │
               └──────────────────┘  └───────────────────┘  └──────────────────┘
                                                                       │
                                                                       ▼
                                                          Microsoft Graph API
                                                          graph.microsoft.com/v1.0
                                                                       │
                                                                       ▼
                                                           SharePoint Online
                                                           Document Library
```

### Two Authorization Layers

| Layer | Mechanism | Enforced By |
|---|---|---|
| **Layer 1 — Client → App** | JWT (HS256) in `Authorization: Bearer` header | `JwtValidator.java` |
| **Layer 2 — App → SharePoint** | OAuth 2.0 Client Credentials → Graph API Bearer token | `SharePointClient.java` |

---

## Project Structure

```
interview-poc/
├── pom.xml                                     ← Spring Boot 3.x, jjwt 0.12.x
├── Dockerfile                                  ← Multi-stage build (Maven + JRE 17)
├── docker-compose.yml                          ← One-command run
│
├── data/
│   ├── vault/credentials.json                  ← Simulates Azure Key Vault
│   └── documents/                              ← Local document store (JSON files)
│
├── src/main/java/com/interview/poc/
│   ├── Server.java                             ← @SpringBootApplication entry point
│   ├── config/AppConfig.java                   ← Properties loader
│   ├── model/
│   │   ├── Document.java                       ← Document entity
│   │   ├── UserRole.java                       ← READ / WRITE / ADMIN enum + allows()
│   │   └── SharePointCredentials.java          ← Vault credential model
│   ├── auth/IdentityProvider.java              ← Simulates Entra ID / ForgeRock token issuance
│   ├── security/JwtValidator.java              ← JWT validate + role extraction
│   ├── vault/VaultService.java                 ← Simulates Azure Key Vault (reads JSON)
│   ├── sharepoint/SharePointClient.java        ← Microsoft Graph API calls (OAuth 2.0)
│   └── resource/DocumentApi.java               ← All REST endpoints
│
├── function-app/                               ← Azure Function App variant
│   └── src/main/java/.../functions/
│       ├── AuthFunction.java                   ← Token issuance function
│       └── DocumentFunction.java               ← Document CRUD functions
│
├── rbac/                                       ← PowerShell scripts for SharePoint RBAC
│   ├── rbacShell.ps1                           ← Create groups + assign permissions
│   ├── graphUpload.ps1                         ← Upload documents via Graph API
│   └── uploadSP.ps1                            ← PnP-based upload helper
│
└── postman/
    ├── interview-poc-collection.json           ← Postman collection (import this)
    └── interview-poc-environment.json          ← Postman environment (import this)
```

---

## Prerequisites

| Tool | Version | Check |
|---|---|---|
| Java JDK | 17 or 21 | `java -version` |
| Maven | 3.8+ | `mvn -version` |
| Docker | 24+ | `docker --version` |
| Docker Compose | 2.20+ | `docker compose version` |

---

## Configuration

All settings are in `src/main/resources/application.properties`.

Credentials are stored separately in `data/vault/credentials.json` (simulating Azure Key Vault).

### 1. Set your SharePoint credentials

Edit `data/vault/credentials.json`:

```json
{
  "siteUrl":          "https://YOUR-TENANT.sharepoint.com",
  "clientId":         "YOUR-ENTRA-APP-CLIENT-ID",
  "tenantId":         "YOUR-TENANT.onmicrosoft.com",
  "clientSecret":     "YOUR-CLIENT-SECRET",
  "libraryPath":      "/Shared Documents",
  "certificatePath":  "",
  "keyPath":          "",
  "username":         "",
  "password":         ""
}
```

### 2. Set your SharePoint details in application.properties

```properties
sharepoint.site.url=https://YOUR-TENANT.sharepoint.com
sharepoint.tenant.id=YOUR-TENANT.onmicrosoft.com
sharepoint.client.id=YOUR-ENTRA-APP-CLIENT-ID
sharepoint.client.secret=YOUR-CLIENT-SECRET
```

### 3. (Optional) Override via environment variables

```bash
SHAREPOINT_CLIENT_ID=xxx
SHAREPOINT_CLIENT_SECRET=xxx
SHAREPOINT_TENANT_ID=xxx
SHAREPOINT_SITE_URL=https://xxx.sharepoint.com
JWT_SECRET=your-strong-secret
```

> The `keystore.p12` binary was not included. To run with HTTPS locally, generate one:
> ```
> keytool -genkeypair -alias poc -keyalg RSA -keysize 2048 -storetype PKCS12 \
>   -keystore src/main/resources/keystore.p12 -validity 365 -storepass changeit
> ```

---

## Running the App

### Option A — Docker Compose (recommended)

```bash
docker compose up --build
```

Server starts on `http://localhost:8080`.

### Option B — Maven (local)

```bash
mvn compile exec:java -Dexec.mainClass=com.interview.poc.Server
```

### Option C — Docker (manual)

```bash
docker build -t interview-poc:latest .
docker run -d -p 8080:8080 \
  -v "${PWD}/data:/app/data" \
  -e SHAREPOINT_CLIENT_ID=xxx \
  -e SHAREPOINT_CLIENT_SECRET=xxx \
  --name poc-server interview-poc:latest

docker logs -f poc-server
```

---

## REST API

Base URL: `http://localhost:8080`

### Public Endpoints (no auth required)

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/health` | Liveness check |
| `GET` | `/api/tokens` | Issue READ / WRITE / ADMIN JWT tokens (1 hour expiry) |
| `GET` | `/api/vault/credentials` | View vault config (password masked) |

### Protected Endpoints (require `Authorization: Bearer <token>`)

| Method | Path | Required Role | Description |
|---|---|:---:|---|
| `GET` | `/api/document` | READ | List all documents |
| `GET` | `/api/document/{id}` | READ | Get document by ID |
| `PUT` | `/api/document/upload` | WRITE | Create or update a document |
| `DELETE` | `/api/document/{id}` | ADMIN | Delete a document |

### Role Matrix

| Role | GET list | GET one | PUT upload | DELETE |
|---|:---:|:---:|:---:|:---:|
| READ | ✅ | ✅ | ❌ | ❌ |
| WRITE | ✅ | ✅ | ✅ | ❌ |
| ADMIN | ✅ | ✅ | ✅ | ✅ |

---

## Quick Test (PowerShell)

```powershell
# 1. Get tokens
$tokens = Invoke-RestMethod http://localhost:8080/api/tokens

# 2. Upload a document (WRITE role)
$body = '{"title":"Test Document","content":"Hello from POC"}'
$result = Invoke-RestMethod -Uri http://localhost:8080/api/document/upload `
  -Method PUT `
  -Headers @{ Authorization = "Bearer $($tokens.write)" } `
  -ContentType "application/json" -Body $body
Write-Host "Created: $($result.document.id)"

# 3. List documents (READ role)
Invoke-RestMethod http://localhost:8080/api/document `
  -Headers @{ Authorization = "Bearer $($tokens.read)" }

# 4. Delete (ADMIN role)
Invoke-RestMethod -Method DELETE `
  http://localhost:8080/api/document/$($result.document.id) `
  -Headers @{ Authorization = "Bearer $($tokens.admin)" }
```

---

## Postman

Import both files from the `postman/` folder:

1. `interview-poc-collection.json` — all requests pre-configured
2. `interview-poc-environment.json` — environment variables

**Important:** Run **"Get All JWT Tokens"** first. The Tests script on that request auto-saves tokens to environment variables. All other requests use `{{authRead}}`, `{{authWrite}}`, `{{authAdmin}}`.

---

## How SharePoint Connection Works

`SharePointClient.java` connects to SharePoint via **Microsoft Graph API** using the **OAuth 2.0 Client Credentials flow**:

```
1. POST https://login.microsoftonline.com/{tenantId}/oauth2/v2.0/token
   body: client_id + client_secret + scope=https://graph.microsoft.com/.default
   → returns JWT access token

2. GET  https://graph.microsoft.com/v1.0/sites/{siteId}/drive/root/children
   Authorization: Bearer <access_token>
   → returns document list

3. PUT  https://graph.microsoft.com/v1.0/sites/{siteId}/drive/root:/{filename}:/content
   → uploads document

4. DELETE https://graph.microsoft.com/v1.0/sites/{siteId}/drive/items/{id}
   → deletes document
```

The credentials (client ID, secret, tenant ID, site URL) are loaded from `data/vault/credentials.json` via `VaultService` on startup — simulating a read from Azure Key Vault.

---

## Azure Function App

The `function-app/` module contains an alternative deployment as Azure Functions (`AuthFunction.java`, `DocumentFunction.java`). It exposes the same operations as HTTP-triggered serverless functions using the same RBAC model.

Configure via `function-app/local.settings.json` (for local development) or Azure App Settings (for production).

---

## PowerShell Scripts (`rbac/`)

| Script | Purpose |
|---|---|
| `rbacShell.ps1` | Creates SharePoint permission groups and assigns users from `rbac.csv` |
| `graphUpload.ps1` | Uploads documents directly to SharePoint via Graph API (no app server needed) |
| `uploadSP.ps1` | PnP PowerShell-based upload helper, falls back to mock server |
| `sharepointOps.ps1` | End-to-end operations script (mock and real modes) |

All scripts accept `-ClientId` and `-ClientSecret` as parameters or read from environment variables — no hardcoded credentials.

---

## Production Upgrade Path

| POC Component | Production Replacement |
|---|---|
| `data/vault/credentials.json` | Azure Key Vault + Managed Identity |
| `IdentityProvider.java` (mock tokens) | Microsoft Entra ID / ForgeRock |
| `VaultService.java` (file read) | `SecretClient` from `azure-security-keyvault-secrets` SDK |
| Local document storage (`data/documents/`) | SharePoint Online via Graph API (already wired in `SharePointClient.java`) |
| HTTP only | HTTPS via Azure Front Door or NGINX reverse proxy |
| `jwt.secret` in properties | Azure Key Vault secret, rotated on schedule |

---

## Security Notes

- JWT secret is in `application.properties` — replace with a Key Vault reference for any shared environment.
- `data/vault/credentials.json` contains plaintext credentials — do not commit this file.
- All `SharePointClient` methods use short-lived OAuth tokens that are cached and refreshed automatically.
- The `Sites.Selected` Graph API scope is recommended over `Sites.ReadWrite.All` to limit blast radius.

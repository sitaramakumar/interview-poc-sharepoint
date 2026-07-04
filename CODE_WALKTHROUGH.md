# Interview POC — Code Walkthrough

> **Workspace state (2026-06-14 17:08 IST)**
> - Working directory: `C:\Users\Ravi Shankar\AppData\Local\Programs\Microsoft VS Code\InterviewProject\interview-poc`
> - Open files: `Server.java`, `TokenGenerator.java`, `AppConfig.java`, `DocumentApi.java`, `JwtValidator.java`, `IdentityProvider.java`, `UserRole.java`, `Document.java`, `SharePointCredentials.java`, `VaultService.java`, `SharePointClient.java`, `Dockerfile`, `application.properties`
> - Running container: `poc-server` on `http://localhost:8080`
> - Time: 2026-06-14T17:08:05+05:30

---

## Request Flow at a Glance

```
HTTP Request (with JWT)
        │
        ▼
DocumentApi.handle()          ← Spring Boot @RestController
    │
    ├─ 1. Extract Authorization: Bearer <token>
    ├─ 2. JwtValidator.validateToken()   ← jjwt 0.12.5, HS256
    ├─ 3. JwtValidator.extractRole()     ← reads "sub" or "role" claim
    ├─ 4. UserRole.allows(method)        ← READ / WRITE / ADMIN check
    ├─ 5. VaultService.readCredentials() ← reads data/vault/credentials.json
    ├─ 6. SharePointClient                ← reads/writes data/documents/*.json
    └─ 7. JSON response with role metadata
```

---

## File-by-File Breakdown

### 1. `pom.xml` — Maven Build Descriptor

**What it does:** Defines the Spring Boot 3.3.4 parent POM, Java 17 target, and all dependencies.

**Key dependencies:**

| Dependency | Purpose |
|------------|---------|
| `spring-boot-starter-web` | Embedded Tomcat + Jackson JSON serialization + Spring MVC annotations (`@RestController`, `@GetMapping`, etc.) |
| `jjwt-api` / `jjwt-impl` / `jjwt-jackson` | JWT encode, decode, and sign (HS256). Version 0.12.5 — uses `Jwts.parser().verifyWith()` API (replaces deprecated `parserBuilder()`) |
| `slf4j-simple` | Logging backend (SLF4J API → Simple implementation). Note: Spring Boot also brings Logback, so you'll see a warning about multiple SLF4J providers at startup — harmless in POC |
| `spring-boot-starter-test` | JUnit 5 test scaffold (not used yet) |

**Build plugins:**

- `spring-boot-maven-plugin` — sets `mainClass` to `com.interview.poc.Server`, enables `mvn package` to produce an executable fat JAR (`app.jar`)
- `maven-compiler-plugin` 3.11.0 — targets Java 17

**Production migration note:** When moving to Azure, add two deps to `pom.xml`:

```xml
<dependency>
    <groupId>com.azure</groupId>
    <artifactId>azure-security-keyvault-secrets</artifactId>
    <version>4.7.2</version>
</dependency>
<dependency>
    <groupId>com.azure</groupId>
    <artifactId>azure-identity</artifactId>
    <version>1.12.0</version>
</dependency>
```

---

### 2. `src/main/java/com/interview/poc/Server.java` — Application Entry Point

```java
@SpringBootApplication
public class Server {
    public static void main(String[] args) {
        SpringApplication.run(Server.class, args);
    }
}
```

**What it does:**

- `@SpringBootApplication` = `@Configuration` + `@EnableAutoConfiguration` + `@ComponentScan`
- Triggers auto-configuration of embedded Tomcat, Jackson, and Spring MVC
- Scans `com.interview.poc` package recursively for `@RestController`, `@Service`, etc.
- `SpringApplication.run()` boots the context, starts Tomcat on the configured port (default 8080)

**No manual HTTP server code needed** — Spring Boot handles it.

---

### 3. `src/main/java/com/interview/poc/resource/DocumentApi.java` — REST Controller

This is the **core file** — all HTTP endpoints live here.

**Class-level annotations:**
```java
@RestController          // Every method returns JSON directly (no View resolution)
@RequestMapping("/api")  // Base path for all endpoints
```

**Public endpoints (no JWT required):**

```java
@GetMapping("/tokens")
public ResponseEntity<Map<String, String>> getTokens()
```
- Calls `IdentityProvider.issueToken()` three times with hardcoded client IDs
- Returns `{"read": "<jwt>", "write": "<jwt>", "admin": "<jwt>"}`
- Tokens expire in 1 hour (`settings.jwt_expiry_seconds = 3600`)

```java
@GetMapping("/vault/credentials")
public ResponseEntity<Map<String, Object>> getVaultInfo()
```
- Calls `vault.readCredentials()`, masks the password, returns config as JSON
- Useful for debugging — shows what the app reads from the "vault"

**Protected endpoints (JWT required):**

Each takes a `String authorization` parameter (Spring injects the header value automatically):

```java
public ResponseEntity<?> list(@RequestHeader("Authorization") String authHeader)
```

**Flow inside every protected endpoint:**
1. Call `_authorize(authHeader, "GET")` — throws `AuthException` (401/403) on failure
2. Perform the operation (list, get, upload, delete)
3. Return JSON with the document + `role` field showing who made the call

**The `_authorize()` helper:**
```java
private UserRole _authorize(String authHeader, String method) {
    // 1. Strip "Bearer " prefix
    // 2. JwtValidator.validateToken() → throws if invalid/expired
    // 3. JwtValidator.extractRole() → maps client_id pattern to UserRole
    // 4. role.allows(method) → throws AuthException(403) if insufficient
    // 5. return role  (used by callers to stamp modifiedBy)
}
```

**Response envelope pattern:**
```json
{
  "message": "Document uploaded successfully",   // human-readable status
  "document": { ... },                            // the document object
  "siteUrl": "https://...",                       // which SharePoint site was targeted
  "role": "write"                                 // role that performed the action
}
```

---

### 4. `src/main/java/com/interview/poc/security/JwtValidator.java` — JWT Engine

**Responsibilities:** validate, parse, extract role, generate tokens.

**`validateToken(String token)` — lines 18-30:**
```java
// 1. Strip "Bearer " prefix if present
// 2. Jwts.parser().verifyWith(SIGNING_KEY).build()
//      → verifies HMAC-SHA256 signature using the shared secret
//      → checks expiry (exp claim)
//      → checks issuer (iss claim, if configured)
// 3. .parseSignedClaims(token).getPayload()
//      → returns the Claims payload (all JWT claims as a map)
```
- Throws `SecurityException` if token is null/blank
- Throws on invalid signature, expired token, or malformed JWT
- Uses jjwt 0.12.5 API (not the deprecated `parserBuilder()`)

**`extractRole(Claims claims)` — lines 32-42:**
```java
// Priority order:
// 1. "sub" (subject) claim → UserRole.fromClientId("write-client-poc-002") → WRITE
// 2. "role" claim        → UserRole.valueOf("admin")               → ADMIN
// 3. Fallback            → READ
```

**`generateToken(...)` — lines 44-52:**
```java
// Used by /api/tokens and TokenGenerator
// Builds: header={"alg":"HS256"} + payload={sub, role, iat, exp}
// Signs with HMAC-SHA256 using the shared secret
// Returns compact JWT string: "header.payload.signature"
```

---

### 5. `src/main/java/com/interview/poc/model/UserRole.java` — RBAC Enum

```java
public enum UserRole {
    READ("read",    "GET"),
    WRITE("write",  "GET,PUT"),
    ADMIN("admin",  "GET,PUT,DELETE");
}
```

**Design choices:**

- Each enum stores a **comma-delimited string** of allowed HTTP methods
- `allows("PUT")` does `allowedMethods.contains("PUT")` — simple substring check, case-insensitive
- `fromClientId(String)` maps Azure AD client IDs to roles:
  - contains `"admin"` → ADMIN
  - contains `"write"` → WRITE
  - else → READ (safe default: least privilege)

**Why `String` enum?** Spring Boot + Jackson serializes enums as strings by default, so `"read"` / `"write"` / `"admin"` appear cleanly in JSON without `@JsonValue` annotations.

---

### 6. `src/main/java/com/interview/poc/model/Document.java` — Document DTO

Plain POJO with Jackson-compatible getters/setters:
```java
public class Document {
    private String id;          // UUID, auto-generated on create
    private String title;       // document title
    private String content;     // document body
    private String modifiedBy;  // "write-user" / "read-user" / "admin-user"
    private Date modifiedAt;    // epoch ms timestamp
}
```

**Serialization flow:**
- **Inbound** (PUT): Spring Boot reads JSON body → Jackson calls setters → `Document` object created
- **Outbound** (GET): Spring Boot calls getters → Jackson writes JSON response

**`id` field:** `null` on input → `SharePointClient.createOrUpdateDocument()` generates `UUID.randomUUID().toString()` → saved to file as the filename

---

### 7. `src/main/java/com/interview/poc/model/SharePointCredentials.java` — Vault Secret DTO

Maps the JSON stored in `data/vault/credentials.json`:

| Field | Purpose | Production equivalent |
|-------|---------|----------------------|
| `siteUrl` | SharePoint site root URL | `https://contoso.sharepoint.com/sites/your-site` |
| `clientId` | Azure AD app (client) ID | Registered app in Azure AD |
| `tenantId` | Azure AD tenant ID | `00000000-...` |
| `certificatePath` | Path to X.509 cert PEM | Azure Key Vault certificate name |
| `keyPath` | Path to private key PEM | Fetched from Key Vault alongside cert |
| `username` | SharePoint login user | service-account@tenant.onmicrosoft.com |
| `password` | SharePoint password | Stored as Azure Key Vault secret |

---

### 8. `src/main/java/com/interview/poc/vault/VaultService.java` — Key Vault Simulation

**Local mode (current):**
- File: `data/vault/credentials.json`
- Static initializer block auto-creates the file with defaults if missing
- `readCredentials()` → `Files.readString()` → `ObjectMapper.readValue()` → `SharePointCredentials` object
- `writeCredentials()` → `ObjectMapper.writeValueAsString()` → `Files.writeString()`
- `maskedView()` → returns all fields except password (replaced with `***masked***`)

**Production mode (Azure Key Vault):**
```java
SecretClient client = new SecretClientBuilder()
    .vaultUrl("https://<vault-name>.vault.azure.net/")
    .credential(new DefaultAzureCredentialBuilder()
        .clientId(clientId)
        .clientCertificate(certPath, keyPath)
        .tenantId(tenantId)
        .build())
    .buildClient();

// Read
KeyVaultSecret secret = client.getSecret("sharepoint-credentials");

// Write
client.setSecret(new KeyVaultSecret("sharepoint-credentials", json));
```

Swapping is just replacing `Files.readString(VAULT_FILE)` with `client.getSecret("sharepoint-credentials").getValue()`.

---

### 9. `src/main/java/com/interview/poc/sharepoint/SharePointClient.java` — SharePoint Mock

**Local mode (current):**
- Documents stored as individual JSON files in `data/documents/{uuid}.json`
- `listDocuments()` → `Files.list(STORAGE)` → stream → sorted by ID
- `getDocument(id)` → reads `{id}.json`, returns null if missing
- `createOrUpdateDocument(doc)` → generates UUID if null, sets `modifiedAt`, writes JSON file
- `deleteDocument(id)` → `Files.deleteIfExists()`, returns boolean

**Production mode (SharePoint Online REST API):**
```java
// GET list
GET https://{tenant}.sharepoint.com/sites/{site}/_api/web/lists/getbytitle('Documents')/items
// GET one
GET https://.../items({id})
// CREATE/UPDATE
POST https://.../items  with body: {"__metadata":{"type":"SP.Data.DocumentsListItem"},"Title":"...","Content":"..."}
// DELETE
POST https://.../items({id})  with header X-HTTP-Method: DELETE, IF-MATCH: *
```

All SharePoint calls require `Authorization: Bearer <access_token>` acquired via Azure AD `client_credentials` grant using the X.509 cert.

---

### 10. `src/main/java/com/interview/poc/auth/IdentityProvider.java` — IdP Simulation

**Purpose:** Simulates what Azure AD or ForgeRock would do — issue JWTs for known client IDs.

**Current behavior (local POC):**
- `issueToken(clientId, role, expirySeconds)` → calls `JwtValidator.generateToken()` directly
- Uses the same `jwt.secret` as the validator (shared secret = symmetric HS256)
- Hardcoded client IDs: `read-client-poc-001`, `write-client-poc-002`, `admin-client-poc-003`

**Production paths:**

| Target | What changes |
|--------|-------------|
| **Azure AD** | Replace `issueToken()` with a call to `ConfidentialClientApplication.acquireTokenForClient()` using `ClientCertificateCredential`. Azure AD returns a real OAuth2 access token (not a self-signed JWT). |
| **ForgeRock** | Call ForgeRock AM `/access_token` endpoint with client cert mTLS, parse returned JWT, validate signature against ForgeRock's JWK endpoint. |

**Why `IdentityProvider` exists:** It isolates the "who issues tokens" concern. In POC, we self-sign. In production, we just swap this class — nothing else changes.

---

### 11. `src/main/java/com/interview/poc/config/AppConfig.java` — Configuration Loader

```java
public class AppConfig {
    private static final Properties props = new Properties();
    static {
        // Loads src/main/resources/application.properties at class load time
        try (InputStream is = AppConfig.class.getClassLoader()
                .getResourceAsStream("application.properties")) {
            props.load(is);
        }
    }
    public static String get(String key) { ... }
    public static String get(String key, String defaultValue) { ... }
}
```

**How it works:**
- Static initializer runs once when the class is first referenced
- Uses the **classpath classloader** to find `application.properties` inside the fat JAR
- Returns `null` if key is missing (unless a default is provided)
- Thread-safe for reads after static init completes

**Used by:** Every class that needs config: `JwtValidator`, `VaultService`, `SharePointClient`, `IdentityProvider`

---

### 12. `src/main/resources/application.properties` — Configuration File

Loaded by `AppConfig` at startup. Packaged inside the JAR.

```properties
server.port=8080              # Spring Boot embedded Tomcat port

vault.local.path=data/vault/credentials.json    # VaultService reads this
vault.cert.path=data/certificates/client-cert.pem
vault.key.path=data/certificates/client-key.pem

vault.client.id=00000000-0000-0000-0000-000000000000  # Azure AD app ID
vault.tenant.id=00000000-0000-0000-0000-000000000000  # Azure AD tenant ID
jwt.secret=YOUR-JWT-SECRET       # HS256 signing key (32+ chars)
jwt.issuer=https://login.microsoftonline.com/.../v2.0  # JWT issuer claim

document.storage.path=data/documents   # SharePointClient reads/writes here
```

**Docker override:** Any key can be overridden at runtime without rebuilding:
```bash
docker run -e "jwt.secret=new-secret" -e "server.port=9090" ...
```

---

### 13. `Dockerfile` — Multi-Stage Image Build

```dockerfile
FROM maven:3.9-eclipse-temurin-17-alpine AS build   # Stage 1: Maven + JDK
WORKDIR /app
COPY pom.xml .                                        # Cache deps layer
COPY src ./src                                         # Copy source
RUN mvn package -DskipTests -q                        # Compile + package fat JAR

FROM eclipse-temurin:17-jre-alpine                    # Stage 2: JRE only
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar           # Copy fat JAR (~60MB)
COPY data /app/data                                   # Mount vault + documents
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]           # Run Spring Boot
```

**Why multi-stage?**
- Stage 1 image (~500MB): has Maven + full JDK to compile
- Stage 2 image (~150MB): has only JRE + the compiled JAR
- Final image is smaller, has no build tools, fewer attack vectors

**Why `maven:3.9-eclipse-temurin-17-alpine` as build stage (not bare JDK)?**
- Alpine JDK doesn't include `mvn`. The original Dockerfile failed with `mvn: not found`
- The Maven base image includes Maven pre-installed

**Volume mount:** `data/` is `COPY`ed into the image at build time, then `-v "${PWD}/data:/app/data"` at runtime overlays it. This lets you edit `credentials.json` or add documents without rebuilding.

---

### 14. `src/main/java/com/interview/poc/util/Base64CertUtil.java` — Certificate Helper

```java
public static PrivateKey decodeBase64PrivateKey(String base64Key, String algorithm)
```
- Takes a base64-encoded private key string + algorithm name (e.g. `"RSA"`)
- Decodes → `PKCS8EncodedKeySpec` → `KeyFactory.generatePrivate()`
- Returns `java.security.PrivateKey`

**Current usage:** Stub for future X.509 cert loading from Key Vault. In POC, certs are loaded from PEM files on disk by `CertLoader` (not shown in active files, but referenced from `VaultService`).

---

## Key Design Decisions Explained

### Why Spring Boot instead of raw HttpServer?
- **Embedded Tomcat** — no external servlet container to install/configure
- **Dependency injection** — clean separation of concerns (VaultService, SharePointClient are swappable)
- **Fat JAR** — single artifact for Docker: `java -jar app.jar`
- **Production standard** — same stack used in enterprise Java microservices

### Why jjwt 0.12.5 API (`parser().verifyWith()`)?
- `parserBuilder()` was deprecated and removed in newer jjwt versions
- `verifyWith(SecretKey)` is the type-safe replacement
- Complies with modern JJWT best practices

### Why `UserRole` stores allowed methods as a `String`?
- Simplest possible RBAC check: `"GET,PUT".contains("PUT")`
- No database, no policy engine — matches the POC scope
- Human-readable in the source code
- Easy to extend (add `"DELETE"` to ADMIN's string)

### Why `AppConfig` uses `Properties` instead of Spring `@Value`?
- `AppConfig` is a **plain Java utility** — usable outside Spring context
- `TokenGenerator.main()` calls it directly — no Spring context needed
- `VaultService` static initializer runs before Spring context exists
- Makes the code testable without loading the full Spring context

### Why static `readCredentials()` / `writeCredentials()` in `VaultService`?
- The POC has **no database** — credentials live in a single JSON file
- Static methods = no Spring bean lifecycle needed
- In production, these become instance methods on a `@Service` bean with `SecretClient` injected

### Why `List<Document>` sorted by ID in `SharePointClient.listDocuments()`?
- Filesystem listing order is non-deterministic (depends on OS/filesystem)
- Sorting by ID gives deterministic output for the REST API response
- Matches how SharePoint would paginate/order results

---

## What Happens When You Run `mvn package -DskipTests`

1. **`process-resources`** — copies `application.properties` to `target/classes/`
2. **`compile`** — compiles all 12 Java files to `target/classes/`
3. **`package`** — Spring Boot plugin builds a fat JAR:
   - Unpacks all dependency JARs into `BOOT-INF/lib/`
   - Compiles classes into `BOOT-INF/classes/`
   - Writes `MANIFEST.MF` with `Main-Class: com.interview.poc.Server`
   - Output: `target/interview-poc-1.0.0.jar` (~60MB)
4. **Docker multi-stage** copies that JAR into a JRE-only Alpine image → final image ~150MB

---

## What Happens When You Hit `PUT /api/document/upload`

```
1. HTTP PUT request arrives at Tomcat port 8080
2. Spring Boot routes to DocumentApi.upload()
3. Spring reads JSON body → Jackson → Document object
4. _authorize() extracts JWT from header:
   a. Strips "Bearer "
   b. JJWT verifies HMAC-SHA256 signature
   c. JJWT checks "exp" claim (rejects if >1h old)
   d. extractRole() reads "sub" claim = "write-client-poc-002"
   e. UserRole.fromClientId() → WRITE
   f. WRITE.allows("PUT") → true ✅
5. doc.setModifiedBy("write-user")  ← role stamped on document
6. SharePointClient.createOrUpdateDocument(doc):
   a. id is null → UUID.randomUUID().toString()
   b. modifiedAt = new Date()
   c. writes to data/documents/{uuid}.json
7. VaultService.readCredentials() → reads data/vault/credentials.json
8. Returns ResponseEntity:
   {
     "message": "Document uploaded successfully",
     "document": { id, title, content, modifiedBy, modifiedAt },
     "siteUrl": "https://localhost:4567/sharepoint/sites/demo",
     "role": "write"
   }
```

---

## What Happens When You Hit `GET /api/document` with a READ token

```
1. Spring routes to DocumentApi.list()
2. _authorize(header, "GET"):
   a. JWT validates OK
   b. Role = READ
   c. READ.allows("GET") → true ✅
3. SharePointClient.listDocuments():
   a. Files.list(data/documents/)
   b. Reads each *.json → Document objects
   c. Sorts by id
4. Returns:
   {
     "documents": [ { ...doc1 }, { ...doc2 } ],
     "count": 2,
     "role": "read"
   }
```

---

## Security Model Summary

```
Token structure (HS256 signed):
{
  "sub": "write-client-poc-002",   ← maps to role via clientId pattern
  "role": "write",                 ← explicit role claim (fallback)
  "iat": 1781405000,               ← issued at (epoch seconds)
  "exp": 1781408600                ← expires at (iat + 3600s)
}

Signature = HMAC-SHA256(header.payload, jwt.secret)

Verification: server recomputes HMAC with its own jwt.secret
              → matches only if the same secret signed it
```

| Property | Value | Notes |
|----------|-------|-------|
| Algorithm | HS256 (symmetric) | POC only — production uses RS256 (asymmetric) with X.509 certs |
| Secret | 1 string in `application.properties` | Hardcoded for POC; in production, each party has its own key pair |
| Expiry | 3600s (1 hour) | Configurable via `jwt_expiry_seconds` |
| Issuer | Azure AD v2.0 endpoint (mock) | Validated by `jwt.decode(..., issuer=...)` — currently cosmetic in POC |

---

## Common Debugging Points

| Issue | Where to look |
|-------|---------------|
| 401 on `/api/tokens` | JWT auth skip was recently added — ensure you rebuilt the Docker image after the code change |
| 400 on `/api/document/{id}` | You're using a literal placeholder like `<id-from-step-2>` instead of a real UUID |
| Empty document list | Check `data/documents/` exists locally **and** inside the container (`docker exec poc-server ls /app/data/documents`) |
| Token expired | Tokens are valid 1 hour. Call `/api/tokens` again to get fresh ones |
| Port conflict | `application.properties` must say `server.port=8080` AND Docker must map `-p 8080:8080` |

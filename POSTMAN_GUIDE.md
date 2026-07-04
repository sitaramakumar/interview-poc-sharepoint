# Interview POC — Postman Collection Setup Guide

> **Server:** `http://localhost:8080` (Docker container `poc-server`)
> **Time:** 2026-06-14T17:40:24+05:30

---

## 1. Create a New Collection

1. Open Postman → **Collections** tab → **New Collection**
2. Name: `Interview POC - SharePoint Document Management`
3. (Optional) Description:
   > JWT-based role simulation with local SharePoint mock. Base URL: http://localhost:8080
4. Click **Create**

---

## 2. Create an Environment

Environments let you store variables (base URL, tokens) once and reuse them across requests.

1. Postman top bar → **Environments** → **New** (or **+**)
2. Name: `Interview POC - Local`
3. Add these **initial** and **current** values:

| Variable | Initial Value | Current Value | Type |
|----------|---------------|---------------|------|
| `baseUrl` | `http://localhost:8080` | `http://localhost:8080` | Default |
| `readToken` | *(empty)* | *(empty)* | Default |
| `writeToken` | *(empty)* | *(empty)* | Default |
| `adminToken` | *(empty)* | *(empty)* | Default |
| `docId` | *(empty)* | *(empty)* | Default |

4. Click **Save** → select `Interview POC - Local` from the environment dropdown (top-right)

---

## 3. Add Requests to the Collection

Open the collection → **Add Request** for each of the following.

---

### Request 1 — Get Tokens (public, no auth)

| Field | Value |
|-------|-------|
| **Name** | `Get All JWT Tokens` |
| **Method** | `GET` |
| **URL** | `{{baseUrl}}/api/tokens` |
| **Headers** | (none required) |
| **Tests** (to auto-save tokens) | *(paste the script below)* |

**Tests tab script** (auto-saves tokens to environment):
```javascript
const response = pm.response.json();
pm.environment.set("readToken", response.read);
pm.environment.set("writeToken", response.write);
pm.environment.set("adminToken", response.admin);
console.log("Tokens saved:", response.read.substring(0, 20) + "...");
```

> **Run this first** every time you open Postman — tokens expire after 1 hour.

---

### Request 2 — List Documents (READ)

| Field | Value |
|-------|-------|
| **Name** | `List All Documents` |
| **Method** | `GET` |
| **URL** | `{{baseUrl}}/api/document` |

**Headers tab:**
| Key | Value |
|-----|-------|
| `Authorization` | `Bearer {{readToken}}` |

**Tests tab** (auto-save first doc ID for later requests):
```javascript
const data = pm.response.json();
if (data.documents && data.documents.length > 0) {
    pm.environment.set("docId", data.documents[0].id);
    console.log("Saved docId:", data.documents[0].id);
}
```

---

### Request 3 — Get Document by ID (READ)

| Field | Value |
|-------|-------|
| **Name** | `Get Document by ID` |
| **Method** | `GET` |
| **URL** | `{{baseUrl}}/api/document/{{docId}}` |

**Headers tab:**
| Key | Value |
|-----|-------|
| `Authorization` | `Bearer {{readToken}}` |

---

### Request 4 — Upload Document (WRITE)

| Field | Value |
|-------|-------|
| **Name** | `Upload / Create Document` |
| **Method** | `PUT` |
| **URL** | `{{baseUrl}}/api/document/upload` |

**Headers tab:**
| Key | Value |
|-----|-------|
| `Authorization` | `Bearer {{writeToken}}` |
| `Content-Type` | `application/json` |

**Body tab:**
- Select **raw** → **JSON**
- Paste:
```json
{
  "title": "Project Plan",
  "content": "Q3 deliverables - sharepoint integration"
}
```

**Tests tab** (auto-save the new doc ID):
```javascript
const data = pm.response.json();
if (data.document && data.document.id) {
    pm.environment.set("docId", data.document.id);
    console.log("Uploaded docId:", data.document.id);
}
```

---

### Request 5 — Delete Document (ADMIN)

| Field | Value |
|-------|-------|
| **Name** | `Delete Document` |
| **Method** | `DELETE` |
| **URL** | `{{baseUrl}}/api/document/{{docId}}` |

**Headers tab:**
| Key | Value |
|-----|-------|
| `Authorization` | `Bearer {{adminToken}}` |

---

### Request 6 — Inspect Vault (public)

| Field | Value |
|-------|-------|
| **Name** | `View Vault Config` |
| **Method** | `GET` |
| **URL** | `{{baseUrl}}/api/vault/credentials` |

**Headers tab:** (none)

---

## 4. Organize into a Folder (Optional)

Inside the collection, create folders to group requests:

| Folder | Requests |
|--------|----------|
| `Auth` | Get All JWT Tokens, View Vault Config |
| `Documents` | List All Documents, Get Document by ID, Upload / Create Document, Delete Document |

Drag requests into folders in the collection sidebar.

---

## 5. Test Workflow (step by step)

1. **Select Environment:** `Interview POC - Local` (top-right dropdown)
2. **Run "Get All JWT Tokens"** → all three `*Token` variables populate automatically
3. **Run "Upload / Create Document"** → creates a doc, saves its ID to `docId`
4. **Run "List All Documents"** → shows all docs, refreshes `docId` if needed
5. **Run "Get Document by ID"** → fetch the doc saved in step 3
6. **Run "Delete Document"** → removes it (ADMIN only)

If you get `401 Unauthorized`, re-run **Get All JWT Tokens** — tokens expire after 1 hour.

---

## 6. Import as JSON (Alternative)

Pre-built files are in the `postman/` folder of this project.

**Import the collection:**
1. Postman → **Import** → **File** tab
2. Select: `interview-poc/postman/interview-poc-collection.json`
3. Click **Import**

**Import the environment:**
1. Postman → **Environments** → **Import**
2. Select: `interview-poc/postman/interview-poc-environment.json`
3. Click **Import**

Then select `Interview POC - Local` from the environment dropdown (top-right).
```json
{
  "info": {
    "name": "Interview POC - SharePoint Document Management",
    "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"
  },
  "item": [
    {
      "name": "Auth",
      "item": [
        {
          "name": "Get All JWT Tokens",
          "request": {
            "method": "GET",
            "header": [],
            "url": {
              "raw": "{{baseUrl}}/api/tokens",
              "host": ["{{baseUrl}}"],
              "path": ["api", "tokens"]
            }
          },
          "event": [
            {
              "listen": "test",
              "script": {
                "exec": [
                  "const response = pm.response.json();",
                  "pm.environment.set(\"readToken\", response.read);",
                  "pm.environment.set(\"writeToken\", response.write);",
                  "pm.environment.set(\"adminToken\", response.admin);",
                  "console.log(\"Tokens saved\");"
                ]
              }
            }
          ]
        },
        {
          "name": "View Vault Config",
          "request": {
            "method": "GET",
            "header": [],
            "url": {
              "raw": "{{baseUrl}}/api/vault/credentials",
              "host": ["{{baseUrl}}"],
              "path": ["api", "vault", "credentials"]
            }
          }
        }
      ]
    },
    {
      "name": "Documents",
      "item": [
        {
          "name": "List All Documents",
          "request": {
            "method": "GET",
            "header": [
              { "key": "Authorization", "value": "Bearer {{readToken}}" }
            ],
            "url": {
              "raw": "{{baseUrl}}/api/document",
              "host": ["{{baseUrl}}"],
              "path": ["api", "document"]
            }
          },
          "event": [
            {
              "listen": "test",
              "script": {
                "exec": [
                  "const data = pm.response.json();",
                  "if (data.documents && data.documents.length > 0) {",
                  "    pm.environment.set(\"docId\", data.documents[0].id);",
                  "}"
                ]
              }
            }
          ]
        },
        {
          "name": "Get Document by ID",
          "request": {
            "method": "GET",
            "header": [
              { "key": "Authorization", "value": "Bearer {{readToken}}" }
            ],
            "url": {
              "raw": "{{baseUrl}}/api/document/{{docId}}",
              "host": ["{{baseUrl}}"],
              "path": ["api", "document", "{{docId}}"]
            }
          }
        },
        {
          "name": "Upload / Create Document",
          "request": {
            "method": "PUT",
            "header": [
              { "key": "Authorization", "value": "Bearer {{writeToken}}" },
              { "key": "Content-Type", "value": "application/json" }
            ],
            "body": {
              "mode": "raw",
              "raw": "{\n  \"title\": \"Project Plan\",\n  \"content\": \"Q3 deliverables\"\n}"
            },
            "url": {
              "raw": "{{baseUrl}}/api/document/upload",
              "host": ["{{baseUrl}}"],
              "path": ["api", "document", "upload"]
            }
          },
          "event": [
            {
              "listen": "test",
              "script": {
                "exec": [
                  "const data = pm.response.json();",
                  "if (data.document && data.document.id) {",
                  "    pm.environment.set(\"docId\", data.document.id);",
                  "}"
                ]
              }
            }
          ]
        },
        {
          "name": "Delete Document",
          "request": {
            "method": "DELETE",
            "header": [
              { "key": "Authorization", "value": "Bearer {{adminToken}}" }
            ],
            "url": {
              "raw": "{{baseUrl}}/api/document/{{docId}}",
              "host": ["{{baseUrl}}"],
              "path": ["api", "document", "{{docId}}"]
            }
          }
        }
      ]
    }
  ]
}
```

---

## 7. Expected Responses

### 200 OK — Get All Tokens
```json
{
  "read": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJyZWFkLWNsaWVudC1wb2MtMDAxIiwicm9sZSI6InJlYWQiLCJpYXQiOjE3ODE0MDU5NDMsImV4cCI6MTc4MTQwOTU0M30...",
  "write": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ3cml0ZS1jbGllbnQtcG9jLTAwMiIsInJvbGUiOiJ3cml0ZSIsImlhdCI6MTc4MTQwNTk0MywiZXhwIjoxNzgxNDA5NTQzfQ...",
  "admin": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbi1jbGllbnQtcG9jLTAwMyIsInJvbGUiOiJhZG1pbiIsImlhdCI6MTc4MTQwNTk0MywiZXhwIjoxNzgxNDA5NTQzfQ..."
}
```

### 200 OK — List Documents
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

### 200 OK — Upload Document
```json
{
  "message": "Document uploaded successfully",
  "document": {
    "id": "a1b2c3d4e5f6...",
    "title": "Project Plan",
    "content": "Q3 deliverables",
    "modifiedBy": "write-user",
    "modifiedAt": 1781405000000
  },
  "siteUrl": "https://localhost:4567/sharepoint/sites/demo",
  "role": "write"
}
```

### 200 OK — Delete Document
```json
{
  "message": "Document deleted",
  "id": "a1b2c3d4e5f6...",
  "siteUrl": "https://localhost:4567/sharepoint/sites/demo",
  "role": "admin"
}
```

### 401 Unauthorized — expired or missing token
```json
{
  "error": "Unauthorized",
  "message": "Token expired",
  "hint": "Pass token in Authorization: Bearer <JWT>"
}
```

### 403 Forbidden — wrong role
```json
{
  "error": "Forbidden",
  "message": "Role 'read' does not allow PUT on this resource",
  "hint": "Pass token in Authorization: Bearer <JWT>"
}
```

---

## 8. Troubleshooting Postman

| Symptom | Fix |
|---------|-----|
| `401 Unauthorized` on everything | Click **Get All JWT Tokens** — tokens expire after 1 hour |
| `Could not send request` | Server not running. Start Docker: `docker compose up --build` |
| `{{docId}}` stays empty | Run **List All Documents** or **Upload** first — their Tests tab sets `docId` |
| Port conflict | Change `baseUrl` to `http://localhost:9090` if server runs on 9090 |
| `readToken` still shows old value | Right-click environment → **Clear** → re-run **Get All JWT Tokens** |

---

## 9. Run with Collection Runner (optional)

To run all requests in sequence (useful for demos):

1. Open the collection → **Runner** (top-right)
2. Select environment: `Interview POC - Local`
3. Select collection: `Interview POC - SharePoint Document Management`
4. Order: **Your collection order** (so "Get Tokens" runs first)
5. Data: *(none needed)*
6. Click **Run Interview POC - SharePoint Document Management**

The runner will execute all requests in order. The Tests scripts will pass `docId` forward automatically. If tokens expire mid-run, stop and re-run **Get All JWT Tokens** first.

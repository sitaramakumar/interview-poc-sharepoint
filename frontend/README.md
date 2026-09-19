# Document PoC — frontend

A single Streamlit UI that works against either backend unmodified, since
both expose the identical REST contract added in the login/refresh and ACL
PRs: `interview-poc-sharepoint` (Java) and `interview-poc-rust`.

## Run

```bash
pip install -r requirements.txt
streamlit run app.py
```

Point **Base URL** in the sidebar at whichever backend is running
(`http://localhost:8080/api` by default — both services default to port
8080, so run only one at a time unless you override `PORT`/`server.port`).

## What it demonstrates

- **Real login** — `POST /api/auth/login` against `UserStore`'s seeded demo
  accounts (`admin@poc.local` / `admin-demo-pass`, etc.), not a hardcoded
  token.
- **Real 401 → refresh → retry** — `ApiClient.request()` catches a `401`,
  calls `POST /api/auth/refresh` with the stored refresh token, and retries
  the original call once. If the refresh itself fails, the session is
  cleared and the login form comes back.
- **Real 403 state** — a `role.allows(method)` or resource-level ACL denial
  from the live backend is shown as-is, not simulated.
- Document list/get/upload/delete against the live backend, including
  setting `allowedRoles` on upload to exercise the resource-level ACL.

Everything here calls a real, running backend — there is no mocked or
hardcoded response in this file.

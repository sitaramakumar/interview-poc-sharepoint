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
hardcoded response in `app.py` itself.

## Tests

`test_app.py` drives the app headlessly with Streamlit's own
`streamlit.testing.v1.AppTest`, mocking only the network boundary
(`requests.post`/`requests.request`) with responses matching what a live
server actually returned when tested by hand — including the case where an
access token expires mid-request *and* the refresh token has also expired,
which is what caught a real bug: showing the warning and calling
`st.rerun()` in the same script run meant the warning rendered into a run
that got discarded before ever reaching the client. Fixed by stashing a
`session_expired_notice` flag in `session_state` and rendering it on the
login screen the rerun actually lands on.

```bash
pip install -r requirements-dev.txt
pytest test_app.py -v
```

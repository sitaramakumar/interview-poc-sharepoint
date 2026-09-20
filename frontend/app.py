"""
Streamlit front end for interview-poc-sharepoint / interview-poc-rust.

One UI for both backends: they expose an identical REST contract
(/api/auth/login, /api/auth/refresh, /api/document*), so this file works
unmodified against either — just point Base URL at whichever is running.

Run with: streamlit run app.py
"""
import requests
import streamlit as st

st.set_page_config(page_title="Document PoC", page_icon="📄", layout="centered")

if "access_token" not in st.session_state:
    st.session_state.access_token = None
    st.session_state.refresh_token = None
    st.session_state.role = None


class ApiClient:
    """
    Central request wrapper: attaches the bearer token, and on a 401 tries
    POST /api/auth/refresh once and retries — the same behavior the rejected
    axios-interceptor mockup claimed, wired to the /api/auth/refresh endpoint
    that now actually exists in both backends.
    """

    def __init__(self, base_url: str):
        self.base_url = base_url.rstrip("/")

    def _headers(self):
        token = st.session_state.access_token
        return {"Authorization": f"Bearer {token}"} if token else {}

    def request(self, method: str, path: str, **kwargs) -> requests.Response:
        url = f"{self.base_url}{path}"
        resp = requests.request(method, url, headers=self._headers(), timeout=10, **kwargs)

        if resp.status_code == 401 and st.session_state.refresh_token:
            if self._try_refresh():
                resp = requests.request(method, url, headers=self._headers(), timeout=10, **kwargs)

        return resp

    def _try_refresh(self) -> bool:
        try:
            resp = requests.post(
                f"{self.base_url}/auth/refresh",
                json={"refreshToken": st.session_state.refresh_token},
                timeout=10,
            )
        except requests.RequestException:
            return False

        if resp.status_code != 200:
            st.session_state.access_token = None
            st.session_state.refresh_token = None
            st.session_state.role = None
            return False

        st.session_state.access_token = resp.json()["accessToken"]
        return True


st.title("📄 Document PoC")

with st.sidebar:
    st.subheader("Backend")
    base_url = st.text_input(
        "Base URL",
        value=st.session_state.get("base_url", "http://localhost:8080/api"),
        help="Java default: http://localhost:8080/api — Rust also defaults "
             "to port 8080, so run only one at a time unless you set PORT.",
    )
    st.session_state.base_url = base_url

client = ApiClient(base_url)


def show_response(resp: requests.Response):
    """
    Shared handler for every document action. Checks session state, not just
    the status code: if ApiClient just cleared the tokens (refresh itself
    failed — both access and refresh token are dead), the response in hand is
    still whatever the ORIGINAL call returned (e.g. a raw "Invalid token:
    ExpiredSignature" 401), which is not a useful thing to show verbatim. A
    session that's fully dead gets one clear message and an immediate return
    to the login form, instead of a backend-internal string next to a page
    that still claims "Logged in".
    """
    if resp.status_code == 200:
        st.json(resp.json())
        return

    if st.session_state.access_token is None:
        # Don't st.warning() then immediately st.rerun(): rerun aborts this
        # script run right there, so that warning would render into a run
        # that gets discarded before the client ever draws it — a flash at
        # best. Stash the notice and show it on the login screen the new
        # run lands on instead, where the user will actually see it.
        st.session_state.session_expired_notice = True
        st.rerun()

    if resp.status_code == 403:
        st.error(f"403 Forbidden — {resp.text}")
    else:
        st.error(f"{resp.status_code} — {resp.text}")

# ── Login ──────────────────────────────────────────────────────────────────

if not st.session_state.access_token:
    if st.session_state.pop("session_expired_notice", False):
        st.warning("Your session has expired. Please log in again.")
    st.subheader("Log in")
    st.caption(
        "Demo accounts seeded by UserStore: read@poc.local / read-demo-pass, "
        "write@poc.local / write-demo-pass, admin@poc.local / admin-demo-pass."
    )
    client_id = st.text_input("Client ID", value="admin@poc.local", key="login_client_id")
    password = st.text_input("Password", value="admin-demo-pass", type="password", key="login_password")

    if st.button("Log in", type="primary", key="login_button"):
        try:
            resp = requests.post(f"{base_url}/auth/login",
                                  json={"clientId": client_id, "password": password},
                                  timeout=10)
        except requests.RequestException as e:
            st.error(f"Could not reach {base_url}: {e}")
        else:
            if resp.status_code == 200:
                body = resp.json()
                st.session_state.access_token = body["accessToken"]
                st.session_state.refresh_token = body["refreshToken"]
                st.session_state.role = body["role"]
                st.rerun()
            else:
                st.error(f"{resp.status_code} — {resp.text}")
    st.stop()

st.success(f"Logged in — role: **{st.session_state.role}**")
if st.button("Log out"):
    st.session_state.access_token = None
    st.session_state.refresh_token = None
    st.session_state.role = None
    st.rerun()

st.markdown("---")

# ── Document actions ─────────────────────────────────────────────────────────

tab_list, tab_get, tab_upload, tab_delete = st.tabs(["List", "Get", "Upload", "Delete"])

with tab_list:
    if st.button("List documents"):
        show_response(client.request("GET", "/document"))

with tab_get:
    doc_id = st.text_input("Document ID", key="get_id")
    if st.button("Get document"):
        show_response(client.request("GET", f"/document/{doc_id}"))

with tab_upload:
    title = st.text_input("Title", key="upload_title")
    content = st.text_area("Content", key="upload_content")
    allowed_roles = st.multiselect("Allowed roles (blank = defaults to your role)",
                                    ["READ", "WRITE", "ADMIN"])
    if st.button("Upload", key="upload_button"):
        body = {"title": title, "content": content}
        if allowed_roles:
            body["allowedRoles"] = allowed_roles
        show_response(client.request("PUT", "/document/upload", json=body))

with tab_delete:
    delete_id = st.text_input("Document ID", key="delete_id")
    if st.button("Delete", type="secondary"):
        show_response(client.request("DELETE", f"/document/{delete_id}"))

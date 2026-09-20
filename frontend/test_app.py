"""
Drives app.py headlessly with streamlit.testing.v1.AppTest, mocking the
network boundary (requests.post / requests.request) so the real Streamlit
control flow — login, then an upload that hits an expired access token AND
an expired refresh token — runs for real, without a live backend.

This exists specifically because the "session fully expired" fix (show a
clear message and return to the login form) is pure client-side session_state
logic with no network dependency of its own; the network-side behavior it
depends on (both tokens actually rejected as 401) was already verified live
against a running backend, and is reproduced here as canned responses.
"""
from unittest.mock import patch

from streamlit.testing.v1 import AppTest


class FakeResponse:
    def __init__(self, status_code, json_data=None, text=""):
        self.status_code = status_code
        self._json = json_data or {}
        self.text = text

    def json(self):
        return self._json


def fake_post_both_expired(url, json=None, timeout=None, **kwargs):
    if url.endswith("/auth/login"):
        return FakeResponse(200, {
            "accessToken": "expired-access-token",
            "refreshToken": "expired-refresh-token",
            "role": "admin",
        })
    if url.endswith("/auth/refresh"):
        # Matches what was actually observed curling a live server with a
        # hand-crafted expired refresh token.
        return FakeResponse(401, text='{"error":"Invalid or expired refresh token"}')
    raise AssertionError(f"unexpected POST {url}")


def fake_request_expired_access(method, url, headers=None, timeout=None, **kwargs):
    if method == "PUT" and url.endswith("/document/upload"):
        # Matches what was actually observed curling a live server with a
        # hand-crafted expired access token.
        return FakeResponse(401, text='{"error":"Invalid token: ExpiredSignature"}')
    raise AssertionError(f"unexpected {method} {url}")


def test_upload_with_both_tokens_expired_returns_to_the_login_form_with_a_clear_message():
    with patch("requests.post", side_effect=fake_post_both_expired), \
         patch("requests.request", side_effect=fake_request_expired_access):

        at = AppTest.from_file("app.py")
        at.run()

        at.text_input(key="login_client_id").set_value("admin@poc.local")
        at.text_input(key="login_password").set_value("admin-demo-pass")
        at.button(key="login_button").click().run()

        assert not at.exception
        assert at.session_state["access_token"] == "expired-access-token"

        at.tabs[2].button(key="upload_button").click().run()

        assert not at.exception
        # The session-expired branch calls st.rerun(), which AppTest follows
        # automatically — by the time .run() returns, both tokens are gone
        # and the login form (gated on access_token being falsy) is back.
        assert at.session_state["access_token"] is None
        assert at.session_state["refresh_token"] is None
        assert any("session has expired" in w.value.lower() for w in at.warning)
        assert any(ti.key == "login_client_id" for ti in at.text_input)


def test_upload_with_an_expired_access_token_but_a_valid_refresh_token_retries_and_succeeds():
    """
    The main case the whole feature exists for, run through the same harness
    so the fix for the edge case (above) is proven not to have broken it:
    access token expired, refresh succeeds, the original request is retried
    with the new access token and returns 200.
    """
    call_log = []

    def fake_post(url, json=None, timeout=None, **kwargs):
        if url.endswith("/auth/login"):
            return FakeResponse(200, {
                "accessToken": "old-access-token",
                "refreshToken": "still-valid-refresh-token",
                "role": "admin",
            })
        if url.endswith("/auth/refresh"):
            return FakeResponse(200, {"accessToken": "new-access-token"})
        raise AssertionError(f"unexpected POST {url}")

    def fake_request(method, url, headers=None, timeout=None, **kwargs):
        call_log.append(headers.get("Authorization"))
        if method == "PUT" and url.endswith("/document/upload"):
            if headers.get("Authorization") == "Bearer old-access-token":
                return FakeResponse(401, text='{"error":"Invalid token: ExpiredSignature"}')
            return FakeResponse(200, {"message": "Document uploaded successfully"})
        raise AssertionError(f"unexpected {method} {url}")

    with patch("requests.post", side_effect=fake_post), \
         patch("requests.request", side_effect=fake_request):

        at = AppTest.from_file("app.py")
        at.run()
        at.text_input(key="login_client_id").set_value("admin@poc.local")
        at.text_input(key="login_password").set_value("admin-demo-pass")
        at.button(key="login_button").click().run()

        at.tabs[2].button(key="upload_button").click().run()

        assert not at.exception
        assert at.session_state["access_token"] == "new-access-token"
        assert call_log == ["Bearer old-access-token", "Bearer new-access-token"]
        assert not any(w for w in at.warning)


if __name__ == "__main__":
    test_upload_with_both_tokens_expired_returns_to_the_login_form_with_a_clear_message()
    test_upload_with_an_expired_access_token_but_a_valid_refresh_token_retries_and_succeeds()
    print("PASSED")

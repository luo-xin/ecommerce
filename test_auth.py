from __future__ import annotations
import requests

BASE_URL = "http://127.0.0.1:8082"


def test_login_success():
    """Backdoor login returns valid token."""
    resp = requests.post(
        f"{BASE_URL}/api/internal/test/login-as",
        params={"userId": 1},
    )
    assert resp.status_code == 200, f"Login failed: {resp.status_code}"
    body = resp.json()
    data = body.get("data", body)
    assert "token" in data, f"No token in response: {body}"
    assert len(data["token"]) > 0


def test_login_fail_invalid_user():
    """Login with non-existent user should fail."""
    resp = requests.post(
        f"{BASE_URL}/api/internal/test/login-as",
        params={"userId": 999999},
    )
    # Should get error response
    body = resp.json()
    assert body.get("code", 0) != 0 or resp.status_code >= 400, \
        f"Expected error for invalid user, got: {body}"

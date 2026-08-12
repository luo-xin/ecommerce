from __future__ import annotations
import pytest
import requests

BASE_URL = "http://127.0.0.1:8082"


@pytest.fixture(scope="session")
def auth_token() -> str | None:
    """Get test token via backdoor login. Returns None if ecommerce not running."""
    try:
        resp = requests.post(
            f"{BASE_URL}/api/internal/test/login-as",
            params={"userId": 1},
            timeout=5,
        )
        if resp.status_code == 200:
            body = resp.json()
            # ecommerce wraps response in Result<T>: {code, msg, data}
            data = body.get("data", body)
            return data.get("token")
    except Exception:
        pass
    return None


@pytest.fixture(scope="session")
def client(auth_token: str | None) -> requests.Session | None:
    """HTTP session with auth header. None if login failed."""
    if not auth_token:
        return None
    s = requests.Session()
    s.headers.update({"Authorization": f"Bearer {auth_token}"})
    return s


@pytest.fixture(scope="session")
def product_id(client: requests.Session | None) -> int | None:
    """Get the first available product ID for use across tests."""
    if not client:
        return None
    try:
        resp = client.get(f"{BASE_URL}/api/products", params={"page": 1, "size": 1})
        items = resp.json().get("data", {}).get("items", []) or resp.json().get("data", {}).get("products", [])
        if items:
            return items[0].get("productId") or items[0].get("id")
    except Exception:
        pass
    return None

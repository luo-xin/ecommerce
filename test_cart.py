from __future__ import annotations

BASE_URL = "http://127.0.0.1:8082"


def test_add_to_cart(client, product_id):
    """POST /api/cart/items adds item to cart."""
    assert client is not None, "Authentication failed"
    assert product_id is not None, "No products in database"
    resp = client.post(
        f"{BASE_URL}/api/cart/items",
        json={"productId": product_id, "quantity": 1},
    )
    assert resp.status_code == 200, f"Add to cart failed: {resp.status_code} {resp.text}"
    body = resp.json()
    assert body["code"] == 0, f"Error: {body}"


def test_view_cart(client):
    """GET /api/cart returns cart items."""
    assert client is not None, "Authentication failed"
    resp = client.get(f"{BASE_URL}/api/cart")
    assert resp.status_code == 200
    body = resp.json()
    assert body["code"] == 0, f"Error: {body}"
    data = body["data"]
    assert "items" in data, f"No items field in cart: {data}"


def test_remove_from_cart(client, product_id):
    """DELETE /api/cart/items/{productId} removes item from cart."""
    assert client is not None, "Authentication failed"
    assert product_id is not None, "No products in database"
    resp = client.delete(f"{BASE_URL}/api/cart/items/{product_id}")
    assert resp.status_code == 200, f"Remove from cart failed: {resp.status_code} {resp.text}"
    body = resp.json()
    assert body["code"] == 0, f"Error: {body}"

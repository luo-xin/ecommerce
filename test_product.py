from __future__ import annotations
import requests

from conftest import BASE_URL


def test_list_products(client):
    """GET /api/products returns product list."""
    assert client is not None, "Authentication failed — ecommerce may not be running"
    resp = client.get(f"{BASE_URL}/api/products", params={"page": 1, "size": 10})
    assert resp.status_code == 200
    body = resp.json()
    assert body["code"] == 0, f"Error: {body}"
    data = body["data"]
    assert "items" in data, f"Expected items field in products response: {data}"
    assert "total" in data


def test_get_product_detail(client, product_id):
    """GET /api/products/{id} returns product detail."""
    assert client is not None, "Authentication failed"
    assert product_id is not None, "No products in database"
    resp = client.get(f"{BASE_URL}/api/products/{product_id}")
    assert resp.status_code == 200
    body = resp.json()
    assert body["code"] == 0, f"Error: {body}"
    product = body["data"]
    assert "name" in product, f"No name in product: {product}"
    assert "price" in product, f"No price in product: {product}"

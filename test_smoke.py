"""ecommerce CI smoke：登录后门 + 业务端点连通性。"""
import requests


def test_backdoor_login(base_url):
    r = requests.post(f"{base_url}/api/internal/test/login-as",
                      params={"userId": 1}, timeout=10)
    assert r.status_code == 200
    assert r.json()["data"]["token"]


def test_product_list(base_url, login_token):
    r = requests.get(f"{base_url}/api/products",
                     headers={"Authorization": f"Bearer {login_token}"},
                     timeout=10)
    assert r.status_code == 200

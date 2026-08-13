"""ecommerce smoke 测试：BASE_URL 环境变量覆盖（平台容器执行链路约定）。"""
import os
import requests
import pytest


@pytest.fixture
def base_url() -> str:
    return os.environ.get("BASE_URL", "http://127.0.0.1:8082")


@pytest.fixture
def login_token(base_url) -> str:
    r = requests.post(f"{base_url}/api/internal/test/login-as",
                      params={"userId": 1}, timeout=10)
    assert r.status_code == 200, f"免密登录失败: {r.status_code} {r.text}"
    token = r.json()["data"]["token"]
    assert token, "登录响应缺少 token"
    return token

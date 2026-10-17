"""
kharcha_v1/tests/test_api_main.py
Integration tests for FastAPI endpoints in main.py backed by FlatFileStorage.
"""
import pytest
from fastapi.testclient import TestClient

from kharcha_v1.kharcha_gateway.app.main import app, get_storage


@pytest.fixture
def client(storage):
    app.dependency_overrides[get_storage] = lambda: storage
    yield TestClient(app)
    app.dependency_overrides.clear()


def test_health_check(client):
    res = client.get("/api/v1/health")
    assert res.status_code == 200
    assert res.json() == {"status": "ok"}


def test_auth_and_profile_flow(client, storage):
    # 1. Signup
    signup_res = client.post(
        "/api/v1/auth/signup",
        json={"name": "Alice", "email": "alice@test.com", "password": "password123"},
    )
    assert signup_res.status_code == 201
    user_id = signup_res.json()["id"]
    assert (storage.db_path / "user_records.json").exists()

    # 2. Login
    login_res = client.post(
        "/api/v1/auth/login",
        json={"email": "alice@test.com", "password": "password123"},
    )
    assert login_res.status_code == 200
    token = login_res.json()["auth_token"]
    assert token is not None

    headers = {"Authorization": f"Bearer {token}"}

    # 3. Get /users/me
    me_res = client.get("/api/v1/users/me", headers=headers)
    assert me_res.status_code == 200
    assert me_res.json()["email"] == "alice@test.com"

    # 4. Patch /users/me
    patch_res = client.patch(
        "/api/v1/users/me",
        json={"name": "Alice Updated"},
        headers=headers,
    )
    assert patch_res.status_code == 200
    assert patch_res.json()["name"] == "Alice Updated"


def test_unauthenticated_request_rejected(client):
    res = client.get("/api/v1/groups")
    assert res.status_code == 401

    res = client.get("/api/v1/groups", headers={"Authorization": "Bearer not-a-jwt"})
    assert res.status_code == 401


def test_group_api_lifecycle(client, storage):
    # Register & Login
    client.post(
        "/api/v1/auth/signup",
        json={"name": "Host", "email": "host@test.com", "password": "password123"},
    )
    login_res = client.post(
        "/api/v1/auth/login",
        json={"email": "host@test.com", "password": "password123"},
    )
    token = login_res.json()["auth_token"]
    headers = {"Authorization": f"Bearer {token}"}

    # 1. Create Group
    create_res = client.post(
        "/api/v1/groups",
        json={"name": "Weekend Trip", "currency": "INR"},
        headers=headers,
    )
    assert create_res.status_code == 201
    group_id = create_res.json()["id"]
    assert (storage.db_path / "group_records.json").exists()

    # 2. Get Group Details
    get_res = client.get(f"/api/v1/groups/{group_id}", headers=headers)
    assert get_res.status_code == 200
    assert get_res.json()["name"] == "Weekend Trip"

    # 3. Lock Group
    lock_res = client.post(f"/api/v1/groups/{group_id}/lock", headers=headers)
    assert lock_res.status_code == 200
    assert lock_res.json()["status"] == "LOCKED"

    # 4. Unlock Group
    unlock_res = client.post(f"/api/v1/groups/{group_id}/unlock", headers=headers)
    assert unlock_res.status_code == 200
    assert unlock_res.json()["status"] == "OPEN"


def test_group_membership_enforcement(client):
    # User 1 (Creator)
    client.post(
        "/api/v1/auth/signup",
        json={"name": "User 1", "email": "u1@test.com", "password": "password123"},
    )
    t1 = client.post(
        "/api/v1/auth/login",
        json={"email": "u1@test.com", "password": "password123"},
    ).json()["auth_token"]

    # User 2 (Outsider)
    client.post(
        "/api/v1/auth/signup",
        json={"name": "User 2", "email": "u2@test.com", "password": "password123"},
    )
    t2 = client.post(
        "/api/v1/auth/login",
        json={"email": "u2@test.com", "password": "password123"},
    ).json()["auth_token"]

    # User 1 creates group
    group_id = client.post(
        "/api/v1/groups",
        json={"name": "Private Group", "currency": "INR"},
        headers={"Authorization": f"Bearer {t1}"},
    ).json()["id"]

    # User 2 tries to access User 1's group -> 403 Forbidden
    res = client.get(
        f"/api/v1/groups/{group_id}",
        headers={"Authorization": f"Bearer {t2}"},
    )
    assert res.status_code == 403
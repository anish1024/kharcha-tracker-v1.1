"""
kharcha_v1/tests/test_api_main.py
Integration tests for FastAPI endpoints in main.py backed by FlatFileStorage.
"""
from datetime import datetime, timedelta, timezone

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


def test_frontend_index_served(client):
    res = client.get("/")
    assert res.status_code == 200
    assert "text/html" in res.headers.get("content-type", "")
    assert "Kharcha Tracker" in res.text
    for asset in ("css/app.css", "js/api.js", "js/app.js", "js/icons.js"):
        assert asset in res.text
        asset_res = client.get("/" + asset)
        assert asset_res.status_code == 200


def test_frontend_hash_routes_serve_index(client):
    res = client.get("/index.html")
    assert res.status_code == 200
    assert "text/html" in res.headers.get("content-type", "")


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


def test_refresh_token_flow_api(client):
    client.post(
        "/api/v1/auth/signup",
        json={"name": "Rene", "email": "rene@test.com", "password": "password123"},
    )
    login_res = client.post(
        "/api/v1/auth/login",
        json={"email": "rene@test.com", "password": "password123"},
    )
    assert login_res.status_code == 200
    body = login_res.json()
    assert body["refresh_token"]
    assert body["refresh_token_expiry"]

    # Refresh: rotates both tokens, returns a usable access token.
    refresh_res = client.post(
        "/api/v1/auth/refresh", json={"refresh_token": body["refresh_token"]}
    )
    assert refresh_res.status_code == 200
    new_body = refresh_res.json()
    assert new_body["auth_token"] != body["auth_token"]
    assert new_body["refresh_token"] != body["refresh_token"]
    me_res = client.get(
        "/api/v1/users/me", headers={"Authorization": f"Bearer {new_body['auth_token']}"}
    )
    assert me_res.status_code == 200
    assert me_res.json()["email"] == "rene@test.com"

    # The old refresh token is single-use and can no longer be exchanged.
    replay_res = client.post(
        "/api/v1/auth/refresh", json={"refresh_token": body["refresh_token"]}
    )
    assert replay_res.status_code == 401

    # A refresh token cannot be used as a bearer access token.
    bearer_res = client.get(
        "/api/v1/users/me",
        headers={"Authorization": f"Bearer {new_body['refresh_token']}"},
    )
    assert bearer_res.status_code == 401

    # Missing/garbage refresh token is rejected.
    assert client.post("/api/v1/auth/refresh", json={}).status_code == 401
    assert (
        client.post(
            "/api/v1/auth/refresh", json={"refresh_token": "garbage"}
        ).status_code
        == 401
    )


def test_subscription_endpoints_api(client):
    client.post(
        "/api/v1/auth/signup",
        json={"name": "Sub", "email": "sub@test.com", "password": "password123"},
    )
    token = client.post(
        "/api/v1/auth/login",
        json={"email": "sub@test.com", "password": "password123"},
    ).json()["auth_token"]
    headers = {"Authorization": f"Bearer {token}"}

    # A fresh account is on the 1-year trial (unlimited).
    status = client.get("/api/v1/users/me/subscription", headers=headers)
    assert status.status_code == 200
    body = status.json()
    assert body["plan"] == "none"
    assert body["is_limited"] is False
    assert body["is_expired"] is False
    assert body["trial_ends_at"] is not None
    assert body["entries_remaining"] is None

    # Activate a yearly plan via the mock payment endpoint.
    act = client.post("/api/v1/subscription", json={"plan": "yearly"}, headers=headers)
    assert act.status_code == 200
    assert act.json()["plan"] == "yearly"
    assert act.json()["plan_status"] == "active"
    assert act.json()["is_limited"] is False

    # Invalid plan is rejected.
    bad = client.post("/api/v1/subscription", json={"plan": "gold"}, headers=headers)
    assert bad.status_code == 422

    # Subscription endpoints require auth.
    assert client.get("/api/v1/users/me/subscription").status_code == 401
    assert client.post("/api/v1/subscription", json={"plan": "monthly"}).status_code == 401


def test_magic_link_flow_api(client):
    # Request a link for a brand-new email; the response carries the token (dev).
    req = client.post("/api/v1/auth/magic-link", json={"email": "ssouser@test.com"})
    assert req.status_code == 202
    body = req.json()
    assert body["magic_token"]
    assert "#/magic?token=" in body["magic_link"]

    # Verifying the token signs the user in and auto-creates the account.
    verify = client.post(
        "/api/v1/auth/magic-link/verify", json={"token": body["magic_token"]}
    )
    assert verify.status_code == 200
    session = verify.json()
    assert session["auth_token"]

    me = client.get(
        "/api/v1/users/me",
        headers={"Authorization": f"Bearer {session['auth_token']}"},
    )
    assert me.status_code == 200
    assert me.json()["email"] == "ssouser@test.com"

    # The token is single-use.
    replay = client.post(
        "/api/v1/auth/magic-link/verify", json={"token": body["magic_token"]}
    )
    assert replay.status_code == 401

    # Bad email and bad token are rejected.
    assert client.post("/api/v1/auth/magic-link", json={"email": "nope"}).status_code == 422
    assert client.post(
        "/api/v1/auth/magic-link/verify", json={"token": "garbage"}
    ).status_code == 401


def test_entry_gate_api(client, storage):
    signup = client.post(
        "/api/v1/auth/signup",
        json={"name": "Gated", "email": "gated@test.com", "password": "password123"},
    )
    user_id = signup.json()["id"]
    token = client.post(
        "/api/v1/auth/login",
        json={"email": "gated@test.com", "password": "password123"},
    ).json()["auth_token"]
    headers = {"Authorization": f"Bearer {token}"}

    # A user still in trial is unlimited: entries never 403.
    assert client.post("/api/v1/expenses", json={}, headers=headers).status_code == 200

    # Expire the trial; the next POST_TRIAL_ENTRY_LIMIT entries are allowed.
    record = storage.get_record(str(user_id), "user_record")
    record.trial_ends_at = datetime.now(timezone.utc) - timedelta(days=1)
    storage.save_record(record, schema="user_record")

    for _ in range(3):
        assert client.post("/api/v1/expenses", json={}, headers=headers).status_code == 200

    # The fourth attempt is blocked with the machine-readable code.
    blocked = client.post("/api/v1/expenses", json={}, headers=headers)
    assert blocked.status_code == 403
    assert blocked.json()["detail"]["code"] == "SUBSCRIPTION_REQUIRED"

    # Activating a plan lifts the gate immediately.
    client.post("/api/v1/subscription", json={"plan": "monthly"}, headers=headers)
    assert client.post("/api/v1/expenses", json={}, headers=headers).status_code == 200


def _auth_headers(client, email, name):
    client.post(
        "/api/v1/auth/signup",
        json={"name": name, "email": email, "password": "password123"},
    )
    token = client.post(
        "/api/v1/auth/login",
        json={"email": email, "password": "password123"},
    ).json()["auth_token"]
    return {"Authorization": f"Bearer {token}"}


def test_invitation_flow_api(client):
    owner_h = _auth_headers(client, "owner@inv.com", "Owner")
    guest_h = _auth_headers(client, "guest@inv.com", "Guest")

    group_id = client.post(
        "/api/v1/groups",
        json={"name": "Trip", "currency": "INR"},
        headers=owner_h,
    ).json()["id"]

    # Owner invites the guest by email -> a pending invitation.
    inv = client.post(
        f"/api/v1/groups/{group_id}/invitations",
        json={"email": "guest@inv.com"},
        headers=owner_h,
    )
    assert inv.status_code == 200
    assert inv.json()["status"] == "pending"

    # The guest sees it under My invitations.
    mine = client.get("/api/v1/users/me/invitations", headers=guest_h).json()
    assert len(mine) == 1
    assert mine[0]["invitee_email"] == "guest@inv.com"

    # Guest accepts -> becomes a member; owner is notified.
    accepted = client.post(
        f"/api/v1/invitations/{mine[0]['id']}/accept", headers=guest_h
    )
    assert accepted.status_code == 200
    assert accepted.json()["status"] == "accepted"

    members = client.get(f"/api/v1/groups/{group_id}", headers=guest_h).json()["members"]
    guest_id = client.get("/api/v1/users/me", headers=guest_h).json()["id"]
    assert guest_id in members

    notes = client.get("/api/v1/users/me/notifications", headers=owner_h).json()
    assert len(notes) == 1
    assert notes[0]["kind"] == "GROUP_INVITE_ACCEPTED"

    # Owner marks notifications read.
    assert client.post("/api/v1/users/me/notifications/read", headers=owner_h).json() == 1
    unread = client.get(
        "/api/v1/users/me/notifications?unread_only=true", headers=owner_h
    ).json()
    assert unread == []

    # A second invite that is declined notifies the owner with a different kind.
    owner_h2 = _auth_headers(client, "owner2@inv.com", "Owner2")
    guest_h2 = _auth_headers(client, "guest2@inv.com", "Guest2")
    gid2 = client.post(
        "/api/v1/groups", json={"name": "Cabin", "currency": "INR"}, headers=owner_h2
    ).json()["id"]
    client.post(
        f"/api/v1/groups/{gid2}/invitations",
        json={"email": "guest2@inv.com"},
        headers=owner_h2,
    )
    pending2 = client.get("/api/v1/users/me/invitations", headers=guest_h2).json()
    declined = client.post(
        f"/api/v1/invitations/{pending2[0]['id']}/decline", headers=guest_h2
    )
    assert declined.status_code == 200
    assert declined.json()["status"] == "rejected"
    notes2 = client.get("/api/v1/users/me/notifications", headers=owner_h2).json()
    assert len(notes2) == 1
    assert notes2[0]["kind"] == "GROUP_INVITE_DECLINED"

    # Accepting a decline again is rejected (422).
    again = client.post(
        f"/api/v1/invitations/{pending2[0]['id']}/accept", headers=guest_h2
    )
    assert again.status_code == 422


def test_invite_code_join_api(client):
    owner_h = _auth_headers(client, "icowner@x.com", "Owner")
    joiner_h = _auth_headers(client, "icjoiner@x.com", "Joiner")

    group_id = client.post(
        "/api/v1/groups",
        json={"name": "Codes", "currency": "INR"},
        headers=owner_h,
    ).json()["id"]

    # The endpoint returns the deterministic per-(group, member) code.
    gen = client.post(f"/api/v1/groups/{group_id}/invite", headers=owner_h)
    assert gen.status_code == 200
    code = gen.json()["invite_code"]
    assert len(code) == 8

    # A second user joins with that code (case-insensitive).
    joined = client.post(
        f"/api/v1/groups/{group_id}/invite/accept",
        json={"invite_code": code.lower()},
        headers=joiner_h,
    )
    assert joined.status_code == 200
    joiner_id = client.get("/api/v1/users/me", headers=joiner_h).json()["id"]
    assert joiner_id in joined.json()["members"]

    # A bogus code is rejected.
    bad = client.post(
        f"/api/v1/groups/{group_id}/invite/accept",
        json={"invite_code": "ZZZZZZZZ"},
        headers=owner_h,
    )
    assert bad.status_code == 404


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
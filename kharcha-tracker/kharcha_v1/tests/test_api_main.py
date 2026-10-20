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

    # A user still in trial is unlimited: the entry gate never 403s.
    # (An empty payload now fails expense validation with 422 — which still
    # proves the gate let the request through.)
    allowed = client.post("/api/v1/expenses", json={}, headers=headers)
    assert allowed.status_code == 422

    # Expire the trial; the next POST_TRIAL_ENTRY_LIMIT entries are allowed.
    record = storage.get_record(str(user_id), "user_record")
    record.trial_ends_at = datetime.now(timezone.utc) - timedelta(days=1)
    storage.save_record(record, schema="user_record")

    for _ in range(3):
        assert client.post("/api/v1/expenses", json={}, headers=headers).status_code == 422

    # The fourth attempt is blocked with the machine-readable code.
    blocked = client.post("/api/v1/expenses", json={}, headers=headers)
    assert blocked.status_code == 403
    assert blocked.json()["detail"]["code"] == "SUBSCRIPTION_REQUIRED"

    # Activating a plan lifts the gate immediately.
    client.post("/api/v1/subscription", json={"plan": "monthly"}, headers=headers)
    assert client.post("/api/v1/expenses", json={}, headers=headers).status_code == 422


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


def test_group_members_endpoint(client):
    owner_h = _auth_headers(client, "memowner@x.com", "MemOwner")
    bob_h = _auth_headers(client, "membob@x.com", "Bob")
    carol_h = _auth_headers(client, "memcarol@x.com", "Carol")

    group_id = client.post(
        "/api/v1/groups",
        json={"name": "Members Group", "currency": "INR"},
        headers=owner_h,
    ).json()["id"]

    res = client.post(
        f"/api/v1/groups/{group_id}/members",
        json={"email": "membob@x.com"},
        headers=owner_h,
    )
    assert res.status_code in (200, 201)

    members = client.get(f"/api/v1/groups/{group_id}/members", headers=owner_h)
    assert members.status_code == 200
    body = members.json()
    assert len(body) == 2
    emails = {m["email"] for m in body}
    assert emails == {"memowner@x.com", "membob@x.com"}
    assert all(m["id"] and m["name"] for m in body)

    # Non-member is rejected.
    outsider = client.get(f"/api/v1/groups/{group_id}/members", headers=carol_h)
    assert outsider.status_code == 403


def test_expense_flow_api(client):
    owner_h = _auth_headers(client, "expowner@x.com", "ExpOwner")
    bob_h = _auth_headers(client, "expbob@x.com", "ExpBob")

    group_id = client.post(
        "/api/v1/groups",
        json={"name": "Expense Group", "currency": "INR"},
        headers=owner_h,
    ).json()["id"]
    client.post(
        f"/api/v1/groups/{group_id}/members",
        json={"email": "expbob@x.com"},
        headers=owner_h,
    )
    owner_id = client.get("/api/v1/users/me", headers=owner_h).json()["id"]
    bob_id = client.get("/api/v1/users/me", headers=bob_h).json()["id"]

    # Create — EQUAL split among both members, owner pays.
    created = client.post(
        "/api/v1/expenses",
        json={
            "group_id": group_id,
            "expn_ctgry": "GROCERY",
            "expn_desc": "Groceries",
            "expn_amnt": 100.0,
            "split_mode": "EQUAL",
            "split_user_ids": [owner_id, bob_id],
            "paid_by_user_id": owner_id,
        },
        headers=owner_h,
    )
    assert created.status_code == 201
    exp = created.json()
    assert exp["trans_id"] == 1
    assert exp["status"] == "Open"
    split_total = sum(s["percentage"] for s in exp["expn_splt"])
    assert abs(split_total - 100.0) < 0.01

    # List for the group.
    listed = client.get(f"/api/v1/groups/{group_id}/expenses", headers=owner_h)
    assert listed.status_code == 200
    assert len(listed.json()) == 1

    # Fetch by trans_id (both members can read).
    fetched = client.get("/api/v1/expenses/1", headers=bob_h)
    assert fetched.status_code == 200
    assert fetched.json()["expn_desc"] == "Groceries"

    # Update description only.
    patched = client.patch(
        "/api/v1/expenses/1",
        json={"expn_desc": "Weekly groceries"},
        headers=owner_h,
    )
    assert patched.status_code == 200
    assert patched.json()["expn_desc"] == "Weekly groceries"
    assert patched.json()["expn_amnt"] == 100.0

    # Delete (soft) then 404 on fetch.
    deleted = client.delete("/api/v1/expenses/1", headers=owner_h)
    assert deleted.status_code == 200
    gone = client.get("/api/v1/expenses/1", headers=owner_h)
    assert gone.status_code == 404


def test_expense_validation_api(client):
    owner_h = _auth_headers(client, "expvalid@x.com", "ExpValid")
    bob_h = _auth_headers(client, "expvalidb@x.com", "ExpBob2")

    group_id = client.post(
        "/api/v1/groups",
        json={"name": "Validation Group", "currency": "INR"},
        headers=owner_h,
    ).json()["id"]
    client.post(
        f"/api/v1/groups/{group_id}/members",
        json={"email": "expvalidb@x.com"},
        headers=owner_h,
    )
    owner_id = client.get("/api/v1/users/me", headers=owner_h).json()["id"]
    bob_id = client.get("/api/v1/users/me", headers=bob_h).json()["id"]

    base = {
        "group_id": group_id,
        "expn_ctgry": "GROCERY",
        "expn_desc": "Test",
        "expn_amnt": 50.0,
        "paid_by_user_id": owner_id,
    }

    # Retired category rejected.
    bad_ctgry = client.post(
        "/api/v1/expenses",
        json={**base, "expn_ctgry": "SETTLEMENT",
              "split_mode": "EQUAL", "split_user_ids": [owner_id]},
        headers=owner_h,
    )
    assert bad_ctgry.status_code == 422

    # Percentage split not summing to 100 rejected.
    bad_pct = client.post(
        "/api/v1/expenses",
        json={**base,
              "split_mode": "PERCENTAGE",
              "expn_splt": [
                  {"user_id": owner_id, "percentage": 60.0},
                  {"user_id": bob_id, "percentage": 30.0},
              ]},
        headers=owner_h,
    )
    assert bad_pct.status_code == 422

    # FIXED paid amount mismatch rejected.
    bad_fixed = client.post(
        "/api/v1/expenses",
        json={**base,
              "split_mode": "FIXED",
              "paid_by_user_id": bob_id,
              "expn_splt": [
                  {"user_id": owner_id, "amt": 30.0},
                  {"user_id": bob_id, "amt": 10.0},
              ]},
        headers=owner_h,
    )
    assert bad_fixed.status_code == 422

    # Locked group rejects new expenses.
    client.post(f"/api/v1/groups/{group_id}/lock", headers=owner_h)
    locked = client.post(
        "/api/v1/expenses",
        json={**base, "split_mode": "EQUAL", "split_user_ids": [owner_id]},
        headers=owner_h,
    )
    assert locked.status_code == 422


def test_expense_paid_by_update_api(client):
    owner_h = _auth_headers(client, "expcontrib@x.com", "ExpContrib")
    bob_h = _auth_headers(client, "expcontribb@x.com", "ExpContribBob")

    group_id = client.post(
        "/api/v1/groups",
        json={"name": "Contrib Group", "currency": "INR"},
        headers=owner_h,
    ).json()["id"]
    client.post(
        f"/api/v1/groups/{group_id}/members",
        json={"email": "expcontribb@x.com"},
        headers=owner_h,
    )
    owner_id = client.get("/api/v1/users/me", headers=owner_h).json()["id"]
    bob_id = client.get("/api/v1/users/me", headers=bob_h).json()["id"]

    created = client.post(
        "/api/v1/expenses",
        json={
            "group_id": group_id,
            "expn_ctgry": "GROCERY",
            "expn_desc": "Shared shop",
            "expn_amnt": 100.0,
            "split_mode": "EQUAL",
            "split_user_ids": [owner_id, bob_id],
            "paid_by_user_id": owner_id,
        },
        headers=owner_h,
    )
    assert created.status_code == 201
    assert created.json()["paid_by"] == [{"user_id": owner_id, "amt": 100.0}]

    # Redistribute: owner paid 70, bob paid 30.
    patched = client.patch(
        "/api/v1/expenses/1",
        json={
            "paid_by": [
                {"user_id": owner_id, "amt": 70.0},
                {"user_id": bob_id, "amt": 30.0},
            ]
        },
        headers=owner_h,
    )
    assert patched.status_code == 200
    paid = {p["user_id"]: p["amt"] for p in patched.json()["paid_by"]}
    assert paid == {owner_id: 70.0, bob_id: 30.0}
    assert patched.json()["expn_amnt"] == 100.0

    # Contributions not summing to the expense amount rejected (422).
    bad_sum = client.patch(
        "/api/v1/expenses/1",
        json={"paid_by": [{"user_id": owner_id, "amt": 60.0}, {"user_id": bob_id, "amt": 30.0}]},
        headers=owner_h,
    )
    assert bad_sum.status_code == 422

def test_activity_date_range_api(client):
    owner_h = _auth_headers(client, "actdate@x.com", "ActDate")
    other_h = _auth_headers(client, "actdate2@x.com", "ActOther")

    group_id = client.post(
        "/api/v1/groups",
        json={"name": "Activity Range", "currency": "INR"},
        headers=owner_h,
    ).json()["id"]
    url = f"/api/v1/groups/{group_id}/activity"

    # Baseline: group CREATE audit exists and the header reports the total.
    res = client.get(url, headers=owner_h)
    assert res.status_code == 200
    total = int(res.headers["X-Total-Count"])
    assert total >= 1

    # Header reflects the filtered total, independent of limit/offset.
    page = client.get(
        url, params={"limit": 1, "offset": total}, headers=owner_h
    )
    assert page.status_code == 200
    assert page.json() == []
    assert int(page.headers["X-Total-Count"]) == total

    # Future date range -> empty page, zero total.
    future = (datetime.now(timezone.utc) + timedelta(days=2)).isoformat()
    empty = client.get(url, params={"date_from": future}, headers=owner_h)
    assert empty.status_code == 200
    assert empty.json() == []
    assert int(empty.headers["X-Total-Count"]) == 0

    # Past range still includes the CREATE row.
    past = (datetime.now(timezone.utc) - timedelta(days=1)).isoformat()
    now = datetime.now(timezone.utc).isoformat()
    recent = client.get(url, params={"date_from": past, "date_to": now}, headers=owner_h)
    assert recent.status_code == 200
    assert len(recent.json()) == total
    assert int(recent.headers["X-Total-Count"]) == total

    # Inverted range -> 422 with a message.
    inverted = client.get(
        url, params={"date_from": now, "date_to": past}, headers=owner_h
    )
    assert inverted.status_code == 422

    # Malformed date -> 422 (FastAPI query parsing).
    malformed = client.get(url, params={"date_from": "not-a-date"}, headers=owner_h)
    assert malformed.status_code == 422

    # Non-member -> 403.
    assert client.get(url, headers=other_h).status_code == 403


def test_groups_sort_activity_api(client):
    owner_h = _auth_headers(client, "sortact@x.com", "SortAct")

    g1 = client.post(
        "/api/v1/groups",
        json={"name": "First", "currency": "INR"},
        headers=owner_h,
    ).json()["id"]
    g2 = client.post(
        "/api/v1/groups",
        json={"name": "Second", "currency": "INR"},
        headers=owner_h,
    ).json()["id"]

    default = client.get("/api/v1/groups", headers=owner_h)
    assert default.status_code == 200
    assert [g["id"] for g in default.json()] == [g1, g2]

    # Locking g1 makes it the most recent audit anywhere -> activity head.
    assert client.post(f"/api/v1/groups/{g1}/lock", headers=owner_h).status_code == 200
    ordered = client.get(
        "/api/v1/groups", params={"sort": "activity"}, headers=owner_h
    )
    assert ordered.status_code == 200
    assert [g["id"] for g in ordered.json()] == [g1, g2]
    # Default sort still untouched by the lock.
    again = client.get("/api/v1/groups", headers=owner_h)
    assert [g["id"] for g in again.json()] == [g1, g2]

    # Invalid sort value -> 422.
    bad = client.get("/api/v1/groups", params={"sort": "bogus"}, headers=owner_h)
    assert bad.status_code == 422

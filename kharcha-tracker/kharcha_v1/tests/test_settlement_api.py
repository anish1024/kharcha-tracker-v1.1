"""
kharcha_v1/tests/test_settlement_api.py
Integration tests for the settlement endpoints wired in main.py (plan S6).
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


def _user_id(headers):
    # resolve via /users/me using the bearer token
    from kharcha_v1.kharcha_gateway.app.main import get_storage as _gs

    storage = app.dependency_overrides[_gs]()
    token = headers["Authorization"].split("Bearer ", 1)[1]
    from kharcha_v1.kharcha_gateway.kharcha_service import auth_service

    user = auth_service.get_current_user(storage, token)
    return user.id


def _setup_group_with_expense(client, tag="s"):
    """Two users, one group, one 100 expense paid by owner, equal split."""
    owner_h = _auth_headers(client, f"own{tag}@x.com", "Owner")
    bob_h = _auth_headers(client, f"bob{tag}@x.com", "Bob")
    owner_id = _user_id(owner_h)
    bob_id = _user_id(bob_h)

    group_id = client.post(
        "/api/v1/groups",
        json={"name": "Settle Group", "currency": "INR"},
        headers=owner_h,
    ).json()["id"]
    client.post(
        f"/api/v1/groups/{group_id}/members",
        json={"email": f"bob{tag}@x.com"},
        headers=owner_h,
    )

    exp = client.post(
        "/api/v1/expenses",
        json={
            "group_id": group_id,
            "expn_ctgry": "GROCERY",
            "expn_desc": "Groceries",
            "expn_amnt": 100.0,
            "split_mode": "EQUAL",
            "split_user_ids": [str(owner_id), str(bob_id)],
            "paid_by_user_id": str(owner_id),
        },
        headers=owner_h,
    )
    assert exp.status_code == 201
    trans_id = exp.json()["trans_id"]

    return {
        "owner_h": owner_h,
        "bob_h": bob_h,
        "owner_id": owner_id,
        "bob_id": bob_id,
        "group_id": group_id,
        "trans_id": trans_id,
    }


# ---------------------------------------------------------------------------
# Preview
# ---------------------------------------------------------------------------
def test_preview_settlement_api(client):
    ctx = _setup_group_with_expense(client, "pv")
    res = client.get(
        f"/api/v1/groups/{ctx['group_id']}/settle", headers=ctx["owner_h"]
    )
    assert res.status_code == 200
    body = res.json()
    assert body["group_status"] == "OPEN"
    assert len(body["proposed_payouts"]) == 1
    payout = body["proposed_payouts"][0]
    assert payout["payor_id"] == str(ctx["bob_id"])
    assert payout["payee_id"] == str(ctx["owner_id"])
    assert abs(payout["amt"] - 50.0) < 0.001
    assert body["open_payouts"] == []
    assert body["orphan_payouts"] == []


def test_preview_settlement_filters_by_user(client):
    ctx = _setup_group_with_expense(client, "pf")
    res = client.get(
        f"/api/v1/groups/{ctx['group_id']}/settle?user_id={ctx['bob_id']}",
        headers=ctx["owner_h"],
    )
    assert res.status_code == 200
    for payout in res.json()["proposed_payouts"]:
        assert str(ctx["bob_id"]) in (payout["payor_id"], payout["payee_id"])


def test_preview_settlement_non_member_forbidden(client):
    ctx = _setup_group_with_expense(client, "pn")
    outsider_h = _auth_headers(client, "outpv@x.com", "Outsider")
    res = client.get(
        f"/api/v1/groups/{ctx['group_id']}/settle", headers=outsider_h
    )
    assert res.status_code == 403


# ---------------------------------------------------------------------------
# settle_user (POST /settle/me)
# ---------------------------------------------------------------------------
def test_settle_me_api(client):
    ctx = _setup_group_with_expense(client, "sm")
    res = client.post(
        f"/api/v1/groups/{ctx['group_id']}/settle/me",
        json={},
        headers=ctx["bob_h"],
    )
    assert res.status_code == 200
    payouts = res.json()
    assert len(payouts) == 1
    assert payouts[0]["status"] == "PENDING"
    assert abs(payouts[0]["amt"] - 50.0) < 0.001

    rounds = client.get(
        f"/api/v1/groups/{ctx['group_id']}/settlement-rounds", headers=ctx["bob_h"]
    ).json()
    assert len(rounds) == 1
    assert rounds[0]["kind"] == "INDIVIDUAL"


def test_settle_me_idempotent_api(client):
    ctx = _setup_group_with_expense(client, "si")
    client.post(
        f"/api/v1/groups/{ctx['group_id']}/settle/me", json={}, headers=ctx["bob_h"]
    )
    # second run: nothing new outstanding (commitment rule) -> empty list
    res = client.post(
        f"/api/v1/groups/{ctx['group_id']}/settle/me", json={}, headers=ctx["bob_h"]
    )
    assert res.status_code == 200
    assert res.json() == []


# ---------------------------------------------------------------------------
# initiate_settlement (POST /settle)
# ---------------------------------------------------------------------------
def test_periodic_settlement_api(client):
    ctx = _setup_group_with_expense(client, "pe")
    res = client.post(
        f"/api/v1/groups/{ctx['group_id']}/settle",
        json={"kind": "PERIODIC"},
        headers=ctx["owner_h"],
    )
    assert res.status_code == 200
    assert len(res.json()) == 1
    # status unchanged
    group = client.get(
        f"/api/v1/groups/{ctx['group_id']}", headers=ctx["owner_h"]
    ).json()
    assert group["status"] == "OPEN"


def test_final_settlement_requires_lock_api(client):
    ctx = _setup_group_with_expense(client, "fl")
    res = client.post(
        f"/api/v1/groups/{ctx['group_id']}/settle",
        json={"kind": "FINAL"},
        headers=ctx["owner_h"],
    )
    assert res.status_code == 409
    assert res.json()["detail"]["code"] == "GROUP_NOT_LOCKED"


def test_final_settlement_flow_api(client):
    ctx = _setup_group_with_expense(client, "ff")
    lock = client.post(
        f"/api/v1/groups/{ctx['group_id']}/lock", headers=ctx["owner_h"]
    )
    assert lock.status_code == 200

    res = client.post(
        f"/api/v1/groups/{ctx['group_id']}/settle",
        json={"kind": "FINAL"},
        headers=ctx["owner_h"],
    )
    assert res.status_code == 200
    payouts = res.json()
    assert len(payouts) == 1
    payout = payouts[0]
    assert payout["status"] == "PENDING"

    group = client.get(
        f"/api/v1/groups/{ctx['group_id']}", headers=ctx["owner_h"]
    ).json()
    assert group["status"] == "PARTIAL_SETTLEMENT"

    # bob pays, owner confirms -> group settles
    paid = client.post(
        f"/api/v1/groups/{ctx['group_id']}/payouts/{payout['id']}/paid",
        json={"evidence": "UPI-1"},
        headers=ctx["bob_h"],
    )
    assert paid.status_code == 200
    assert paid.json()["status"] == "PAID"

    settled = client.post(
        f"/api/v1/groups/{ctx['group_id']}/payouts/{payout['id']}/settled",
        json={"evidence": "received"},
        headers=ctx["owner_h"],
    )
    assert settled.status_code == 200
    assert settled.json()["status"] == "SETTLED"

    group = client.get(
        f"/api/v1/groups/{ctx['group_id']}", headers=ctx["owner_h"]
    ).json()
    assert group["status"] == "SETTLED"


def test_final_settlement_orphan_blocks_api(client):
    ctx = _setup_group_with_expense(client, "fo")
    client.post(f"/api/v1/groups/{ctx['group_id']}/lock", headers=ctx["owner_h"])
    # bob records an orphan ad-hoc payment (no expense_ids)
    adhoc = client.post(
        f"/api/v1/groups/{ctx['group_id']}/payments",
        json={"payor_id": str(ctx["bob_id"]), "payee_id": str(ctx["owner_id"]), "amt": 10.0},
        headers=ctx["bob_h"],
    )
    assert adhoc.status_code == 201

    res = client.post(
        f"/api/v1/groups/{ctx['group_id']}/settle",
        json={"kind": "FINAL"},
        headers=ctx["owner_h"],
    )
    assert res.status_code == 409
    assert res.json()["detail"]["code"] == "FINAL_SETTLEMENT"


# ---------------------------------------------------------------------------
# Payout lifecycle endpoints
# ---------------------------------------------------------------------------
def test_payout_lifecycle_endpoints_api(client):
    ctx = _setup_group_with_expense(client, "lc")
    p = client.post(
        f"/api/v1/groups/{ctx['group_id']}/settle/me", json={}, headers=ctx["bob_h"]
    ).json()[0]

    # wrong role: owner (payor of nothing) cannot mark paid
    wrong = client.post(
        f"/api/v1/groups/{ctx['group_id']}/payouts/{p['id']}/paid",
        json={},
        headers=ctx["owner_h"],
    )
    assert wrong.status_code == 403

    paid = client.post(
        f"/api/v1/groups/{ctx['group_id']}/payouts/{p['id']}/paid",
        json={"evidence": "ref-1"},
        headers=ctx["bob_h"],
    )
    assert paid.status_code == 200
    assert paid.json()["status"] == "PAID"

    # bob (payor) cannot confirm; owner (payee) can
    bad_settle = client.post(
        f"/api/v1/groups/{ctx['group_id']}/payouts/{p['id']}/settled",
        json={},
        headers=ctx["bob_h"],
    )
    assert bad_settle.status_code == 403

    settled = client.post(
        f"/api/v1/groups/{ctx['group_id']}/payouts/{p['id']}/settled",
        json={},
        headers=ctx["owner_h"],
    )
    assert settled.status_code == 200
    assert settled.json()["status"] == "SETTLED"


def test_cancel_payout_requires_reason_api(client):
    ctx = _setup_group_with_expense(client, "cr")
    p = client.post(
        f"/api/v1/groups/{ctx['group_id']}/settle/me", json={}, headers=ctx["bob_h"]
    ).json()[0]

    no_reason = client.post(
        f"/api/v1/groups/{ctx['group_id']}/payouts/{p['id']}/cancel",
        json={"reason": ""},
        headers=ctx["bob_h"],
    )
    assert no_reason.status_code == 422

    cancelled = client.post(
        f"/api/v1/groups/{ctx['group_id']}/payouts/{p['id']}/cancel",
        json={"reason": "wrong amount"},
        headers=ctx["bob_h"],
    )
    assert cancelled.status_code == 200
    assert cancelled.json()["status"] == "CANCELLED"
    assert cancelled.json()["status_reason"] == "wrong amount"


def test_fail_payout_from_paid_api(client):
    ctx = _setup_group_with_expense(client, "fa")
    p = client.post(
        f"/api/v1/groups/{ctx['group_id']}/settle/me", json={}, headers=ctx["bob_h"]
    ).json()[0]

    # cannot fail from PENDING
    early = client.post(
        f"/api/v1/groups/{ctx['group_id']}/payouts/{p['id']}/failed",
        json={"reason": "nope"},
        headers=ctx["bob_h"],
    )
    assert early.status_code == 409

    client.post(
        f"/api/v1/groups/{ctx['group_id']}/payouts/{p['id']}/paid",
        json={},
        headers=ctx["bob_h"],
    )
    failed = client.post(
        f"/api/v1/groups/{ctx['group_id']}/payouts/{p['id']}/failed",
        json={"reason": "transfer failed"},
        headers=ctx["owner_h"],
    )
    assert failed.status_code == 200
    assert failed.json()["status"] == "FAILED"


def test_reissued_payout_links_replaces_api(client):
    ctx = _setup_group_with_expense(client, "rl")
    p = client.post(
        f"/api/v1/groups/{ctx['group_id']}/settle/me", json={}, headers=ctx["bob_h"]
    ).json()[0]
    client.post(
        f"/api/v1/groups/{ctx['group_id']}/payouts/{p['id']}/paid", json={}, headers=ctx["bob_h"]
    )
    client.post(
        f"/api/v1/groups/{ctx['group_id']}/payouts/{p['id']}/failed",
        json={"reason": "bad ref"},
        headers=ctx["owner_h"],
    )

    reissued = client.post(
        f"/api/v1/groups/{ctx['group_id']}/settle/me", json={}, headers=ctx["bob_h"]
    ).json()
    assert len(reissued) == 1
    assert p["id"] in reissued[0]["replaces_payout_ids"]


def test_remind_and_escalate_endpoints_api(client):
    ctx = _setup_group_with_expense(client, "re")
    p = client.post(
        f"/api/v1/groups/{ctx['group_id']}/settle/me", json={}, headers=ctx["bob_h"]
    ).json()[0]

    # payee (owner) reminds payor on PENDING
    remind = client.post(
        f"/api/v1/groups/{ctx['group_id']}/payouts/{p['id']}/remind",
        headers=ctx["owner_h"],
    )
    assert remind.status_code == 200

    # payor cannot remind on PENDING
    bad = client.post(
        f"/api/v1/groups/{ctx['group_id']}/payouts/{p['id']}/remind",
        headers=ctx["bob_h"],
    )
    assert bad.status_code == 403

    client.post(
        f"/api/v1/groups/{ctx['group_id']}/payouts/{p['id']}/paid", json={}, headers=ctx["bob_h"]
    )
    # escalate too early -> 409
    early = client.post(
        f"/api/v1/groups/{ctx['group_id']}/payouts/{p['id']}/escalate",
        headers=ctx["owner_h"],
    )
    assert early.status_code == 409


# ---------------------------------------------------------------------------
# Ad-hoc payments + linking
# ---------------------------------------------------------------------------
def test_adhoc_payment_api(client):
    ctx = _setup_group_with_expense(client, "ah")
    res = client.post(
        f"/api/v1/groups/{ctx['group_id']}/payments",
        json={
            "payor_id": str(ctx["bob_id"]),
            "payee_id": str(ctx["owner_id"]),
            "amt": 25.0,
            "expense_ids": [ctx["trans_id"]],
            "evidence": "cash",
        },
        headers=ctx["bob_h"],
    )
    assert res.status_code == 201
    body = res.json()
    assert body["is_adhoc"] is True
    assert body["status"] == "PAID"
    assert body["expense_ids"] == [ctx["trans_id"]]


def test_adhoc_linking_api(client):
    ctx = _setup_group_with_expense(client, "al")
    orphan = client.post(
        f"/api/v1/groups/{ctx['group_id']}/payments",
        json={"payor_id": str(ctx["bob_id"]), "payee_id": str(ctx["owner_id"]), "amt": 50.0},
        headers=ctx["bob_h"],
    ).json()

    linked = client.post(
        f"/api/v1/groups/{ctx['group_id']}/payouts/{orphan['id']}/expenses",
        json={"expense_ids": [ctx["trans_id"]]},
        headers=ctx["bob_h"],
    )
    assert linked.status_code == 200
    assert linked.json()["expense_ids"] == [ctx["trans_id"]]


def test_adhoc_validation_api(client):
    ctx = _setup_group_with_expense(client, "av")
    # third-party actor rejected
    third_h = _auth_headers(client, "thirdav@x.com", "Third")
    res = client.post(
        f"/api/v1/groups/{ctx['group_id']}/payments",
        json={"payor_id": str(ctx["bob_id"]), "payee_id": str(ctx["owner_id"]), "amt": 5.0},
        headers=third_h,
    )
    assert res.status_code == 403  # not a member

    # missing fields -> 422
    bad = client.post(
        f"/api/v1/groups/{ctx['group_id']}/payments",
        json={"amt": 5.0},
        headers=ctx["bob_h"],
    )
    assert bad.status_code == 422


# ---------------------------------------------------------------------------
# Listing endpoints
# ---------------------------------------------------------------------------
def test_list_payouts_and_rounds_api(client):
    ctx = _setup_group_with_expense(client, "ls")
    client.post(
        f"/api/v1/groups/{ctx['group_id']}/settle/me", json={}, headers=ctx["bob_h"]
    )

    payouts = client.get(
        f"/api/v1/groups/{ctx['group_id']}/payouts", headers=ctx["owner_h"]
    ).json()
    assert len(payouts) == 1

    bob_only = client.get(
        f"/api/v1/groups/{ctx['group_id']}/payouts?user_id={ctx['bob_id']}",
        headers=ctx["owner_h"],
    ).json()
    assert len(bob_only) == 1

    rounds = client.get(
        f"/api/v1/groups/{ctx['group_id']}/settlement-rounds", headers=ctx["owner_h"]
    ).json()
    assert len(rounds) == 1


def test_list_my_payouts_api(client):
    ctx = _setup_group_with_expense(client, "mp")
    client.post(
        f"/api/v1/groups/{ctx['group_id']}/settle/me", json={}, headers=ctx["bob_h"]
    )

    mine = client.get("/api/v1/users/me/payouts", headers=ctx["bob_h"]).json()
    assert len(mine) == 1
    assert mine[0]["payor_id"] == str(ctx["bob_id"])

    owner_mine = client.get("/api/v1/users/me/payouts", headers=ctx["owner_h"]).json()
    assert len(owner_mine) == 1
    assert owner_mine[0]["payee_id"] == str(ctx["owner_id"])


def test_payout_not_found_api(client):
    ctx = _setup_group_with_expense(client, "nf")
    from uuid import uuid4

    res = client.post(
        f"/api/v1/groups/{ctx['group_id']}/payouts/{uuid4()}/paid",
        json={},
        headers=ctx["bob_h"],
    )
    assert res.status_code == 404
    assert res.json()["detail"]["code"] == "PAYOUT_NOT_FOUND"

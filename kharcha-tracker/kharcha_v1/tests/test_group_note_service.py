"""
kharcha_v1/tests/test_group_note_service.py
Unit + API tests for group notes (E5): CRUD, membership gate, audit rows.
"""
import json
from datetime import datetime, timezone
from uuid import uuid4

import pytest
from fastapi.testclient import TestClient

from kharcha_v1.kharcha_gateway.app.main import app, get_storage
from kharcha_v1.kharcha_gateway.kharcha_service import (
    auth_service,
    group_note_service,
    group_service,
)


def _user(storage, email, name):
    return auth_service.signup(storage, name, email, "password123")


def _group(storage, owner, name="Trip"):
    return group_service.create_group(storage, name, "INR", owner.id)


def _audit_rows(storage, entity_type="group_note"):
    return [
        r
        for r in storage.list_records(schema="audit_log")
        if getattr(r, "entity_type", None) == entity_type
        and not getattr(r, "is_deleted", False)
    ]


# ---------------------------------------------------------------------------
# Service
# ---------------------------------------------------------------------------
def test_create_note_stores_fields_and_system_timestamp(storage):
    owner = _user(storage, "note-owner@x.com", "Owner")
    group = _group(storage, owner)
    author_date = datetime(2026, 7, 4, 9, 30, tzinfo=timezone.utc)

    note = group_note_service.create_group_note(
        storage,
        group.id,
        "  Buy milk  ",
        owner.id,
        tags=[" trip ", "food", "trip", ""],
        date=author_date,
    )
    assert note.group_id == group.id
    assert note.message == "Buy milk"
    assert note.tags == ["trip", "food"]
    assert note.date == author_date
    assert note.created_by == owner.id
    assert note.created_at is not None
    assert note.modified_by is None
    assert note.modified_at is None


def test_create_note_defaults_date_to_now(storage):
    owner = _user(storage, "note-owner2@x.com", "Owner")
    group = _group(storage, owner)
    note = group_note_service.create_group_note(
        storage, group.id, "No explicit date", owner.id
    )
    assert note.date is not None
    assert abs((note.date - note.created_at).total_seconds()) < 5


def test_create_note_requires_membership(storage):
    owner = _user(storage, "note-owner3@x.com", "Owner")
    outsider = _user(storage, "note-outsider3@x.com", "Outsider")
    group = _group(storage, owner)

    with pytest.raises(PermissionError):
        group_note_service.create_group_note(
            storage, group.id, "nope", outsider.id
        )


def test_create_note_unknown_group(storage):
    owner = _user(storage, "note-owner4@x.com", "Owner")

    with pytest.raises(LookupError):
        group_note_service.create_group_note(
            storage, uuid4(), "nope", owner.id
        )


def test_create_note_rejects_blank_message_and_bad_tags(storage):
    owner = _user(storage, "note-owner5@x.com", "Owner")
    group = _group(storage, owner)

    with pytest.raises(ValueError):
        group_note_service.create_group_note(storage, group.id, "   ", owner.id)
    with pytest.raises(ValueError):
        group_note_service.create_group_note(
            storage, group.id, "long", owner.id, tags=["x" * 51]
        )
    with pytest.raises(ValueError):
        group_note_service.create_group_note(
            storage, group.id, "many", owner.id, tags=[f"t{i}" for i in range(21)]
        )
    with pytest.raises(ValueError):
        group_note_service.create_group_note(
            storage, group.id, "bad", owner.id, tags=[123]
        )


def test_list_notes_newest_first_and_group_scoped(storage):
    owner = _user(storage, "note-owner6@x.com", "Owner")
    group_a = _group(storage, owner, "Group A")
    group_b = _group(storage, owner, "Group B")
    d_old = datetime(2026, 1, 1, tzinfo=timezone.utc)
    d_new = datetime(2026, 6, 1, tzinfo=timezone.utc)

    old = group_note_service.create_group_note(
        storage, group_a.id, "old", owner.id, date=d_old
    )
    group_note_service.create_group_note(
        storage, group_a.id, "new", owner.id, date=d_new
    )
    group_note_service.create_group_note(
        storage, group_b.id, "other group", owner.id, date=d_new
    )

    notes = group_note_service.list_group_notes(storage, group_a.id, owner.id)
    assert [n.message for n in notes] == ["new", "old"]
    assert all(n.group_id == group_a.id for n in notes)


def test_list_notes_requires_membership(storage):
    owner = _user(storage, "note-owner7@x.com", "Owner")
    outsider = _user(storage, "note-outsider7@x.com", "Outsider")
    group = _group(storage, owner)

    with pytest.raises(PermissionError):
        group_note_service.list_group_notes(storage, group.id, outsider.id)
    with pytest.raises(LookupError):
        group_note_service.list_group_notes(storage, uuid4(), owner.id)


def test_update_note_partial(storage):
    owner = _user(storage, "note-owner8@x.com", "Owner")
    group = _group(storage, owner)
    note = group_note_service.create_group_note(
        storage, group.id, "original", owner.id, tags=["a", "b"]
    )

    updated = group_note_service.update_group_note(
        storage, group.id, note.id, owner.id, message="changed"
    )
    assert updated.message == "changed"
    assert updated.tags == ["a", "b"]
    assert updated.date == note.date
    assert updated.modified_by == owner.id
    assert updated.modified_at is not None

    cleared = group_note_service.update_group_note(
        storage, group.id, note.id, owner.id, tags=[]
    )
    assert cleared.tags == []
    assert cleared.message == "changed"


def test_update_note_rejects_blank_message_and_bad_refs(storage):
    owner = _user(storage, "note-owner9@x.com", "Owner")
    group_a = _group(storage, owner, "Group A")
    group_b = _group(storage, owner, "Group B")
    note = group_note_service.create_group_note(
        storage, group_a.id, "original", owner.id
    )

    with pytest.raises(ValueError):
        group_note_service.update_group_note(
            storage, group_a.id, note.id, owner.id, message="  "
        )
    with pytest.raises(LookupError):
        group_note_service.update_group_note(
            storage, group_a.id, uuid4(), owner.id, message="x"
        )
    # A note that belongs to a different group is not findable here.
    with pytest.raises(LookupError):
        group_note_service.update_group_note(
            storage, group_b.id, note.id, owner.id, message="x"
        )


def test_update_note_requires_membership(storage):
    owner = _user(storage, "note-owner10@x.com", "Owner")
    outsider = _user(storage, "note-outsider10@x.com", "Outsider")
    group = _group(storage, owner)
    note = group_note_service.create_group_note(
        storage, group.id, "original", owner.id
    )

    with pytest.raises(PermissionError):
        group_note_service.update_group_note(
            storage, group.id, note.id, outsider.id, message="x"
        )


def test_delete_note_soft_and_audited(storage):
    owner = _user(storage, "note-owner11@x.com", "Owner")
    group = _group(storage, owner)
    note = group_note_service.create_group_note(
        storage, group.id, "doomed", owner.id
    )

    assert group_note_service.delete_group_note(
        storage, group.id, note.id, owner.id
    ) is True
    assert [n.id for n in group_note_service.list_group_notes(
        storage, group.id, owner.id
    )] == []

    # Soft-deleted rows are hidden from get_record, so inspect the raw file.
    raw = json.loads(
        (storage.db_path / "group_note_records.json").read_text()
    )
    rows = raw if isinstance(raw, list) else raw.get("records", raw)
    row = next(r for r in rows if str(r.get("id")) == str(note.id))
    assert row["is_deleted"] is True
    assert str(row["modified_by"]) == str(owner.id)

    # Deleting again is a 404.
    with pytest.raises(LookupError):
        group_note_service.delete_group_note(
            storage, group.id, note.id, owner.id
        )


def test_delete_note_requires_membership(storage):
    owner = _user(storage, "note-owner12@x.com", "Owner")
    outsider = _user(storage, "note-outsider12@x.com", "Outsider")
    group = _group(storage, owner)
    note = group_note_service.create_group_note(
        storage, group.id, "original", owner.id
    )

    with pytest.raises(PermissionError):
        group_note_service.delete_group_note(
            storage, group.id, note.id, outsider.id
        )


def test_note_lifecycle_writes_audit_rows(storage):
    owner = _user(storage, "note-owner13@x.com", "Owner")
    group = _group(storage, owner)
    note = group_note_service.create_group_note(
        storage, group.id, "audited", owner.id, tags=["a"]
    )
    group_note_service.update_group_note(
        storage, group.id, note.id, owner.id, message="audited 2"
    )
    group_note_service.delete_group_note(storage, group.id, note.id, owner.id)

    rows = _audit_rows(storage)
    assert [r.event for r in rows] == ["CREATE", "UPDATE", "DELETE"]
    for row in rows:
        assert row.entity_id == note.id
        assert row.group_id == group.id
        assert row.actor_user_id == owner.id
        assert row.actor_name == "Owner"


# ---------------------------------------------------------------------------
# API
# ---------------------------------------------------------------------------
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


def _make_group(client, headers, name="Trip"):
    res = client.post(
        "/api/v1/groups", json={"name": name, "currency": "INR"}, headers=headers
    )
    assert res.status_code in (200, 201)
    return res.json()["id"]


def test_api_note_crud_flow(client):
    owner_h = _auth_headers(client, "api-note@x.com", "Owner")
    group_id = _make_group(client, owner_h)

    # Create
    created = client.post(
        f"/api/v1/groups/{group_id}/notes",
        json={
            "message": "Landing 18:40, check in early",
            "tags": ["trip", "flight"],
            "date": "2026-07-04",
        },
        headers=owner_h,
    )
    assert created.status_code == 201
    note = created.json()
    assert note["message"] == "Landing 18:40, check in early"
    assert note["tags"] == ["trip", "flight"]
    assert note["date"].startswith("2026-07-04")
    note_id = note["id"]

    # List
    listed = client.get(
        f"/api/v1/groups/{group_id}/notes", headers=owner_h
    )
    assert listed.status_code == 200
    assert [n["id"] for n in listed.json()] == [note_id]

    # Update
    patched = client.patch(
        f"/api/v1/groups/{group_id}/notes/{note_id}",
        json={"message": "Landing 20:10", "tags": ["trip"]},
        headers=owner_h,
    )
    assert patched.status_code == 200
    body = patched.json()
    assert body["message"] == "Landing 20:10"
    assert body["tags"] == ["trip"]
    assert body["date"].startswith("2026-07-04")  # unchanged

    # Delete
    deleted = client.delete(
        f"/api/v1/groups/{group_id}/notes/{note_id}", headers=owner_h
    )
    assert deleted.status_code == 200
    assert client.get(
        f"/api/v1/groups/{group_id}/notes", headers=owner_h
    ).json() == []


def test_api_note_error_mapping(client):
    owner_h = _auth_headers(client, "api-note2@x.com", "Owner")
    outsider_h = _auth_headers(client, "api-note2-o@x.com", "Outsider")
    group_id = _make_group(client, owner_h)

    # Unknown group -> 404
    assert client.post(
        f"/api/v1/groups/{uuid4()}/notes",
        json={"message": "x"},
        headers=owner_h,
    ).status_code == 404
    # Non-member -> 403
    assert client.post(
        f"/api/v1/groups/{group_id}/notes",
        json={"message": "x"},
        headers=outsider_h,
    ).status_code == 403
    assert client.get(
        f"/api/v1/groups/{group_id}/notes", headers=outsider_h
    ).status_code == 403
    # Blank message -> 422
    assert client.post(
        f"/api/v1/groups/{group_id}/notes",
        json={"message": "   "},
        headers=owner_h,
    ).status_code == 422
    # Unknown note -> 404
    assert client.patch(
        f"/api/v1/groups/{group_id}/notes/{uuid4()}",
        json={"message": "x"},
        headers=owner_h,
    ).status_code == 404
    assert client.delete(
        f"/api/v1/groups/{group_id}/notes/{uuid4()}", headers=owner_h
    ).status_code == 404


def test_api_note_audit_rows_persisted(client, storage):
    owner_h = _auth_headers(client, "api-note3@x.com", "Owner")
    group_id = _make_group(client, owner_h)
    note_id = client.post(
        f"/api/v1/groups/{group_id}/notes",
        json={"message": "via api"},
        headers=owner_h,
    ).json()["id"]
    client.patch(
        f"/api/v1/groups/{group_id}/notes/{note_id}",
        json={"message": "via api 2"},
        headers=owner_h,
    )

    rows = _audit_rows(storage)
    assert [r.event for r in rows] == ["CREATE", "UPDATE"]
    assert all(str(r.entity_id) == note_id for r in rows)
    assert all(str(r.group_id) == group_id for r in rows)

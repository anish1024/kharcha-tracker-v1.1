"""
kharcha_v1/tests/test_group_service.py
Unit tests for group_service.py backed by FlatFileStorage.
"""
import pytest

from kharcha_v1.kharcha_gateway.kharcha_service import auth_service, group_service
from kharcha_v1.kharcha_gateway.kharcha_service.constants.group_status import (
    GroupStatus,
)


def test_create_group(storage):
    creator = auth_service.signup(storage, "Owner", "owner@example.com", "password123")
    group = group_service.create_group(
        storage, name="Trip to Goa", currency="INR", created_by=creator.id
    )

    assert group.name == "Trip to Goa"
    assert group.currency == "INR"
    assert group.status == GroupStatus.OPEN
    assert group.created_by_user_id == creator.id
    assert creator.id in group.members
    assert group.invite_code != ""
    assert (storage.db_path / "group_records.json").exists()


def test_membership_and_listing(storage):
    u1 = auth_service.signup(storage, "User1", "u1@example.com", "password123")
    u2 = auth_service.signup(storage, "User2", "u2@example.com", "password123")

    g1 = group_service.create_group(storage, "Group 1", "INR", u1.id)
    assert group_service.is_member(storage, g1.id, u1.id) is True
    assert group_service.is_member(storage, g1.id, u2.id) is False

    u1_groups = group_service.list_groups_for_user(storage, u1.id)
    assert len(u1_groups) == 1
    assert u1_groups[0].id == g1.id


def test_join_by_invite_code(storage):
    u1 = auth_service.signup(storage, "User1", "u1@example.com", "password123")
    u2 = auth_service.signup(storage, "User2", "u2@example.com", "password123")

    g = group_service.create_group(storage, "Dinner", "INR", u1.id)
    joined_group = group_service.join_group_by_invite(
        storage, g.id, g.invite_code, u2.id
    )

    assert u2.id in joined_group.members
    assert group_service.is_member(storage, g.id, u2.id) is True


def test_add_member_by_email(storage):
    u1 = auth_service.signup(storage, "User1", "u1@example.com", "password123")
    u2 = auth_service.signup(storage, "User2", "u2@example.com", "password123")

    g = group_service.create_group(storage, "Lunch", "INR", u1.id)
    updated = group_service.add_member(storage, g.id, "u2@example.com", u1.id)

    assert u2.id in updated.members


def test_lock_and_unlock_group(storage):
    u1 = auth_service.signup(storage, "User1", "u1@example.com", "password123")
    g = group_service.create_group(storage, "Rent", "INR", u1.id)

    locked = group_service.lock_group(storage, g.id, u1.id)
    assert locked.status == GroupStatus.LOCKED

    with pytest.raises(ValueError):
        group_service.lock_group(storage, g.id, u1.id)

    unlocked = group_service.unlock_group(storage, g.id, u1.id)
    assert unlocked.status == GroupStatus.OPEN


def test_delete_group_permissions(storage):
    u1 = auth_service.signup(storage, "User1", "u1@example.com", "password123")
    u2 = auth_service.signup(storage, "User2", "u2@example.com", "password123")
    g = group_service.create_group(storage, "Cabin", "USD", u1.id)

    with pytest.raises(PermissionError):
        group_service.delete_group(storage, g.id, u2.id)

    assert group_service.delete_group(storage, g.id, u1.id) is True
    with pytest.raises(LookupError):
        group_service.get_group(storage, g.id)


def test_group_activity_logs(storage):
    u1 = auth_service.signup(storage, "User1", "u1@example.com", "password123")
    g = group_service.create_group(storage, "Audit Test", "INR", u1.id)
    group_service.lock_group(storage, g.id, u1.id)

    activity = group_service.get_group_activity(storage, g.id)
    assert len(activity) >= 2
    assert (storage.db_path / "audit_logs.json").exists()
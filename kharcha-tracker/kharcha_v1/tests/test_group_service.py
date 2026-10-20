"""
kharcha_v1/tests/test_group_service.py
Unit tests for group_service.py backed by FlatFileStorage.
"""
from datetime import datetime, timedelta, timezone
from uuid import uuid4

import pytest

from kharcha_v1.kharcha_gateway.kharcha_service import auth_service, group_service
from kharcha_v1.kharcha_gateway.kharcha_service.constants.group_status import (
    GroupStatus,
)
from kharcha_v1.kharcha_gateway.kharcha_service.storage_service.schema.audit_log import (
    AuditLog,
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

def _make_audit(storage, group_id, actor_id, when, new_value):
    audit = AuditLog(
        id=uuid4(),
        event="UPDATE",
        entity_type="group",
        entity_id=group_id,
        action="UPDATE",
        new_value=new_value,
        actor_user_id=actor_id,
        created_by=actor_id,
        created_at=when,
        modified_at=when,
    )
    storage.save_record(audit, schema="audit_log")
    return audit


def test_group_activity_date_range(storage):
    u1 = auth_service.signup(storage, "User1", "u1@example.com", "password123")
    g = group_service.create_group(storage, "Range", "INR", u1.id)

    now = datetime.now(timezone.utc)
    _make_audit(storage, g.id, u1.id, now - timedelta(days=3), "day1")
    _make_audit(storage, g.id, u1.id, now - timedelta(days=2), "day2")
    _make_audit(storage, g.id, u1.id, now - timedelta(days=1), "day3")

    newest = now - timedelta(days=1)
    oldest = now - timedelta(days=3)

    from_only = group_service.get_group_activity(
        storage, g.id, limit=100, date_from=now - timedelta(days=2)
    )
    from_values = {l.new_value for l in from_only}
    assert {"day2", "day3"} <= from_values
    assert "day1" not in from_values

    to_only = group_service.get_group_activity(
        storage, g.id, limit=100, date_to=now - timedelta(days=2)
    )
    to_values = {l.new_value for l in to_only}
    assert {"day1", "day2"} <= to_values
    assert "day3" not in to_values

    both = group_service.get_group_activity(
        storage, g.id, limit=100, date_from=oldest, date_to=newest + timedelta(days=1)
    )
    both_values = {l.new_value for l in both}
    assert {"day1", "day2", "day3"} <= both_values

    # Inclusive boundaries: exactly the row timestamp is kept.
    exact = group_service.get_group_activity(
        storage, g.id, limit=100, date_from=now - timedelta(days=3), date_to=now - timedelta(days=3)
    )
    assert [l.new_value for l in exact] == ["day1"]

    assert group_service.count_group_activity(storage, g.id) == len(
        group_service.get_group_activity(storage, g.id, limit=1000)
    )
    assert group_service.count_group_activity(
        storage, g.id, date_from=now - timedelta(days=2)
    ) == len(from_only)

    # Newest-first ordering preserved under filtering (CREATE row is newest).
    assert [
        l.new_value for l in from_only if str(l.new_value).startswith("day")
    ][:2] == ["day3", "day2"]


def test_group_activity_date_range_empty_and_invalid(storage):
    u1 = auth_service.signup(storage, "User1", "u1@example.com", "password123")
    g = group_service.create_group(storage, "Empty range", "INR", u1.id)
    now = datetime.now(timezone.utc)

    assert group_service.get_group_activity(
        storage, g.id, date_from=now + timedelta(days=10)
    ) == []
    assert (
        group_service.count_group_activity(
            storage, g.id, date_to=now - timedelta(days=10)
        )
        == 0
    )

    with pytest.raises(ValueError):
        group_service.get_group_activity(
            storage, g.id, date_from=now, date_to=now - timedelta(days=1)
        )
    with pytest.raises(ValueError):
        group_service.count_group_activity(
            storage, g.id, date_from=now, date_to=now - timedelta(days=1)
        )


def test_group_activity_pagination_with_count(storage):
    u1 = auth_service.signup(storage, "User1", "u1@example.com", "password123")
    g = group_service.create_group(storage, "Pages", "INR", u1.id)
    now = datetime.now(timezone.utc)
    for i in range(5):
        _make_audit(storage, g.id, u1.id, now - timedelta(hours=i + 1), f"row{i}")

    total = group_service.count_group_activity(storage, g.id)
    assert total == 6  # 5 synthetic + group CREATE

    page1 = group_service.get_group_activity(storage, g.id, limit=4, offset=0)
    page2 = group_service.get_group_activity(storage, g.id, limit=4, offset=4)
    assert len(page1) == 4
    assert len(page2) == 2
    assert {l.id for l in page1} | {l.id for l in page2} == {
        l.id for l in group_service.get_group_activity(storage, g.id, limit=1000)
    }
    # Ordering strictly descending across the page boundary.
    fallback = datetime.min.replace(tzinfo=timezone.utc)
    stamps = [(l.created_at or fallback) for l in page1 + page2]
    assert stamps == sorted(stamps, reverse=True)


def test_record_audit_populates_actor_name(storage):
    u1 = auth_service.signup(storage, "ActName", "actname@example.com", "password123")
    g = group_service.create_group(storage, "Named", "INR", u1.id)
    group_service.lock_group(storage, g.id, u1.id)

    activity = group_service.get_group_activity(storage, g.id, limit=100)
    assert len(activity) >= 2
    assert all(l.actor_name == "ActName" for l in activity)


def test_list_groups_sort_activity(storage):
    u1 = auth_service.signup(storage, "User1", "u1@example.com", "password123")
    group_service.create_group(storage, "Alpha", "INR", u1.id)
    g2 = group_service.create_group(storage, "Beta", "INR", u1.id)
    group_service.create_group(storage, "Gamma", "INR", u1.id)

    # Default keeps storage (creation) order regardless of audit recency.
    default = group_service.list_groups_for_user(storage, u1.id)
    assert [g.name for g in default] == ["Alpha", "Beta", "Gamma"]

    # Beta's lock is the newest audit anywhere -> Beta jumps to the front.
    group_service.lock_group(storage, g2.id, u1.id)
    by_activity = group_service.list_groups_for_user(storage, u1.id, sort="activity")
    assert [g.name for g in by_activity] == ["Beta", "Gamma", "Alpha"]

    # Default order is unaffected by audit activity.
    default_again = group_service.list_groups_for_user(storage, u1.id)
    assert [g.name for g in default_again] == ["Alpha", "Beta", "Gamma"]


def test_list_groups_sort_activity_unaudited_and_invalid(storage):
    from datetime import datetime as _dt, timedelta as _td, timezone as _tz
    from uuid import uuid4 as _uuid4

    from kharcha_v1.kharcha_gateway.kharcha_service.storage_service.schema.group_record import (
        GroupRecord as _GroupRecord,
    )

    u1 = auth_service.signup(storage, "User1", "u1@example.com", "password123")
    group_service.create_group(storage, "Alpha", "INR", u1.id)

    now = _dt.now(_tz.utc)
    older = _GroupRecord(
        id=_uuid4(),
        name="Older",
        members=[u1.id],
        status="OPEN",
        currency="INR",
        created_by=u1.id,
        created_at=now - _td(days=1),
        modified_at=now - _td(days=1),
    )
    newer = _GroupRecord(
        id=_uuid4(),
        name="Newer",
        members=[u1.id],
        status="OPEN",
        currency="INR",
        created_by=u1.id,
        created_at=now,
        modified_at=now,
    )
    storage.save_record(older, schema="group_record")
    storage.save_record(newer, schema="group_record")

    by_activity = group_service.list_groups_for_user(storage, u1.id, sort="activity")
    # Audited group first; unaudited groups follow, newest-created first.
    assert [g.name for g in by_activity] == ["Alpha", "Newer", "Older"]

    default = group_service.list_groups_for_user(storage, u1.id)
    assert [g.name for g in default] == ["Alpha", "Older", "Newer"]

    with pytest.raises(ValueError):
        group_service.list_groups_for_user(storage, u1.id, sort="bogus")


def test_group_audit_rows_carry_group_id(storage):
    u1 = auth_service.signup(storage, "User1", "u1@example.com", "password123")
    g = group_service.create_group(storage, "Tagged", "INR", u1.id)
    group_service.lock_group(storage, g.id, u1.id)

    activity = group_service.get_group_activity(storage, g.id, limit=100)
    assert len(activity) >= 2
    assert all(l.group_id == g.id for l in activity)


def test_group_activity_union_dedupes_dual_key_rows(storage):
    from kharcha_v1.kharcha_gateway.kharcha_service.storage_service.schema.audit_log import (
        AuditLog as _AuditLog,
    )

    u1 = auth_service.signup(storage, "User1", "u1@example.com", "password123")
    g = group_service.create_group(storage, "Union", "INR", u1.id)

    now = datetime.now(timezone.utc)
    dual = _AuditLog(
        id=uuid4(),
        event="UPDATE",
        entity_type="group",
        entity_id=g.id,
        group_id=g.id,
        action="UPDATE",
        new_value="dual-key",
        actor_user_id=u1.id,
        created_by=u1.id,
        created_at=now - timedelta(minutes=2),
        modified_at=now - timedelta(minutes=2),
    )
    group_only = _AuditLog(
        id=uuid4(),
        event="UPDATE",
        entity_type="expense",
        group_id=g.id,
        action="UPDATE",
        new_value="group-only",
        actor_user_id=u1.id,
        created_by=u1.id,
        created_at=now - timedelta(minutes=1),
        modified_at=now - timedelta(minutes=1),
    )
    storage.save_record(dual, schema="audit_log")
    storage.save_record(group_only, schema="audit_log")

    activity = group_service.get_group_activity(storage, g.id, limit=100)
    ids = [str(l.id) for l in activity]
    assert len(ids) == len(set(ids)), "dual-key row must not appear twice"
    values = [str(l.new_value) for l in activity]
    assert values.count("dual-key") == 1
    assert "group-only" in values
    assert group_service.count_group_activity(storage, g.id) == len(activity)

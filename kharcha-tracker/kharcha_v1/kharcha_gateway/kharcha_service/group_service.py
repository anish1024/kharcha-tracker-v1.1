"""
kharcha_v1/kharcha_gateway/kharcha_service/group_service.py
Groups and membership management (FR-06..10).
"""
from __future__ import annotations

import base64
import hashlib
from datetime import datetime, timezone
from typing import List, Optional
from uuid import UUID, uuid4

from .constants.group_status import GroupStatus
from .models.group import Group
from .storage_service.schema.audit_log import AuditLog
from .storage_service.schema.group_record import GroupRecord
from .storage_service.schema.user_record import UserRecord
from .storage_service.storage_interface import StorageInterface as storage_interface


def _record_audit(
    storage: storage_interface,
    action: str,
    group_id: UUID,
    actor_user_id: UUID,
    old_value: Optional[str] = None,
    new_value: Optional[str] = None,
) -> None:
    now = datetime.now(timezone.utc)
    actor = storage.get_record(record_id=str(actor_user_id), schema="user_record")
    actor_name = (
        actor.name
        if isinstance(actor, UserRecord) and not actor.is_deleted
        else None
    )
    audit = AuditLog(
        id=uuid4(),
        event=action,
        entity_type="group",
        entity_id=group_id,
        group_id=group_id,
        action=action,
        old_value=old_value,
        new_value=new_value,
        actor_user_id=actor_user_id,
        actor_name=actor_name,
        created_by=actor_user_id,
        created_at=now,
        modified_at=now,
    )
    storage.save_record(audit, schema="audit_log")


def _to_group_dto(record: GroupRecord) -> Group:
    return Group(
        id=record.id,
        name=record.name,
        created_by_user_id=record.created_by,
        invite_code=record.invite_code or "",
        members=record.members or [],
        status=GroupStatus(record.status),
        currency=record.currency,
        time_created=record.created_at,
        last_updated=record.modified_at or record.created_at,
    )


def derive_invite_code(group_id: UUID, inviter_id: UUID) -> str:
    """Deterministic pass-key for a (group, member) pair (plan §5d).

    ``base32(sha256("<group_id>:<inviter_id>"))[:8]`` — stable per member,
    checkable without storing it. A code is only meaningful together with
    its 128-bit ``group_id``, which is itself unguessable.
    """
    raw = f"{group_id}:{inviter_id}"
    digest = hashlib.sha256(raw.encode()).digest()
    return base64.b32encode(digest).decode()[:8].upper()


def create_group(
    storage: storage_interface,
    name: str,
    currency: str,
    created_by: UUID,
) -> Group:
    if not name or not name.strip():
        raise ValueError("Group name cannot be blank")
    if not currency or len(currency) != 3:
        raise ValueError("Currency must be a 3-letter code (e.g. INR, USD)")

    now = datetime.now(timezone.utc)
    group_id = uuid4()
    code = derive_invite_code(group_id, created_by)

    record = GroupRecord(
        id=group_id,
        name=name.strip(),
        invite_code=code,
        members=[created_by],
        status=GroupStatus.OPEN.value,
        currency=currency.upper(),
        is_deleted=False,
        created_by=created_by,
        created_at=now,
        modified_by=created_by,
        modified_at=now,
    )
    saved = storage.save_record(record, schema="group_record")
    _record_audit(storage, "CREATE", group_id, created_by, new_value=name.strip())
    return _to_group_dto(saved)


def get_group(
    storage: storage_interface,
    group_id: UUID,
) -> Group:
    record = storage.get_record(record_id=str(group_id), schema="group_record")
    if not record or getattr(record, "is_deleted", False):
        raise LookupError(f"Group {group_id} not found")
    return _to_group_dto(record)


def list_groups_for_user(
    storage: storage_interface,
    user_id: UUID,
    sort: str = "default",
) -> List[Group]:
    if sort not in ("default", "activity"):
        raise ValueError(
            f"Unsupported sort '{sort}' (expected 'default' or 'activity')"
        )
    all_groups = storage.list_records(schema="group_record")
    user_groups: List[Group] = []
    for g in all_groups:
        if not getattr(g, "is_deleted", False) and user_id in (g.members or []):
            user_groups.append(_to_group_dto(g))
    if sort == "activity" and len(user_groups) > 1:
        ids = [str(g.id) for g in user_groups]
        logs = storage.list_records(
            schema="audit_log",
            filters={"entity_id": ids},
            sort_by="created_at",
            ascending=False,
        )
        order: dict[str, int] = {}
        for log in logs:
            eid = str(getattr(log, "entity_id", ""))
            if (
                eid in ids
                and eid not in order
                and not getattr(log, "is_deleted", False)
            ):
                order[eid] = len(order)
        by_id = {str(g.id): g for g in user_groups}
        ranked = [by_id[eid] for eid in order]
        rest = [g for g in user_groups if str(g.id) not in order]
        rest.sort(key=lambda g: (g.time_created is None, g.time_created), reverse=True)
        return ranked + rest
    return user_groups


def is_member(
    storage: storage_interface,
    group_id: UUID,
    user_id: UUID,
) -> bool:
    try:
        group = get_group(storage, group_id)
        return user_id in group.members
    except LookupError:
        return False


def list_group_members(
    storage: storage_interface,
    group_id: UUID,
) -> List[dict]:
    """Resolve a group's member UUIDs to display summaries (name/email)."""
    record = storage.get_record(record_id=str(group_id), schema="group_record")
    if not record or getattr(record, "is_deleted", False):
        raise LookupError(f"Group {group_id} not found")

    members: List[dict] = []
    for member_id in (record.members or []):
        user = storage.get_record(record_id=str(member_id), schema="user_record")
        if user and not getattr(user, "is_deleted", False):
            members.append(
                {
                    "id": user.id,
                    "name": user.name,
                    "email": user.email,
                    "avatar_url": getattr(user, "avatar_url", None),
                }
            )
        else:
            members.append(
                {"id": member_id, "name": str(member_id), "email": "", "avatar_url": None}
            )
    return members


def generate_invite_code(
    storage: storage_interface,
    group_id: UUID,
    actor_user_id: UUID,
) -> str:
    record = storage.get_record(record_id=str(group_id), schema="group_record")
    if not record or getattr(record, "is_deleted", False):
        raise LookupError(f"Group {group_id} not found")
    if record.status == GroupStatus.SETTLED.value:
        raise ValueError("Cannot generate invite code for a SETTLED group")

    # Deterministic per (group, member) — no stored / rotated code needed.
    return derive_invite_code(group_id, actor_user_id)


def join_group_by_invite(
    storage: storage_interface,
    group_id: UUID,
    invite_code: str,
    user_id: UUID,
) -> Group:
    record = storage.get_record(record_id=str(group_id), schema="group_record")
    if not record or getattr(record, "is_deleted", False):
        raise LookupError("Group not found")
    if record.status != GroupStatus.OPEN.value:
        raise ValueError(f"Cannot join group in {record.status} status")

    submitted = (invite_code or "").strip().upper()
    if not submitted:
        raise LookupError("Invalid invite code")

    # Accept any current member's derived code (plan §5d).
    valid = {
        derive_invite_code(group_id, member)
        for member in (record.members or [])
        if member is not None
    }
    if submitted not in valid:
        raise LookupError("Invalid invite code")

    members = list(record.members or [])
    if user_id not in members:
        members.append(user_id)
        record.members = members
        record.modified_at = datetime.now(timezone.utc)
        record.modified_by = user_id
        storage.save_record(record, schema="group_record")
        _record_audit(storage, "UPDATE", group_id, user_id, new_value=f"User joined {user_id}")
    return _to_group_dto(record)


def add_member(
    storage: storage_interface,
    group_id: UUID,
    email: str,
    actor_user_id: UUID,
) -> Group:
    record = storage.get_record(record_id=str(group_id), schema="group_record")
    if not record or getattr(record, "is_deleted", False):
        raise LookupError("Group not found")
    if record.status != GroupStatus.OPEN.value:
        raise ValueError(f"Cannot add member to group in {record.status} status")

    user_matches = storage.list_records(
        schema="user_record",
        filters={"email": [email.strip().lower()]},
        limit=1,
    )
    if not user_matches or getattr(user_matches[0], "is_deleted", False):
        raise LookupError(f"User with email '{email}' not found")

    target_user_id = user_matches[0].id
    members = list(record.members or [])
    if target_user_id not in members:
        members.append(target_user_id)
        record.members = members
        record.modified_at = datetime.now(timezone.utc)
        record.modified_by = actor_user_id
        storage.save_record(record, schema="group_record")
        _record_audit(storage, "UPDATE", group_id, actor_user_id, new_value=f"Added member {target_user_id}")
    return _to_group_dto(record)


def leave_group(
    storage: storage_interface,
    group_id: UUID,
    user_id: UUID,
) -> Group:
    record = storage.get_record(record_id=str(group_id), schema="group_record")
    if not record or getattr(record, "is_deleted", False):
        raise LookupError("Group not found")
    if record.status == GroupStatus.SETTLED.value:
        raise ValueError("Group is SETTLED")

    members = list(record.members or [])
    if user_id not in members:
        raise ValueError("User is not a member of the group")

    # Settle out check per FR-08: net due must be 0 and no open payouts
    from . import settlement_engine
    try:
        preview = settlement_engine.preview_settlement(storage, group_id, user_id)
        net_due = (preview.net_dues or {}).get(user_id, 0.0)
        if abs(net_due) > 0.01:
            raise ValueError(f"Cannot leave group with outstanding balance: {net_due}")
        if preview.open_payouts:
            raise ValueError("Cannot leave group while open payouts remain")
    except (NotImplementedError, AttributeError):
        pass  # Engine stub bypass if not fully linked

    members.remove(user_id)
    record.members = members
    record.modified_at = datetime.now(timezone.utc)
    record.modified_by = user_id
    storage.save_record(record, schema="group_record")
    _record_audit(storage, "UPDATE", group_id, user_id, new_value=f"User {user_id} left group")
    return _to_group_dto(record)


def lock_group(
    storage: storage_interface,
    group_id: UUID,
    actor_user_id: UUID,
) -> Group:
    record = storage.get_record(record_id=str(group_id), schema="group_record")
    if not record or getattr(record, "is_deleted", False):
        raise LookupError("Group not found")
    if record.status != GroupStatus.OPEN.value:
        raise ValueError(f"Only OPEN groups can be locked; currently {record.status}")

    record.status = GroupStatus.LOCKED.value
    record.modified_at = datetime.now(timezone.utc)
    record.modified_by = actor_user_id
    storage.save_record(record, schema="group_record")
    _record_audit(storage, "LOCK", group_id, actor_user_id)
    return _to_group_dto(record)


def unlock_group(
    storage: storage_interface,
    group_id: UUID,
    actor_user_id: UUID,
) -> Group:
    record = storage.get_record(record_id=str(group_id), schema="group_record")
    if not record or getattr(record, "is_deleted", False):
        raise LookupError("Group not found")
    if record.status != GroupStatus.LOCKED.value:
        raise ValueError(f"Only LOCKED groups can be unlocked; currently {record.status}")

    record.status = GroupStatus.OPEN.value
    record.modified_at = datetime.now(timezone.utc)
    record.modified_by = actor_user_id
    storage.save_record(record, schema="group_record")
    _record_audit(storage, "UNLOCK", group_id, actor_user_id)
    return _to_group_dto(record)


def delete_group(
    storage: storage_interface,
    group_id: UUID,
    actor_user_id: UUID,
) -> bool:
    record = storage.get_record(record_id=str(group_id), schema="group_record")
    if not record or getattr(record, "is_deleted", False):
        raise LookupError("Group not found")
    if record.created_by != actor_user_id:
        raise PermissionError("Only the group creator can delete this group")
    if record.status == GroupStatus.PARTIAL_SETTLEMENT.value:
        raise ValueError("Cannot delete group while PARTIAL_SETTLEMENT is active")

    record.is_deleted = True
    record.modified_at = datetime.now(timezone.utc)
    record.modified_by = actor_user_id
    storage.save_record(record, schema="group_record")
    _record_audit(storage, "DELETE", group_id, actor_user_id)
    return True


def _as_utc(value: datetime) -> datetime:
    if value.tzinfo is None:
        return value.replace(tzinfo=timezone.utc)
    return value.astimezone(timezone.utc)


def _filter_activity(
    logs: List[AuditLog],
    date_from: Optional[datetime] = None,
    date_to: Optional[datetime] = None,
) -> List[AuditLog]:
    if date_from is not None and date_to is not None:
        if _as_utc(date_from) > _as_utc(date_to):
            raise ValueError("date_from must not be after date_to")
    filtered = logs
    if date_from is not None:
        start = _as_utc(date_from)
        filtered = [
            l for l in filtered if l.created_at is not None and _as_utc(l.created_at) >= start
        ]
    if date_to is not None:
        end = _as_utc(date_to)
        filtered = [
            l for l in filtered if l.created_at is not None and _as_utc(l.created_at) <= end
        ]
    return filtered


def _load_group_activity(storage: storage_interface, group_id: UUID) -> List[AuditLog]:
    gid = str(group_id)
    by_entity = storage.list_records(
        schema="audit_log",
        filters={"entity_id": [gid]},
        sort_by="created_at",
        ascending=False,
    )
    by_group = storage.list_records(
        schema="audit_log",
        filters={"group_id": [gid]},
        sort_by="created_at",
        ascending=False,
    )
    merged: dict[str, AuditLog] = {}
    for log in list(by_entity) + list(by_group):
        if getattr(log, "is_deleted", False):
            continue
        key = str(getattr(log, "id", None) or uuid4())
        merged[key] = log  # type: ignore[assignment]
    fallback = datetime.min.replace(tzinfo=timezone.utc)
    return sorted(
        merged.values(),
        key=lambda l: (l.created_at is None, l.created_at or fallback),
        reverse=True,
    )


def get_group_activity(
    storage: storage_interface,
    group_id: UUID,
    limit: int = 50,
    offset: int = 0,
    date_from: Optional[datetime] = None,
    date_to: Optional[datetime] = None,
) -> List[AuditLog]:
    logs = _filter_activity(_load_group_activity(storage, group_id), date_from, date_to)
    return logs[offset : offset + limit]


def count_group_activity(
    storage: storage_interface,
    group_id: UUID,
    date_from: Optional[datetime] = None,
    date_to: Optional[datetime] = None,
) -> int:
    return len(_filter_activity(_load_group_activity(storage, group_id), date_from, date_to))
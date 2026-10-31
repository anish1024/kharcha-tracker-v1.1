"""
kharcha_v1/kharcha_gateway/kharcha_service/group_note_service.py
Group notes management (E5): per-group list of {date, message, tags}
entries. Members-only, audited (entity_type "group_note").
"""
from __future__ import annotations

from datetime import datetime, timezone
from typing import List, Optional
from uuid import UUID, uuid4

from .group_service import get_group
from .models.group_note import GroupNote
from .storage_service.schema.audit_log import AuditLog
from .storage_service.schema.group_note import GroupNoteRecord
from .storage_service.schema.user_record import UserRecord
from .storage_service.storage_interface import StorageInterface as storage_interface

MAX_NOTE_MESSAGE_LEN = 500
MAX_NOTE_TAG_LEN = 50
MAX_NOTE_TAGS = 20


def _record_audit(
    storage: storage_interface,
    action: str,
    group_id: UUID,
    note_id: UUID,
    actor_user_id: UUID,
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
        entity_type="group_note",
        entity_id=note_id,
        group_id=group_id,
        action=action,
        actor_user_id=actor_user_id,
        actor_name=actor_name,
        created_by=actor_user_id,
        created_at=now,
        modified_at=now,
    )
    storage.save_record(audit, schema="audit_log")


def _to_dto(record: GroupNoteRecord) -> GroupNote:
    return GroupNote(
        id=record.id,
        group_id=record.group_id,
        message=record.message,
        tags=list(record.tags or []),
        date=record.date,
        created_by=record.created_by,
        created_at=record.created_at,
        modified_by=record.modified_by,
        modified_at=record.modified_at,
    )


def _require_group_member(
    storage: storage_interface,
    group_id: UUID,
    actor_user_id: UUID,
) -> None:
    """LookupError if the group is missing; PermissionError if not a member."""
    group = get_group(storage, group_id)
    if actor_user_id not in (group.members or []):
        raise PermissionError(
            f"User {actor_user_id} is not a member of group {group_id}"
        )


def _require_note(
    storage: storage_interface,
    group_id: UUID,
    note_id: UUID,
) -> GroupNoteRecord:
    record = storage.get_record(record_id=str(note_id), schema="group_note_record")
    if (
        not record
        or getattr(record, "is_deleted", False)
        or record.group_id != group_id
    ):
        raise LookupError(f"Group note {note_id} not found")
    return record


def _clean_tags(tags: Optional[List[str]]) -> List[str]:
    """Strip, drop empties, dedupe (order-preserving); enforce per-tag caps."""
    cleaned: List[str] = []
    for tag in tags or []:
        if not isinstance(tag, str):
            raise ValueError("Note tags must be strings")
        text = tag.strip()
        if not text:
            continue
        if len(text) > MAX_NOTE_TAG_LEN:
            raise ValueError(f"Tag '{text[:20]}' exceeds {MAX_NOTE_TAG_LEN} characters")
        if text not in cleaned:
            cleaned.append(text)
    if len(cleaned) > MAX_NOTE_TAGS:
        raise ValueError(f"At most {MAX_NOTE_TAGS} tags per note")
    return cleaned


def _clean_message(message: str) -> str:
    text = (message or "").strip()
    if not text:
        raise ValueError("Note message cannot be blank")
    if len(text) > MAX_NOTE_MESSAGE_LEN:
        raise ValueError(f"Note message exceeds {MAX_NOTE_MESSAGE_LEN} characters")
    return text


def create_group_note(
    storage: storage_interface,
    group_id: UUID,
    message: str,
    actor_user_id: UUID,
    tags: Optional[List[str]] = None,
    date: Optional[datetime] = None,
) -> GroupNote:
    """Create a note on the group.

    LookupError -> 404, PermissionError -> 403, ValueError -> 422 (mapped by
    main). ``date`` is the author-facing date the note is about; when omitted
    it defaults to now (UTC). ``created_at`` is always the system timestamp.
    """
    _require_group_member(storage, group_id, actor_user_id)
    text = _clean_message(message)
    cleaned = _clean_tags(tags)
    now = datetime.now(timezone.utc)
    record = GroupNoteRecord(
        id=uuid4(),
        group_id=group_id,
        date=date or now,
        message=text,
        tags=cleaned,
        created_by=actor_user_id,
        created_at=now,
    )
    storage.save_record(record, schema="group_note_record")
    _record_audit(storage, "CREATE", group_id, record.id, actor_user_id)
    return _to_dto(record)


def list_group_notes(
    storage: storage_interface,
    group_id: UUID,
    actor_user_id: UUID,
) -> List[GroupNote]:
    """List the group's notes, newest author date first."""
    _require_group_member(storage, group_id, actor_user_id)
    notes: List[GroupNote] = []
    for record in storage.list_records(schema="group_note_record"):
        if (
            getattr(record, "group_id", None) == group_id
            and not getattr(record, "is_deleted", False)
        ):
            notes.append(_to_dto(record))
    notes.sort(
        key=lambda n: (n.date, n.created_at or n.date),
        reverse=True,
    )
    return notes


def update_group_note(
    storage: storage_interface,
    group_id: UUID,
    note_id: UUID,
    actor_user_id: UUID,
    message: Optional[str] = None,
    tags: Optional[List[str]] = None,
    date: Optional[datetime] = None,
) -> GroupNote:
    """Partially update a note: only fields that are not None are changed.

    Passing ``tags=[]`` clears all tags; ``tags=None`` leaves them alone.
    """
    _require_group_member(storage, group_id, actor_user_id)
    record = _require_note(storage, group_id, note_id)
    if message is not None:
        record.message = _clean_message(message)
    if tags is not None:
        record.tags = _clean_tags(tags)
    if date is not None:
        record.date = date
    now = datetime.now(timezone.utc)
    record.modified_by = actor_user_id
    record.modified_at = now
    storage.save_record(record, schema="group_note_record")
    _record_audit(storage, "UPDATE", group_id, note_id, actor_user_id)
    return _to_dto(record)


def delete_group_note(
    storage: storage_interface,
    group_id: UUID,
    note_id: UUID,
    actor_user_id: UUID,
) -> bool:
    """Soft-delete a note (is_deleted=True); audited as DELETE."""
    _require_group_member(storage, group_id, actor_user_id)
    record = _require_note(storage, group_id, note_id)
    now = datetime.now(timezone.utc)
    record.is_deleted = True
    record.modified_by = actor_user_id
    record.modified_at = now
    storage.save_record(record, schema="group_note_record")
    _record_audit(storage, "DELETE", group_id, note_id, actor_user_id)
    return True

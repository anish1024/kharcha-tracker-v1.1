"""
kharcha_v1/kharcha_gateway/kharcha_service/notification_service.py
In-app notifications (creation, listing, mark-as-read).
"""
from __future__ import annotations

from datetime import datetime, timezone
from typing import List, Optional
from uuid import UUID, uuid4

from .models.notification import Notification
from .storage_service.schema.notification_record import NotificationRecord
from .storage_service.storage_interface import StorageInterface as storage_interface


def _to_notification_dto(record: NotificationRecord) -> Notification:
    return Notification(
        id=record.id,
        user_id=record.user_id,
        group_id=getattr(record, "group_id", None),
        payout_id=getattr(record, "payout_id", None),
        kind=record.kind,
        message=record.message,
        is_read=bool(getattr(record, "is_read", False)),
        created_at=record.created_at,
    )


def create_notification(
    storage: storage_interface,
    user_id: UUID,
    kind: str,
    message: str,
    group_id: Optional[UUID] = None,
    payout_id: Optional[UUID] = None,
) -> Notification:
    """Persist a notification for ``user_id`` and return its DTO."""
    now = datetime.now(timezone.utc)
    kind_value = getattr(kind, "value", kind)
    record = NotificationRecord(
        id=uuid4(),
        user_id=user_id,
        group_id=group_id,
        payout_id=payout_id,
        kind=kind_value,
        message=message,
        is_read=False,
        created_by=user_id,
        created_at=now,
        modified_by=user_id,
        modified_at=now,
    )
    saved = storage.save_record(record, schema="notification_record")
    return _to_notification_dto(saved)


def list_notifications(
    storage: storage_interface,
    user_id: UUID,
    unread_only: bool = False,
) -> List[Notification]:
    """Return a user's notifications, newest first."""
    records = storage.list_records(
        schema="notification_record",
        filters={"user_id": [str(user_id)]},
        sort_by="created_at",
        ascending=False,
    )
    result: List[Notification] = []
    for record in records:
        if getattr(record, "is_deleted", False):
            continue
        if unread_only and getattr(record, "is_read", False):
            continue
        result.append(_to_notification_dto(record))
    return result


def mark_all_read(storage: storage_interface, user_id: UUID) -> int:
    """Mark every unread notification for a user as read; return the count."""
    records = storage.list_records(
        schema="notification_record",
        filters={"user_id": [str(user_id)]},
    )
    now = datetime.now(timezone.utc)
    marked = 0
    for record in records:
        if getattr(record, "is_deleted", False) or getattr(record, "is_read", False):
            continue
        record.is_read = True
        record.modified_at = now
        record.modified_by = user_id
        storage.save_record(record, schema="notification_record")
        marked += 1
    return marked

"""
kharcha_v1/kharcha_gateway/kharcha_service/invitation_service.py
Pending group invitations with owner notifications (FR: invite by email,
view / accept / decline, owner always notified).
"""
from __future__ import annotations

from datetime import datetime, timedelta, timezone
from typing import List
from uuid import UUID, uuid4

from . import group_service, notification_service
from .constants import INVITATION_TTL_DAYS
from .constants.invitation_status import InvitationStatus
from .constants.notification_kind import NotificationKind
from .models.invitation import Invitation
from .storage_service.schema.invitation_record import InvitationRecord
from .storage_service.storage_interface import StorageInterface as storage_interface


def _to_invitation_dto(record: InvitationRecord) -> Invitation:
    return Invitation(
        id=record.id,
        group_id=record.group_id,
        inviter_id=record.inviter_id,
        invitee_email=record.invitee_email,
        invite_code=record.invite_code,
        status=record.status,
        created_at=record.created_at,
        expires_at=getattr(record, "expires_at", None),
    )


def _is_expired(record: InvitationRecord, now: datetime) -> bool:
    expires_at = getattr(record, "expires_at", None)
    return expires_at is not None and now >= expires_at


def create_invitation(
    storage: storage_interface,
    group_id: UUID,
    inviter_id: UUID,
    email: str,
    ttl_days: int = INVITATION_TTL_DAYS,
) -> Invitation:
    """Create (or reuse) a pending invitation for ``email`` to ``group_id``."""
    normalized = (email or "").strip().lower()
    if not normalized or "@" not in normalized:
        raise ValueError("Invalid email format")

    group = storage.get_record(record_id=str(group_id), schema="group_record")
    if not group or getattr(group, "is_deleted", False):
        raise LookupError(f"Group {group_id} not found")
    if inviter_id not in (group.members or []):
        raise PermissionError("Only group members can invite others")

    now = datetime.now(timezone.utc)

    # Idempotent: reuse an existing live pending invitation for the same email.
    for record in storage.list_records(
        schema="invitation_record", filters={"group_id": [str(group_id)]}
    ):
        if (
            not getattr(record, "is_deleted", False)
            and record.invitee_email == normalized
            and record.status == InvitationStatus.PENDING.value
            and not _is_expired(record, now)
        ):
            return _to_invitation_dto(record)

    invitation = InvitationRecord(
        id=uuid4(),
        group_id=group_id,
        inviter_id=inviter_id,
        invitee_email=normalized,
        invite_code=group.invite_code or "",
        status=InvitationStatus.PENDING.value,
        expires_at=now + timedelta(days=ttl_days),
        created_by=inviter_id,
        created_at=now,
        modified_by=inviter_id,
        modified_at=now,
    )
    saved = storage.save_record(invitation, schema="invitation_record")
    return _to_invitation_dto(saved)


def list_invitations_for_email(
    storage: storage_interface,
    email: str,
    include_resolved: bool = False,
) -> List[Invitation]:
    """Return invitations addressed to ``email`` (pending only by default)."""
    normalized = (email or "").strip().lower()
    now = datetime.now(timezone.utc)
    result: List[Invitation] = []
    for record in storage.list_records(
        schema="invitation_record", filters={"invitee_email": [normalized]}
    ):
        if getattr(record, "is_deleted", False):
            continue
        if _is_expired(record, now) and record.status == InvitationStatus.PENDING.value:
            continue
        if not include_resolved and record.status != InvitationStatus.PENDING.value:
            continue
        result.append(_to_invitation_dto(record))
    return result


def list_invitations_for_group(
    storage: storage_interface,
    group_id: UUID,
) -> List[Invitation]:
    """Return every invitation (any status) for a group."""
    result: List[Invitation] = []
    for record in storage.list_records(
        schema="invitation_record", filters={"group_id": [str(group_id)]}
    ):
        if getattr(record, "is_deleted", False):
            continue
        result.append(_to_invitation_dto(record))
    return result


def _resolve(
    storage: storage_interface,
    invitation_id: UUID,
    user_id: UUID,
    user_email: str,
    new_status: InvitationStatus,
    kind: NotificationKind,
    verb: str,
) -> Invitation:
    now = datetime.now(timezone.utc)
    record = storage.get_record(record_id=str(invitation_id), schema="invitation_record")
    if not record or getattr(record, "is_deleted", False):
        raise LookupError("Invitation not found")
    if record.status != InvitationStatus.PENDING.value:
        raise ValueError(f"Invitation is already {record.status}")
    if _is_expired(record, now):
        record.status = InvitationStatus.EXPIRED.value
        record.modified_at = now
        storage.save_record(record, schema="invitation_record")
        raise ValueError("Invitation has expired")
    if (user_email or "").strip().lower() != record.invitee_email:
        raise PermissionError("This invitation is for a different email address")

    if new_status is InvitationStatus.ACCEPTED:
        group_service.add_member(
            storage, record.group_id, record.invitee_email, actor_user_id=user_id
        )

    record.status = new_status.value
    record.modified_at = now
    record.modified_by = user_id
    storage.save_record(record, schema="invitation_record")

    # Always notify the group owner (the group's created_by).
    # Required message format:
    #   {user name} ({email}) {accepted/rejected} the invite code for "{group name}"
    group = storage.get_record(record_id=str(record.group_id), schema="group_record")
    if group and group.created_by:
        actor = storage.get_record(record_id=str(user_id), schema="user_record")
        actor_name = (getattr(actor, "name", None) or "").strip() or record.invitee_email
        notification_service.create_notification(
            storage,
            user_id=group.created_by,
            kind=kind,
            message=(
                f'{actor_name} ({record.invitee_email}) {verb} '
                f'the invite code for "{group.name}"'
            ),
            group_id=record.group_id,
        )
    return _to_invitation_dto(record)


def accept_invitation(
    storage: storage_interface,
    invitation_id: UUID,
    user_id: UUID,
    user_email: str,
) -> Invitation:
    return _resolve(
        storage,
        invitation_id,
        user_id,
        user_email,
        InvitationStatus.ACCEPTED,
        NotificationKind.GROUP_INVITE_ACCEPTED,
        "accepted",
    )


def decline_invitation(
    storage: storage_interface,
    invitation_id: UUID,
    user_id: UUID,
    user_email: str,
) -> Invitation:
    return _resolve(
        storage,
        invitation_id,
        user_id,
        user_email,
        InvitationStatus.REJECTED,
        NotificationKind.GROUP_INVITE_DECLINED,
        "rejected",
    )

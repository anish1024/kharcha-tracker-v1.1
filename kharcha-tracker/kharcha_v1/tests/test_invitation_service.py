"""
kharcha_v1/tests/test_invitation_service.py
Unit tests for pending group invitations and owner notifications.
"""
from uuid import uuid4

import pytest

from kharcha_v1.kharcha_gateway.kharcha_service import (
    auth_service,
    group_service,
    invitation_service,
    notification_service,
)
from kharcha_v1.kharcha_gateway.kharcha_service.constants.invitation_status import (
    InvitationStatus,
)
from kharcha_v1.kharcha_gateway.kharcha_service.constants.notification_kind import (
    NotificationKind,
)


def _user(storage, email, name):
    return auth_service.signup(storage, name, email, "password123")


def _group(storage, owner):
    return group_service.create_group(storage, "Trip", "INR", owner.id)


def test_create_invitation_creates_pending(storage):
    owner = _user(storage, "owner1@x.com", "Owner")
    guest = _user(storage, "guest1@x.com", "Guest")
    group = _group(storage, owner)

    inv = invitation_service.create_invitation(
        storage, group.id, owner.id, "Guest1@X.com"
    )
    assert inv.status == InvitationStatus.PENDING.value
    assert inv.invitee_email == "guest1@x.com"
    assert inv.inviter_id == owner.id
    assert inv.expires_at is not None
    assert inv.invite_code == group.invite_code
    assert inv.group_name == "Trip"


def test_create_invitation_is_idempotent(storage):
    owner = _user(storage, "owner2@x.com", "Owner")
    group = _group(storage, owner)

    first = invitation_service.create_invitation(storage, group.id, owner.id, "dup@x.com")
    second = invitation_service.create_invitation(storage, group.id, owner.id, "dup@x.com")
    assert first.id == second.id


def test_create_invitation_requires_membership(storage):
    owner = _user(storage, "owner3@x.com", "Owner")
    outsider = _user(storage, "outsider3@x.com", "Outsider")
    group = _group(storage, owner)

    with pytest.raises(PermissionError):
        invitation_service.create_invitation(storage, group.id, outsider.id, "x@x.com")


def test_create_invitation_validates_input(storage):
    owner = _user(storage, "owner4@x.com", "Owner")
    group = _group(storage, owner)

    with pytest.raises(ValueError):
        invitation_service.create_invitation(storage, group.id, owner.id, "notanemail")
    with pytest.raises(LookupError):
        invitation_service.create_invitation(storage, uuid4(), owner.id, "a@b.com")


def test_accept_invitation_joins_group_and_notifies_owner(storage):
    owner = _user(storage, "owner5@x.com", "Owner")
    guest = _user(storage, "guest5@x.com", "Guest")
    group = _group(storage, owner)
    inv = invitation_service.create_invitation(storage, group.id, owner.id, guest.email)

    accepted = invitation_service.accept_invitation(storage, inv.id, guest.id, guest.email)
    assert accepted.status == InvitationStatus.ACCEPTED.value
    assert accepted.group_name == "Trip"

    assert guest.id in group_service.get_group(storage, group.id).members

    notes = notification_service.list_notifications(storage, owner.id)
    assert len(notes) == 1
    assert notes[0].kind == NotificationKind.GROUP_INVITE_ACCEPTED.value
    assert notes[0].user_id == owner.id
    assert notes[0].message == f'Guest ({guest.email}) accepted the invite code for "Trip"'


def test_decline_invitation_notifies_owner_without_joining(storage):
    owner = _user(storage, "owner6@x.com", "Owner")
    guest = _user(storage, "guest6@x.com", "Guest")
    group = _group(storage, owner)
    inv = invitation_service.create_invitation(storage, group.id, owner.id, guest.email)

    declined = invitation_service.decline_invitation(storage, inv.id, guest.id, guest.email)
    assert declined.status == InvitationStatus.REJECTED.value
    assert declined.group_name == "Trip"

    assert guest.id not in group_service.get_group(storage, group.id).members

    notes = notification_service.list_notifications(storage, owner.id)
    assert len(notes) == 1
    assert notes[0].kind == NotificationKind.GROUP_INVITE_DECLINED.value
    assert notes[0].message == f'Guest ({guest.email}) rejected the invite code for "Trip"'


def test_accept_requires_matching_email(storage):
    owner = _user(storage, "owner7@x.com", "Owner")
    guest = _user(storage, "guest7@x.com", "Guest")
    intruder = _user(storage, "intruder7@x.com", "Intruder")
    group = _group(storage, owner)
    inv = invitation_service.create_invitation(storage, group.id, owner.id, guest.email)

    with pytest.raises(PermissionError):
        invitation_service.accept_invitation(storage, inv.id, intruder.id, intruder.email)


def test_accept_twice_rejected(storage):
    owner = _user(storage, "owner8@x.com", "Owner")
    guest = _user(storage, "guest8@x.com", "Guest")
    group = _group(storage, owner)
    inv = invitation_service.create_invitation(storage, group.id, owner.id, guest.email)

    invitation_service.accept_invitation(storage, inv.id, guest.id, guest.email)
    with pytest.raises(ValueError):
        invitation_service.accept_invitation(storage, inv.id, guest.id, guest.email)


def test_expired_invitation_rejected(storage):
    owner = _user(storage, "owner9@x.com", "Owner")
    guest = _user(storage, "guest9@x.com", "Guest")
    group = _group(storage, owner)
    inv = invitation_service.create_invitation(
        storage, group.id, owner.id, guest.email, ttl_days=-1
    )

    with pytest.raises(ValueError):
        invitation_service.accept_invitation(storage, inv.id, guest.id, guest.email)


def test_list_invitations_hides_resolved(storage):
    owner = _user(storage, "owner10@x.com", "Owner")
    guest = _user(storage, "guest10@x.com", "Guest")
    group = _group(storage, owner)
    invitation_service.create_invitation(storage, group.id, owner.id, guest.email)

    pending = invitation_service.list_invitations_for_email(storage, guest.email)
    assert len(pending) == 1
    assert pending[0].group_name == "Trip"

    invitation_service.decline_invitation(storage, pending[0].id, guest.id, guest.email)
    assert invitation_service.list_invitations_for_email(storage, guest.email) == []
    resolved = invitation_service.list_invitations_for_email(
        storage, guest.email, include_resolved=True
    )
    assert len(resolved) == 1


def test_group_name_resolved_and_falls_back_to_none(storage):
    owner = _user(storage, "owner11@x.com", "Owner")
    guest = _user(storage, "guest11@x.com", "Guest")
    group = _group(storage, owner)
    invitation_service.create_invitation(storage, group.id, owner.id, guest.email)

    for_list = invitation_service.list_invitations_for_group(storage, group.id)
    assert len(for_list) == 1
    assert for_list[0].group_name == "Trip"

    group_service.delete_group(storage, group.id, actor_user_id=owner.id)
    after = invitation_service.list_invitations_for_email(storage, guest.email)
    assert len(after) == 1
    assert after[0].group_name is None


def test_notification_lifecycle(storage):
    owner = _user(storage, "ownerN@x.com", "Owner")
    note = notification_service.create_notification(
        storage, owner.id, NotificationKind.GROUP_SETTLED, "group settled"
    )
    assert note.user_id == owner.id
    assert note.is_read is False

    unread = notification_service.list_notifications(storage, owner.id, unread_only=True)
    assert len(unread) == 1

    assert notification_service.mark_all_read(storage, owner.id) == 1
    assert notification_service.list_notifications(storage, owner.id, unread_only=True) == []
    assert len(notification_service.list_notifications(storage, owner.id)) == 1

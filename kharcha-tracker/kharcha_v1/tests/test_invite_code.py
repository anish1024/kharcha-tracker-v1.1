"""
kharcha_v1/tests/test_invite_code.py
Unit tests for the deterministic invite-code algorithm (plan §5d).
"""
import base64
import hashlib
from uuid import uuid4

import pytest

from kharcha_v1.kharcha_gateway.kharcha_service import auth_service, group_service

BASE32_ALPHABET = set("ABCDEFGHIJKLMNOPQRSTUVWXYZ234567")


def _user(storage, email, name):
    return auth_service.signup(storage, name, email, "password123")


def test_derive_invite_code_is_deterministic_and_shaped():
    gid, uid = uuid4(), uuid4()
    code = group_service.derive_invite_code(gid, uid)

    assert code == group_service.derive_invite_code(gid, uid)
    assert len(code) == 8
    assert code == code.upper()
    assert set(code) <= BASE32_ALPHABET

    # Matches the spec: base32(sha256("<group>:<inviter>"))[:8].upper()
    digest = hashlib.sha256(f"{gid}:{uid}".encode()).digest()
    expected = base64.b32encode(digest).decode()[:8].upper()
    assert code == expected


def test_derive_invite_code_varies_by_group_and_member():
    g1, g2 = uuid4(), uuid4()
    u1, u2 = uuid4(), uuid4()

    assert group_service.derive_invite_code(g1, u1) != group_service.derive_invite_code(g1, u2)
    assert group_service.derive_invite_code(g1, u1) != group_service.derive_invite_code(g2, u1)


def test_create_group_uses_derived_code(storage):
    owner = _user(storage, "owner@ic.com", "Owner")
    group = group_service.create_group(storage, "Trip", "INR", owner.id)

    assert group.invite_code == group_service.derive_invite_code(group.id, owner.id)


def test_generate_invite_code_returns_actor_derivation(storage):
    owner = _user(storage, "owner2@ic.com", "Owner")
    member = _user(storage, "member2@ic.com", "Member")
    group = group_service.create_group(storage, "Trip", "INR", owner.id)
    group_service.join_group_by_invite(storage, group.id, group.invite_code, member.id)

    code = group_service.generate_invite_code(storage, group.id, member.id)
    assert code == group_service.derive_invite_code(group.id, member.id)
    assert code != group.invite_code


def test_join_with_any_current_members_code(storage):
    owner = _user(storage, "owner3@ic.com", "Owner")
    member = _user(storage, "member3@ic.com", "Member")
    joiner = _user(storage, "joiner3@ic.com", "Joiner")
    group = group_service.create_group(storage, "Trip", "INR", owner.id)

    # member joins with the owner's code...
    group_service.join_group_by_invite(storage, group.id, group.invite_code, member.id)
    # ...and joiner joins with the *member's* derived code (not the stored one).
    member_code = group_service.derive_invite_code(group.id, member.id)
    updated = group_service.join_group_by_invite(storage, group.id, member_code, joiner.id)

    assert joiner.id in updated.members
    assert member.id in updated.members


def test_join_normalises_case_and_whitespace(storage):
    owner = _user(storage, "owner4@ic.com", "Owner")
    joiner = _user(storage, "joiner4@ic.com", "Joiner")
    group = group_service.create_group(storage, "Trip", "INR", owner.id)

    messy = "  " + group.invite_code.lower() + "  "
    updated = group_service.join_group_by_invite(storage, group.id, messy, joiner.id)
    assert joiner.id in updated.members


def test_join_rejects_wrong_and_foreign_codes(storage):
    owner = _user(storage, "owner5@ic.com", "Owner")
    joiner = _user(storage, "joiner5@ic.com", "Joiner")
    group = group_service.create_group(storage, "Trip", "INR", owner.id)

    with pytest.raises(LookupError):
        group_service.join_group_by_invite(storage, group.id, "ZZZZZZZZ", joiner.id)
    with pytest.raises(LookupError):
        group_service.join_group_by_invite(storage, group.id, "", joiner.id)

    # A code derived for a different group must not open this one.
    foreign = group_service.derive_invite_code(uuid4(), owner.id)
    with pytest.raises(LookupError):
        group_service.join_group_by_invite(storage, group.id, foreign, joiner.id)

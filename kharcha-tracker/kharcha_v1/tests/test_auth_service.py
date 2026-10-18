"""
kharcha_v1/tests/test_auth_service.py
Unit tests for auth_service.py backed by FlatFileStorage.
"""
from datetime import datetime, timezone
from uuid import UUID

import pytest

from kharcha_v1.kharcha_gateway.kharcha_service import auth_service
from kharcha_v1.kharcha_gateway.kharcha_service.models.base import Base
from kharcha_v1.kharcha_gateway.kharcha_service.models.user import User


def test_signup_success(storage):
    user = auth_service.signup(
        storage=storage,
        name="Alice Doe",
        email="alice@example.com",
        password="secretpassword",
        avatar_url="https://example.com/avatar.png",
    )
    assert isinstance(user, User)
    assert user.name == "Alice Doe"
    assert user.email == "alice@example.com"
    assert user.password == ""
    assert isinstance(user.id, UUID)

    # Verify persisted to flat file table
    loaded = storage.get_record(str(user.id), "user_record")
    assert loaded is not None
    assert loaded.email == "alice@example.com"


def test_signup_invalid_inputs(storage):
    with pytest.raises(ValueError, match="Invalid email format"):
        auth_service.signup(storage, "Bad", "not-an-email", "password123")

    with pytest.raises(ValueError, match="Password must be at least 6 characters"):
        auth_service.signup(storage, "Short", "valid@example.com", "123")


def test_signup_duplicate_email(storage):
    auth_service.signup(storage, "Alice", "alice@example.com", "password123")
    with pytest.raises(ValueError, match="already registered"):
        auth_service.signup(storage, "Alice Duplicate", "ALICE@example.com", "password123")


def test_login_success(storage):
    auth_service.signup(storage, "Bob", "bob@example.com", "password123")
    auth_base = auth_service.login(storage, "bob@example.com", "password123")

    assert isinstance(auth_base, Base)
    assert auth_base.auth_token is not None
    assert len(auth_base.auth_token.split(".")) == 3
    assert auth_base.auth_token_expiry > datetime.now(timezone.utc)


def test_login_invalid_credentials(storage):
    auth_service.signup(storage, "Charlie", "charlie@example.com", "password123")

    with pytest.raises(PermissionError, match="Invalid credentials"):
        auth_service.login(storage, "charlie@example.com", "wrongpassword")

    with pytest.raises(PermissionError, match="Invalid credentials"):
        auth_service.login(storage, "unknown@example.com", "password123")


def test_get_current_user_valid_token(storage):
    auth_service.signup(storage, "Dave", "dave@example.com", "password123")
    auth_base = auth_service.login(storage, "dave@example.com", "password123")

    current_user = auth_service.get_current_user(storage, auth_base.auth_token)
    assert current_user is not None
    assert current_user.email == "dave@example.com"
    assert current_user.name == "Dave"


def test_get_current_user_invalid_or_expired_token(storage):
    assert auth_service.get_current_user(storage, "invalid.token") is None

    auth_service.signup(storage, "Eve", "eve@example.com", "password123")
    auth_base = auth_service.login(storage, "eve@example.com", "password123")
    tampered = auth_base.auth_token[:-4] + "fake"
    assert auth_service.get_current_user(storage, tampered) is None


def test_password_reset_flow(storage):
    auth_service.signup(storage, "Frank", "frank@example.com", "oldpassword")

    auth_service.request_password_reset(storage, "frank@example.com")
    assert len(auth_service._ACTIVE_RESETS) == 1
    token = list(auth_service._ACTIVE_RESETS.keys())[0]

    auth_service.confirm_password_reset(storage, token, "newpassword123")

    with pytest.raises(PermissionError):
        auth_service.login(storage, "frank@example.com", "oldpassword")

    login_result = auth_service.login(storage, "frank@example.com", "newpassword123")
    assert login_result.auth_token is not None


def test_update_profile(storage):
    user = auth_service.signup(storage, "Grace", "grace@example.com", "password123")
    updated = auth_service.update_profile(
        storage=storage,
        user_id=user.id,
        name="Grace Hopper",
        avatar_url="https://example.com/new.png",
    )
    assert updated.name == "Grace Hopper"
    assert updated.avatar_url == "https://example.com/new.png"

    reloaded = storage.get_record(str(user.id), "user_record")
    assert reloaded.name == "Grace Hopper"


# ---------------------------------------------------------------------------
# Phase 3 — refresh tokens + rotation
# ---------------------------------------------------------------------------
def test_login_returns_refresh_token(storage):
    auth_service.signup(storage, "Heidi", "heidi@example.com", "password123")
    auth_base = auth_service.login(storage, "heidi@example.com", "password123")

    assert auth_base.refresh_token is not None
    assert len(auth_base.refresh_token.split(".")) == 3
    assert auth_base.refresh_token_expiry > datetime.now(timezone.utc)
    # Access token is short-lived; refresh token outlives it.
    assert auth_base.refresh_token_expiry > auth_base.auth_token_expiry

    payload = auth_service._decode_jwt(auth_base.auth_token)
    assert payload is not None and payload["typ"] == "access"
    refresh_payload = auth_service._decode_jwt(auth_base.refresh_token)
    assert refresh_payload is not None and refresh_payload["typ"] == "refresh"


def test_refresh_session_rotates_and_retires_old_token(storage):
    auth_service.signup(storage, "Ivan", "ivan@example.com", "password123")
    auth_base = auth_service.login(storage, "ivan@example.com", "password123")
    old_refresh = auth_base.refresh_token

    renewed = auth_service.refresh_session(storage, old_refresh)
    assert renewed.auth_token is not None
    assert renewed.refresh_token is not None
    assert renewed.refresh_token != old_refresh
    # New access token is usable as a bearer token.
    assert auth_service.get_current_user(storage, renewed.auth_token) is not None

    # The previously-issued refresh token is single-use and now rejected.
    with pytest.raises(PermissionError):
        auth_service.refresh_session(storage, old_refresh)

    # The rotated refresh token still works.
    again = auth_service.refresh_session(storage, renewed.refresh_token)
    assert again.auth_token is not None


def test_refresh_session_rejects_bad_tokens(storage):
    auth_service.signup(storage, "Judy", "judy@example.com", "password123")
    auth_base = auth_service.login(storage, "judy@example.com", "password123")

    with pytest.raises(PermissionError):
        auth_service.refresh_session(storage, "")
    with pytest.raises(PermissionError):
        auth_service.refresh_session(storage, "not.a.jwt")
    with pytest.raises(PermissionError):
        auth_service.refresh_session(storage, auth_base.auth_token[:-4] + "fake")
    # An access token must not be accepted where a refresh token is required.
    with pytest.raises(PermissionError):
        auth_service.refresh_session(storage, auth_base.auth_token)


def test_refresh_token_rejected_as_bearer(storage):
    auth_service.signup(storage, "Mallory", "mallory@example.com", "password123")
    auth_base = auth_service.login(storage, "mallory@example.com", "password123")

    assert auth_service.get_current_user(storage, auth_base.auth_token) is not None
    # A refresh token is not a valid access token.
    assert auth_service.get_current_user(storage, auth_base.refresh_token) is None


def test_magic_link_logs_in_existing_user(storage):
    auth_service.signup(storage, "Niaj", "niaj@example.com", "password123")

    issued = auth_service.request_magic_link(storage, "Niaj@Example.com")
    assert issued["email"] == "niaj@example.com"
    assert "token=" in issued["magic_link"]
    payload = auth_service._decode_jwt(issued["magic_token"])
    assert payload is not None and payload["typ"] == "magic"

    base = auth_service.verify_magic_link(storage, issued["magic_token"])
    assert base.auth_token is not None
    assert base.refresh_token is not None
    assert auth_service.get_current_user(storage, base.auth_token) is not None


def test_magic_link_auto_creates_account(storage):
    issued = auth_service.request_magic_link(storage, "newcomer@example.com")
    base = auth_service.verify_magic_link(storage, issued["magic_token"])

    user = auth_service.get_current_user(storage, base.auth_token)
    assert user is not None
    assert user.email == "newcomer@example.com"
    assert user.name == "newcomer"

    # A password session for the provisioned account is unusable (random password).
    with pytest.raises(PermissionError):
        auth_service.login(storage, "newcomer@example.com", "password123")


def test_magic_link_is_single_use(storage):
    issued = auth_service.request_magic_link(storage, "once@example.com")
    auth_service.verify_magic_link(storage, issued["magic_token"])
    with pytest.raises(PermissionError):
        auth_service.verify_magic_link(storage, issued["magic_token"])


def test_magic_link_rejects_bad_input(storage):
    with pytest.raises(ValueError):
        auth_service.request_magic_link(storage, "not-an-email")
    with pytest.raises(PermissionError):
        auth_service.verify_magic_link(storage, "not.a.jwt")
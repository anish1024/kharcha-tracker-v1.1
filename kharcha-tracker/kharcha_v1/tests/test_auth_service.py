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
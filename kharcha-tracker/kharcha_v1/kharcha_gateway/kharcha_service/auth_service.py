"""
kharcha_v1/kharcha_gateway/kharcha_service/auth_service.py
Implements FR-01..05 authentication and user profile management.
"""
from __future__ import annotations

import base64
import hashlib
import hmac
import json
import os
import secrets
from datetime import datetime, timedelta, timezone
from typing import Any, Optional
from uuid import UUID, uuid4

from .models.base import Base
from .models.user import User
from .storage_service.schema.user_record import UserRecord
from .storage_service.storage_interface import StorageInterface as storage_interface

JWT_SECRET_KEY = os.getenv("KHARCHA_JWT_SECRET", "kharcha-tracker-insecure-secret-key-change-in-prod")
TOKEN_EXPIRY_HOURS = 24
RESET_TOKEN_EXPIRY_HOURS = 1

# Reset token cache: {reset_token_str: (user_id, expiry_datetime)}
_ACTIVE_RESETS: dict[str, tuple[UUID, datetime]] = {}


# ---------------------------------------------------------------------------
# Standard-Library JWT Helpers (Option A)
# ---------------------------------------------------------------------------
def _b64url_encode(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).decode("utf-8").rstrip("=")


def _b64url_decode(segment: str) -> bytes:
    padding = "=" * ((4 - len(segment) % 4) % 4)
    return base64.urlsafe_b64decode(segment + padding)


def _encode_jwt(payload: dict[str, Any], secret: str = JWT_SECRET_KEY) -> str:
    header = {"alg": "HS256", "typ": "JWT"}
    hdr_b64 = _b64url_encode(json.dumps(header, separators=(",", ":")).encode("utf-8"))
    payload_b64 = _b64url_encode(json.dumps(payload, separators=(",", ":")).encode("utf-8"))
    signing_input = f"{hdr_b64}.{payload_b64}".encode("utf-8")
    sig = hmac.new(secret.encode("utf-8"), signing_input, hashlib.sha256).digest()
    sig_b64 = _b64url_encode(sig)
    return f"{hdr_b64}.{payload_b64}.{sig_b64}"


def _decode_jwt(token: str, secret: str = JWT_SECRET_KEY) -> Optional[dict[str, Any]]:
    parts = token.split(".")
    if len(parts) != 3:
        return None
    hdr_b64, payload_b64, sig_b64 = parts
    signing_input = f"{hdr_b64}.{payload_b64}".encode("utf-8")
    expected_sig = hmac.new(secret.encode("utf-8"), signing_input, hashlib.sha256).digest()
    try:
        actual_sig = _b64url_decode(sig_b64)
        if not hmac.compare_digest(expected_sig, actual_sig):
            return None
        payload = json.loads(_b64url_decode(payload_b64).decode("utf-8"))
        exp = payload.get("exp")
        if exp and datetime.now(timezone.utc).timestamp() > exp:
            return None
        return payload
    except Exception:
        return None


# ---------------------------------------------------------------------------
# Password Helpers
# ---------------------------------------------------------------------------
def _hash_password(password: str, salt: Optional[str] = None) -> str:
    if salt is None:
        salt = secrets.token_hex(16)
    hashed = hashlib.sha256((salt + password).encode("utf-8")).hexdigest()
    return f"{salt}:{hashed}"


def _verify_password(password: str, stored_hash: str) -> bool:
    try:
        salt, expected_hash = stored_hash.split(":", 1)
        actual_hash = hashlib.sha256((salt + password).encode("utf-8")).hexdigest()
        return hmac.compare_digest(actual_hash, expected_hash)
    except Exception:
        return False


def _find_user_by_email(storage: storage_interface, email: str) -> Optional[UserRecord]:
    records = storage.list_records(
        schema="user_record",
        filters={"email": [email.strip().lower()]},
        limit=1,
    )
    if records:
        record = records[0]
        if not getattr(record, "is_deleted", False):
            return record  # type: ignore[return-value]
    return None


# ---------------------------------------------------------------------------
# Public Functions (Preserving Exact YAML Signatures)
# ---------------------------------------------------------------------------
def signup(
    storage: storage_interface,
    name: str,
    email: str,
    password: str,
    avatar_url: Optional[str] = None,
) -> User:
    normalized_email = email.strip().lower()
    if not normalized_email or "@" not in normalized_email:
        raise ValueError("Invalid email format")
    if not password or len(password) < 6:
        raise ValueError("Password must be at least 6 characters")

    existing = _find_user_by_email(storage, normalized_email)
    if existing:
        raise ValueError(f"Email '{normalized_email}' is already registered")

    now = datetime.now(timezone.utc)
    user_id = uuid4()
    hashed = _hash_password(password)

    record = UserRecord(
        id=user_id,
        name=name.strip(),
        password=hashed,
        email=normalized_email,
        avatar_url=avatar_url,
        is_active=True,
        is_deleted=False,
        created_by=user_id,
        created_at=now,
        modified_by=user_id,
        modified_at=now,
    )
    saved = storage.save_record(record, schema="user_record")
    return User(
        id=saved.id,
        name=saved.name,
        password="",
        email=saved.email,
        avatar_url=saved.avatar_url,
    )


def login(
    storage: storage_interface,
    email: str,
    password: str,
) -> Base:
    normalized_email = email.strip().lower()
    user = _find_user_by_email(storage, normalized_email)
    if not user or not getattr(user, "is_active", True):
        raise PermissionError("Invalid credentials")

    if not _verify_password(password, user.password):
        raise PermissionError("Invalid credentials")

    now = datetime.now(timezone.utc)
    expiry = now + timedelta(hours=TOKEN_EXPIRY_HOURS)
    payload = {
        "sub": str(user.id),
        "email": user.email,
        "iat": int(now.timestamp()),
        "exp": int(expiry.timestamp()),
    }
    jwt_token = _encode_jwt(payload)

    return Base(
        auth_user_id=user.id,
        auth_token=jwt_token,
        auth_token_expiry=expiry,
    )


def request_password_reset(
    storage: storage_interface,
    email: str,
) -> None:
    normalized_email = email.strip().lower()
    user = _find_user_by_email(storage, normalized_email)
    if not user:
        raise LookupError(f"User with email '{normalized_email}' not found")

    reset_token = secrets.token_urlsafe(24)
    expiry = datetime.now(timezone.utc) + timedelta(hours=RESET_TOKEN_EXPIRY_HOURS)
    _ACTIVE_RESETS[reset_token] = (user.id, expiry)


def confirm_password_reset(
    storage: storage_interface,
    reset_token: str,
    new_password: str,
) -> None:
    if not new_password or len(new_password) < 6:
        raise ValueError("Password must be at least 6 characters")

    reset_data = _ACTIVE_RESETS.get(reset_token)
    if not reset_data:
        raise ValueError("Invalid or expired reset token")

    user_id, expiry = reset_data
    if datetime.now(timezone.utc) > expiry:
        _ACTIVE_RESETS.pop(reset_token, None)
        raise ValueError("Reset token has expired")

    user_record = storage.get_record(record_id=str(user_id), schema="user_record")
    if not user_record or getattr(user_record, "is_deleted", False):
        raise LookupError("User not found")

    user_record.password = _hash_password(new_password)
    user_record.modified_at = datetime.now(timezone.utc)
    user_record.modified_by = user_id
    storage.save_record(user_record, schema="user_record")
    _ACTIVE_RESETS.pop(reset_token, None)


def get_current_user(
    storage: storage_interface,
    auth_token: str,
) -> Optional[User]:
    payload = _decode_jwt(auth_token)
    if not payload or "sub" not in payload:
        return None

    try:
        user_id = UUID(payload["sub"])
    except Exception:
        return None

    record = storage.get_record(record_id=str(user_id), schema="user_record")
    if not record or getattr(record, "is_deleted", False) or not getattr(record, "is_active", True):
        return None

    return User(
        id=record.id,
        name=record.name,
        password="",
        email=record.email,
        avatar_url=record.avatar_url,
    )


def update_profile(
    storage: storage_interface,
    user_id: UUID,
    name: Optional[str] = None,
    avatar_url: Optional[str] = None,
) -> User:
    record = storage.get_record(record_id=str(user_id), schema="user_record")
    if not record or getattr(record, "is_deleted", False):
        raise LookupError("User not found")

    if name is not None:
        record.name = name.strip()
    if avatar_url is not None:
        record.avatar_url = avatar_url

    record.modified_at = datetime.now(timezone.utc)
    record.modified_by = user_id
    saved = storage.save_record(record, schema="user_record")

    return User(
        id=saved.id,
        name=saved.name,
        password="",
        email=saved.email,
        avatar_url=saved.avatar_url,
    )
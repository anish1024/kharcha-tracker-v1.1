"""
kharcha_v1/kharcha_gateway/app/main.py
FastAPI router and application entrypoint.
"""
from __future__ import annotations

import os
from pathlib import Path
from typing import Any, List, Optional
from uuid import UUID

from fastapi import Depends, FastAPI, Header, HTTPException, Query, status
from fastapi.staticfiles import StaticFiles

from ..kharcha_service import (
    auth_service,
    expense_service,
    group_service,
    invitation_service,
    notification_service,
    settlement_engine,
    subscription_service,
)
from ..kharcha_service.models import (
    Base,
    Expense,
    ExpenseUpdate,
    Group,
    Invitation,
    Notification,
    Payout,
    SettlementPreview,
    SettlementRound,
    User,
)
from ..kharcha_service.storage_service.flat_file import FlatFileStorage
from ..kharcha_service.storage_service.schema.audit_log import AuditLog
from ..kharcha_service.storage_service.schema.expense_record import ExpenseRecord
from ..kharcha_service.storage_service.schema.group_record import GroupRecord
from ..kharcha_service.storage_service.schema.invitation_record import InvitationRecord
from ..kharcha_service.storage_service.schema.notification_record import NotificationRecord
from ..kharcha_service.storage_service.schema.payout_record import PayoutRecord
from ..kharcha_service.storage_service.schema.settlement_round_record import (
    SettlementRoundRecord,
)
from ..kharcha_service.storage_service.schema.user_record import UserRecord
from ..kharcha_service.storage_service.storage_interface import StorageInterface as storage_interface

app = FastAPI(title="Kharcha Tracker API", version="0.2.1")

_STORAGE_INSTANCE: Optional[storage_interface] = None

# Application schema registry for the flat-file backend (mirrors the table
# mapping in flat_file.py). Passed programmatically so the server does not
# depend on an on-disk schema_registry.json.
_FLAT_FILE_SCHEMA_REGISTRY = {
    "user_record": UserRecord,
    "group_record": GroupRecord,
    "expense_record": ExpenseRecord,
    "payout_record": PayoutRecord,
    "settlement_round_record": SettlementRoundRecord,
    "notification_record": NotificationRecord,
    "invitation_record": InvitationRecord,
    "audit_log": AuditLog,
}


def get_storage() -> storage_interface:
    global _STORAGE_INSTANCE
    if _STORAGE_INSTANCE is None:
        backend = os.getenv("KHARCHA_STORAGE", "flat_file").lower()
        if backend == "flat_file":
            _STORAGE_INSTANCE = FlatFileStorage(
                schema_registry=_FLAT_FILE_SCHEMA_REGISTRY
            )
        elif backend == "sql_lite":
            from ..kharcha_service.storage_service.sql_lite import SQLiteStorage
            _STORAGE_INSTANCE = SQLiteStorage()
        elif backend == "postgres":
            from ..kharcha_service.storage_service.postgres import PostgresStorage
            _STORAGE_INSTANCE = PostgresStorage()
        else:
            raise RuntimeError(f"Unsupported KHARCHA_STORAGE backend: {backend}")
    return _STORAGE_INSTANCE


def get_authenticated_user(
    authorization: Optional[str] = Header(None),
    storage: storage_interface = Depends(get_storage),
) -> User:
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Missing or malformed Authorization header",
        )
    token = authorization.split("Bearer ", 1)[1].strip()
    user = auth_service.get_current_user(storage, token)
    if not user:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid or expired token",
        )
    return user


def verify_membership(
    group_id: UUID,
    actor_id: UUID,
    storage: storage_interface,
) -> None:
    if not group_service.is_member(storage, group_id, actor_id):
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail=f"User {actor_id} is not a member of group {group_id}",
        )


# ---------------------------------------------------------------------------
# Public Health
# ---------------------------------------------------------------------------
@app.get("/api/v1/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


# ---------------------------------------------------------------------------
# Public Auth Endpoints
# ---------------------------------------------------------------------------
@app.post("/api/v1/auth/signup", response_model=User, status_code=status.HTTP_201_CREATED)
def signup(
    payload: dict[str, Any],
    storage: storage_interface = Depends(get_storage),
) -> User:
    try:
        return auth_service.signup(
            storage=storage,
            name=payload.get("name", ""),
            email=payload.get("email", ""),
            password=payload.get("password", ""),
            avatar_url=payload.get("avatar_url"),
        )
    except ValueError as e:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail=str(e))


@app.post("/api/v1/auth/login", response_model=Base)
def login(
    payload: dict[str, Any],
    storage: storage_interface = Depends(get_storage),
) -> Base:
    try:
        return auth_service.login(
            storage=storage,
            email=payload.get("email", ""),
            password=payload.get("password", ""),
        )
    except PermissionError:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid credentials")


@app.post("/api/v1/auth/refresh", response_model=Base)
def refresh_tokens(
    payload: dict[str, Any],
    storage: storage_interface = Depends(get_storage),
) -> Base:
    try:
        return auth_service.refresh_session(
            storage=storage,
            refresh_token=payload.get("refresh_token", ""),
        )
    except PermissionError as e:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail=str(e))


@app.post("/api/v1/auth/magic-link", status_code=status.HTTP_202_ACCEPTED)
def request_magic_link(
    payload: dict[str, Any],
    storage: storage_interface = Depends(get_storage),
) -> dict[str, Any]:
    try:
        return auth_service.request_magic_link(
            storage=storage,
            email=payload.get("email", ""),
        )
    except ValueError as e:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail=str(e))


@app.post("/api/v1/auth/magic-link/verify", response_model=Base)
def verify_magic_link(
    payload: dict[str, Any],
    storage: storage_interface = Depends(get_storage),
) -> Base:
    try:
        return auth_service.verify_magic_link(
            storage=storage,
            token=payload.get("token", ""),
        )
    except PermissionError as e:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail=str(e))


@app.post("/api/v1/auth/password-reset/request", status_code=status.HTTP_202_ACCEPTED)
def request_password_reset(
    payload: dict[str, Any],
    storage: storage_interface = Depends(get_storage),
) -> dict[str, str]:
    try:
        auth_service.request_password_reset(
            storage=storage,
            email=payload.get("email", ""),
        )
    except LookupError:
        pass
    return {"message": "Password reset request accepted"}


@app.post("/api/v1/auth/password-reset/confirm", status_code=status.HTTP_200_OK)
def confirm_password_reset(
    payload: dict[str, Any],
    storage: storage_interface = Depends(get_storage),
) -> dict[str, str]:
    try:
        auth_service.confirm_password_reset(
            storage=storage,
            reset_token=payload.get("reset_token", ""),
            new_password=payload.get("new_password", ""),
        )
        return {"message": "Password successfully updated"}
    except ValueError as e:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail=str(e))
    except LookupError as e:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=str(e))


# ---------------------------------------------------------------------------
# Authenticated User Profiles (FR-03)
# ---------------------------------------------------------------------------
@app.get("/api/v1/users/me", response_model=User)
def get_current_user(
    current_user: User = Depends(get_authenticated_user),
) -> User:
    return current_user


@app.patch("/api/v1/users/me", response_model=User)
def update_profile(
    payload: dict[str, Any],
    current_user: User = Depends(get_authenticated_user),
    storage: storage_interface = Depends(get_storage),
) -> User:
    try:
        return auth_service.update_profile(
            storage=storage,
            user_id=current_user.id,
            name=payload.get("name"),
            avatar_url=payload.get("avatar_url"),
        )
    except LookupError as e:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=str(e))
    except ValueError as e:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail=str(e))


# ---------------------------------------------------------------------------
# Subscription & Trial (mock payment)
# ---------------------------------------------------------------------------
@app.get("/api/v1/users/me/subscription")
def get_subscription_status(
    current_user: User = Depends(get_authenticated_user),
    storage: storage_interface = Depends(get_storage),
) -> dict[str, Any]:
    try:
        return subscription_service.get_status(storage, current_user.id)
    except LookupError as e:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=str(e))


@app.post("/api/v1/subscription")
def activate_subscription(
    payload: dict[str, Any],
    current_user: User = Depends(get_authenticated_user),
    storage: storage_interface = Depends(get_storage),
) -> dict[str, Any]:
    try:
        return subscription_service.activate(
            storage=storage,
            user_id=current_user.id,
            plan=payload.get("plan", ""),
        )
    except LookupError as e:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=str(e))
    except ValueError as e:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail=str(e))


# ---------------------------------------------------------------------------
# Authenticated Group Endpoints (FR-06..10)
# ---------------------------------------------------------------------------
@app.post("/api/v1/groups", response_model=Group, status_code=status.HTTP_201_CREATED)
def create_group(
    payload: dict[str, Any],
    current_user: User = Depends(get_authenticated_user),
    storage: storage_interface = Depends(get_storage),
) -> Group:
    try:
        return group_service.create_group(
            storage=storage,
            name=payload.get("name", ""),
            currency=payload.get("currency", "INR"),
            created_by=current_user.id,
        )
    except ValueError as e:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail=str(e))


@app.get("/api/v1/groups", response_model=List[Group])
def list_groups(
    current_user: User = Depends(get_authenticated_user),
    storage: storage_interface = Depends(get_storage),
) -> List[Group]:
    return group_service.list_groups_for_user(storage, current_user.id)


@app.get("/api/v1/groups/{group_id}", response_model=Group)
def get_group(
    group_id: UUID,
    current_user: User = Depends(get_authenticated_user),
    storage: storage_interface = Depends(get_storage),
) -> Group:
    verify_membership(group_id, current_user.id, storage)
    try:
        return group_service.get_group(storage, group_id)
    except LookupError as e:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=str(e))


@app.delete("/api/v1/groups/{group_id}", response_model=bool)
def delete_group(
    group_id: UUID,
    current_user: User = Depends(get_authenticated_user),
    storage: storage_interface = Depends(get_storage),
) -> bool:
    try:
        return group_service.delete_group(storage, group_id, current_user.id)
    except LookupError as e:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=str(e))
    except PermissionError as e:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail=str(e))
    except ValueError as e:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail=str(e))


@app.post("/api/v1/groups/{group_id}/invite")
def generate_invite_code(
    group_id: UUID,
    current_user: User = Depends(get_authenticated_user),
    storage: storage_interface = Depends(get_storage),
) -> dict[str, str]:
    verify_membership(group_id, current_user.id, storage)
    try:
        code = group_service.generate_invite_code(storage, group_id, current_user.id)
        return {"invite_code": code}
    except LookupError as e:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=str(e))
    except ValueError as e:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail=str(e))


@app.post("/api/v1/groups/{group_id}/invite/accept", response_model=Group)
def join_group_by_invite(
    group_id: UUID,
    payload: dict[str, Any],
    current_user: User = Depends(get_authenticated_user),
    storage: storage_interface = Depends(get_storage),
) -> Group:
    try:
        return group_service.join_group_by_invite(
            storage=storage,
            group_id=group_id,
            invite_code=payload.get("invite_code", ""),
            user_id=current_user.id,
        )
    except LookupError as e:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=str(e))
    except ValueError as e:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail=str(e))


@app.post("/api/v1/groups/{group_id}/members", response_model=Group)
def add_member(
    group_id: UUID,
    payload: dict[str, Any],
    current_user: User = Depends(get_authenticated_user),
    storage: storage_interface = Depends(get_storage),
) -> Group:
    verify_membership(group_id, current_user.id, storage)
    try:
        return group_service.add_member(
            storage=storage,
            group_id=group_id,
            email=payload.get("email", ""),
            actor_user_id=current_user.id,
        )
    except LookupError as e:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=str(e))
    except ValueError as e:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail=str(e))


@app.delete("/api/v1/groups/{group_id}/members/me", response_model=Group)
def leave_group(
    group_id: UUID,
    current_user: User = Depends(get_authenticated_user),
    storage: storage_interface = Depends(get_storage),
) -> Group:
    verify_membership(group_id, current_user.id, storage)
    try:
        return group_service.leave_group(storage, group_id, current_user.id)
    except LookupError as e:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=str(e))
    except ValueError as e:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail=str(e))


# ---------------------------------------------------------------------------
# Group invitations (pending) + owner notifications
# ---------------------------------------------------------------------------
@app.post("/api/v1/groups/{group_id}/invitations", response_model=Invitation)
def create_invitation(
    group_id: UUID,
    payload: dict[str, Any],
    current_user: User = Depends(get_authenticated_user),
    storage: storage_interface = Depends(get_storage),
) -> Invitation:
    verify_membership(group_id, current_user.id, storage)
    try:
        return invitation_service.create_invitation(
            storage=storage,
            group_id=group_id,
            inviter_id=current_user.id,
            email=payload.get("email", ""),
        )
    except LookupError as e:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=str(e))
    except PermissionError as e:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail=str(e))
    except ValueError as e:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail=str(e))


@app.get("/api/v1/groups/{group_id}/invitations", response_model=List[Invitation])
def list_group_invitations(
    group_id: UUID,
    current_user: User = Depends(get_authenticated_user),
    storage: storage_interface = Depends(get_storage),
) -> List[Invitation]:
    verify_membership(group_id, current_user.id, storage)
    return invitation_service.list_invitations_for_group(storage, group_id)


@app.get("/api/v1/users/me/invitations", response_model=List[Invitation])
def list_my_invitations(
    current_user: User = Depends(get_authenticated_user),
    storage: storage_interface = Depends(get_storage),
) -> List[Invitation]:
    return invitation_service.list_invitations_for_email(storage, current_user.email)


@app.post("/api/v1/invitations/{invitation_id}/accept", response_model=Invitation)
def accept_invitation(
    invitation_id: UUID,
    current_user: User = Depends(get_authenticated_user),
    storage: storage_interface = Depends(get_storage),
) -> Invitation:
    try:
        return invitation_service.accept_invitation(
            storage, invitation_id, current_user.id, current_user.email
        )
    except LookupError as e:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=str(e))
    except PermissionError as e:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail=str(e))
    except ValueError as e:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail=str(e))


@app.post("/api/v1/invitations/{invitation_id}/decline", response_model=Invitation)
def decline_invitation(
    invitation_id: UUID,
    current_user: User = Depends(get_authenticated_user),
    storage: storage_interface = Depends(get_storage),
) -> Invitation:
    try:
        return invitation_service.decline_invitation(
            storage, invitation_id, current_user.id, current_user.email
        )
    except LookupError as e:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=str(e))
    except PermissionError as e:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail=str(e))
    except ValueError as e:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail=str(e))


@app.post("/api/v1/groups/{group_id}/lock", response_model=Group)
def lock_group(
    group_id: UUID,
    current_user: User = Depends(get_authenticated_user),
    storage: storage_interface = Depends(get_storage),
) -> Group:
    verify_membership(group_id, current_user.id, storage)
    try:
        return group_service.lock_group(storage, group_id, current_user.id)
    except LookupError as e:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=str(e))
    except ValueError as e:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail=str(e))


@app.post("/api/v1/groups/{group_id}/unlock", response_model=Group)
def unlock_group(
    group_id: UUID,
    current_user: User = Depends(get_authenticated_user),
    storage: storage_interface = Depends(get_storage),
) -> Group:
    verify_membership(group_id, current_user.id, storage)
    try:
        return group_service.unlock_group(storage, group_id, current_user.id)
    except LookupError as e:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=str(e))
    except ValueError as e:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail=str(e))


@app.get("/api/v1/groups/{group_id}/activity", response_model=List[AuditLog])
def get_group_activity(
    group_id: UUID,
    limit: int = Query(50, ge=1, le=1000),
    offset: int = Query(0, ge=0),
    current_user: User = Depends(get_authenticated_user),
    storage: storage_interface = Depends(get_storage),
) -> List[AuditLog]:
    verify_membership(group_id, current_user.id, storage)
    return group_service.get_group_activity(storage, group_id, limit, offset)


# ---------------------------------------------------------------------------
# Authenticated Remaining Domain Stubs (Expense & Settlement)
# ---------------------------------------------------------------------------
@app.post("/api/v1/expenses")
def add_expense(
    payload: dict[str, Any],
    current_user: User = Depends(get_authenticated_user),
    storage: storage_interface = Depends(get_storage),
) -> dict[str, Any]:
    try:
        subscription_service.consume_entry(storage, current_user.id)
    except subscription_service.SubscriptionRequiredError as e:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail={"code": e.code, "message": str(e)},
        )
    return {}

@app.get("/api/v1/groups/{group_id}/expenses")
def list_group_expenses(
    group_id: UUID,
    current_user: User = Depends(get_authenticated_user),
) -> list[Any]:
    return []

@app.get("/api/v1/expenses/{trans_id}")
def get_expense(
    trans_id: int,
    current_user: User = Depends(get_authenticated_user),
) -> dict[str, Any]:
    return {}

@app.patch("/api/v1/expenses/{trans_id}")
def update_expense(
    trans_id: int,
    payload: dict[str, Any],
    current_user: User = Depends(get_authenticated_user),
) -> dict[str, Any]:
    return {}

@app.delete("/api/v1/expenses/{trans_id}")
def delete_expense(
    trans_id: int,
    current_user: User = Depends(get_authenticated_user),
) -> bool:
    return False

@app.get("/api/v1/groups/{group_id}/settle")
def preview_settlement(
    group_id: UUID,
    user_id: Optional[UUID] = Query(None),
    current_user: User = Depends(get_authenticated_user),
) -> dict[str, Any]:
    return {}

@app.post("/api/v1/groups/{group_id}/settle")
def initiate_settlement(
    group_id: UUID,
    current_user: User = Depends(get_authenticated_user),
) -> list[Any]:
    return []

@app.post("/api/v1/groups/{group_id}/settle/me")
def settle_user(
    group_id: UUID,
    current_user: User = Depends(get_authenticated_user),
) -> list[Any]:
    return []

@app.get("/api/v1/groups/{group_id}/settlement-rounds")
def list_settlement_rounds(
    group_id: UUID,
    current_user: User = Depends(get_authenticated_user),
) -> list[Any]:
    return []

@app.get("/api/v1/groups/{group_id}/payouts")
def list_payouts(
    group_id: UUID,
    user_id: Optional[UUID] = Query(None),
    current_user: User = Depends(get_authenticated_user),
) -> list[Any]:
    return []

@app.post("/api/v1/groups/{group_id}/payouts/{payout_id}/paid")
def mark_payout_paid(
    group_id: UUID,
    payout_id: UUID,
    current_user: User = Depends(get_authenticated_user),
) -> dict[str, Any]:
    return {}

@app.post("/api/v1/groups/{group_id}/payouts/{payout_id}/settled")
def settle_payout(
    group_id: UUID,
    payout_id: UUID,
    current_user: User = Depends(get_authenticated_user),
) -> dict[str, Any]:
    return {}

@app.post("/api/v1/groups/{group_id}/payouts/{payout_id}/failed")
def fail_payout(
    group_id: UUID,
    payout_id: UUID,
    current_user: User = Depends(get_authenticated_user),
) -> dict[str, Any]:
    return {}

@app.post("/api/v1/groups/{group_id}/payouts/{payout_id}/cancel")
def cancel_payout(
    group_id: UUID,
    payout_id: UUID,
    current_user: User = Depends(get_authenticated_user),
) -> dict[str, Any]:
    return {}

@app.post("/api/v1/groups/{group_id}/payouts/{payout_id}/remind")
def remind_payout(
    group_id: UUID,
    payout_id: UUID,
    current_user: User = Depends(get_authenticated_user),
) -> dict[str, Any]:
    return {}

@app.post("/api/v1/groups/{group_id}/payouts/{payout_id}/escalate")
def escalate_payout(
    group_id: UUID,
    payout_id: UUID,
    current_user: User = Depends(get_authenticated_user),
) -> dict[str, Any]:
    return {}

@app.post("/api/v1/groups/{group_id}/payouts/{payout_id}/expenses")
def link_payout_expenses(
    group_id: UUID,
    payout_id: UUID,
    current_user: User = Depends(get_authenticated_user),
) -> dict[str, Any]:
    return {}

@app.post("/api/v1/groups/{group_id}/payments")
def record_adhoc_payment(
    group_id: UUID,
    current_user: User = Depends(get_authenticated_user),
) -> dict[str, Any]:
    return {}

@app.get("/api/v1/users/me/payouts")
def list_my_payouts(
    current_user: User = Depends(get_authenticated_user),
) -> list[Any]:
    return []

@app.get("/api/v1/users/me/notifications", response_model=List[Notification])
def list_my_notifications(
    unread_only: bool = Query(False),
    current_user: User = Depends(get_authenticated_user),
    storage: storage_interface = Depends(get_storage),
) -> List[Notification]:
    return notification_service.list_notifications(
        storage, current_user.id, unread_only=unread_only
    )

@app.post("/api/v1/users/me/notifications/read")
def mark_notifications_read(
    current_user: User = Depends(get_authenticated_user),
    storage: storage_interface = Depends(get_storage),
) -> int:
    return notification_service.mark_all_read(storage, current_user.id)


# ---------------------------------------------------------------------------
# Static Frontend (vanilla HTML/CSS/JS) — mounted last so /api/v1 and /docs
# keep matching first; every other path serves the SPA.
# ---------------------------------------------------------------------------
_FRONTEND_DIR = Path(__file__).resolve().parent.parent / "frontend_service"
if _FRONTEND_DIR.is_dir():
    app.mount("/", StaticFiles(directory=str(_FRONTEND_DIR), html=True), name="frontend")
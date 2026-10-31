"""
kharcha_v1/kharcha_gateway/kharcha_service/storage_service/schema/audit_log.py
Audit log storage schema capturing entity mutations.
"""
from __future__ import annotations

from typing import Optional
from uuid import UUID

from pydantic import Field

from .base_record import BaseRecord


class AuditLog(BaseRecord):
    id: Optional[UUID] = Field(default=None, description="Primary key audit log UUID")
    event: str = Field(
        ...,
        description="Event kind: CREATE, UPDATE, DELETE, LOCK, UNLOCK, SETTLE, CANCEL, RECOVER",
    )
    entity_type: str = Field(
        ...,
        description="Entity type: group, group_note, expense, user, payout, audit_log",
    )
    entity_id: Optional[UUID] = Field(default=None, description="Target entity UUID")
    group_id: Optional[UUID] = Field(
        default=None,
        description="Owning group UUID (set on group rows and on expense/payout rows)",
    )
    action: Optional[str] = Field(default=None, description="Specific action executed")
    field_name: Optional[str] = Field(default=None, description="Field modified if applicable")
    old_value: Optional[str] = Field(default=None, description="Serialized old value prior to mutation")
    new_value: Optional[str] = Field(default=None, description="Serialized new value after mutation")
    actor_user_id: Optional[UUID] = Field(default=None, description="User UUID initiating the action")
    actor_name: Optional[str] = Field(default=None, description="Display name of actor")
    actor_ip: Optional[str] = Field(default=None, description="Actor IP address")
    actor_user_agent: Optional[str] = Field(default=None, description="Client User-Agent header")
    reason: Optional[str] = Field(default=None, description="Reason supplied for action")
    error_code: Optional[str] = Field(default=None, description="Error code if failure logged")
"""
kharcha_v1/kharcha_gateway/kharcha_service/storage_service/schema/invitation_record.py
Pending group-invitation table row storage schema.
"""
from __future__ import annotations

from datetime import datetime
from typing import Optional
from uuid import UUID

from pydantic import Field

from .base_record import BaseRecord


class InvitationRecord(BaseRecord):
    id: Optional[UUID] = Field(default=None, description="Primary key invitation UUID")
    group_id: UUID = Field(..., description="Group the invitee is invited to")
    inviter_id: UUID = Field(..., description="User who sent the invitation")
    invitee_email: str = Field(..., description="Email address of the invitee")
    invite_code: str = Field(..., description="Invite code snapshot at send time")
    status: str = Field(default="pending", description="Invitation status enum string")
    expires_at: Optional[datetime] = Field(default=None, description="Invitation expiry")

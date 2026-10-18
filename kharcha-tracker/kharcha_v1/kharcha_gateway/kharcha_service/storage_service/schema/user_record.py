"""
kharcha_v1/kharcha_gateway/kharcha_service/storage_service/schema/user_record.py
User table row storage schema.
"""
from __future__ import annotations

from datetime import datetime
from typing import Optional
from uuid import UUID

from pydantic import Field

from .base_record import BaseRecord


class UserRecord(BaseRecord):
    id: Optional[UUID] = Field(default=None, description="Primary key user UUID")
    name: str = Field(..., description="Display name of the user")
    password: str = Field(..., description="Hashed password string")
    email: str = Field(..., description="Unique email address")
    avatar_url: Optional[str] = Field(default=None, description="Profile avatar URL")
    is_active: bool = Field(default=True, description="Active status flag")
    trial_ends_at: Optional[datetime] = Field(
        default=None, description="End of the free trial period (1 year from signup)"
    )
    plan: str = Field(default="none", description="Active subscription plan: none|monthly|6month|yearly|lifetime")
    plan_status: str = Field(default="none", description="Subscription status: none|active")
    plan_ends_at: Optional[datetime] = Field(
        default=None, description="Plan expiry (null for lifetime or no plan)"
    )
    limited_entries_used: int = Field(
        default=0, description="Entries consumed after the trial/plan expired (limited mode)"
    )
    # age: Optional[int] = Field(default=None, description="User age")
    # extra_sort: Optional[str] = Field(default=None, description="Extra sort field for testing")
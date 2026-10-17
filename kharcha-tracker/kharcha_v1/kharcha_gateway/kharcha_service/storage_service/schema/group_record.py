"""
kharcha_v1/kharcha_gateway/kharcha_service/storage_service/schema/group_record.py
Group table row storage schema.
"""
from __future__ import annotations

from typing import List, Optional
from uuid import UUID

from pydantic import Field

from .base_record import BaseRecord


class GroupRecord(BaseRecord):
    id: Optional[UUID] = Field(default=None, description="Primary key group UUID")
    name: str = Field(..., min_length=1, max_length=100, description="Group name")
    invite_code: Optional[str] = Field(default=None, description="Unique join invite code")
    members: List[UUID] = Field(default_factory=list, description="List of member user UUIDs")
    status: str = Field(
        ...,
        description="Lifecycle status: OPEN, LOCKED, SETTLED, CANCELLED, PARTIAL_SETTLEMENT",
    )
    currency: str = Field(..., pattern=r"^[A-Z]{3}$", description="3-letter currency code (e.g. INR, USD, EUR)")
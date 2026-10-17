"""
kharcha_v1/kharcha_gateway/kharcha_service/storage_service/schema/user_record.py
User table row storage schema.
"""
from __future__ import annotations

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
    # age: Optional[int] = Field(default=None, description="User age")
    # extra_sort: Optional[str] = Field(default=None, description="Extra sort field for testing")
"""
kharcha_v1/kharcha_gateway/kharcha_service/storage_service/schema/base_record.py
Common base columns present on all table schema records.
"""
from __future__ import annotations

from datetime import datetime
from typing import Optional
from uuid import UUID

from pydantic import BaseModel, Field


class BaseRecord(BaseModel):
    is_deleted: bool = Field(default=False, description="Soft deletion flag")
    created_by: Optional[UUID] = Field(default=None, description="User ID who created the record")
    created_at: Optional[datetime] = Field(default=None, description="Timestamp when the record was created")
    modified_by: Optional[UUID] = Field(default=None, description="User ID who last modified the record")
    modified_at: Optional[datetime] = Field(default=None, description="Timestamp when the record was last modified")
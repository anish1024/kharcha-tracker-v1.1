"""
kharcha_v1/kharcha_gateway/kharcha_service/storage_service/schema/group_note.py
Group note table row storage schema (E5).
"""
from __future__ import annotations

from datetime import datetime
from typing import List, Optional
from uuid import UUID

from pydantic import Field

from .base_record import BaseRecord


class GroupNoteRecord(BaseRecord):
    id: Optional[UUID] = Field(default=None, description="Primary key group note UUID")
    group_id: UUID = Field(..., description="Owning group UUID")
    date: datetime = Field(
        ...,
        description="Author date the note is about (may differ from created_at)",
    )
    message: str = Field(
        ..., min_length=1, max_length=500, description="Note message"
    )
    tags: List[str] = Field(
        default_factory=list, description="Free-form tags (list of strings)"
    )

from datetime import datetime
from typing import List, Optional
from uuid import UUID

from pydantic import BaseModel


class GroupNote(BaseModel):
    id: UUID
    group_id: UUID
    # E5: author-facing date the note is about (may differ from created_at).
    date: datetime
    message: str
    tags: List[str]
    created_by: UUID
    created_at: datetime
    modified_by: Optional[UUID] = None
    modified_at: Optional[datetime] = None

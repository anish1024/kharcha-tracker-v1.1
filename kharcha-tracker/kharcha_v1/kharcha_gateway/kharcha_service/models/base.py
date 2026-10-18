from datetime import datetime
from typing import Optional
from uuid import UUID

from pydantic import BaseModel


class Base(BaseModel):
    auth_user_id: UUID
    auth_token: str
    auth_token_expiry: datetime
    refresh_token: Optional[str] = None
    refresh_token_expiry: Optional[datetime] = None

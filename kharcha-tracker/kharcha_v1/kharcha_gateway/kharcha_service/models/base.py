from datetime import datetime
from uuid import UUID

from pydantic import BaseModel


class Base(BaseModel):
    auth_user_id: UUID
    auth_token: str
    auth_token_expiry: datetime

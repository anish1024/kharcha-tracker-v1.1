from uuid import UUID

from pydantic import BaseModel


class ExpenseShare(BaseModel):
    user_id: UUID
    percentage: float

from dataclasses import dataclass
from uuid import UUID


@dataclass(frozen=True)
class MatrixRowKey:
    trans_id: int | None
    payout_id: UUID | None
    expense_ids: list[int]

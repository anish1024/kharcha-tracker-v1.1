from dataclasses import dataclass
from uuid import UUID


@dataclass(frozen=True)
class MatrixRowKey:
    trans_id: int | None
    payout_id: UUID | None
    expense_ids: tuple[int, ...]


def mrk_from_expense(trans_id: int, expense_ids: list[int] | tuple[int, ...]) -> MatrixRowKey:
    if isinstance(expense_ids, tuple):
        return MatrixRowKey(trans_id=trans_id, payout_id=None, expense_ids=expense_ids)
    return MatrixRowKey(trans_id=trans_id, payout_id=None, expense_ids=tuple(expense_ids))


def mrk_from_payout(payout_id: UUID, expense_ids: list[int] | tuple[int, ...]) -> MatrixRowKey:
    if isinstance(expense_ids, tuple):
        return MatrixRowKey(trans_id=None, payout_id=payout_id, expense_ids=expense_ids)
    return MatrixRowKey(trans_id=None, payout_id=payout_id, expense_ids=tuple(expense_ids))

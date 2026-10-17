from dataclasses import dataclass
from uuid import UUID

from .matrix_row_key import MatrixRowKey


@dataclass(frozen=True)
class RowPayout:
    row_key: MatrixRowKey
    payor_id: UUID
    payee_id: UUID
    amount_minor: int

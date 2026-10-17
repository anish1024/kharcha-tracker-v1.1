from dataclasses import dataclass
from uuid import UUID

from .payout import Payout


@dataclass(frozen=True)
class SettlementPreview:
    group_id: UUID
    group_status: str
    net_dues: dict[UUID, float]
    open_payouts: list[Payout]
    proposed_payouts: list[Payout]
    orphan_payouts: list[Payout]

# Generated from kharcha_tracker.yaml.
# Do not add enum members or configuration values outside the blueprint.

from enum import Enum


class GroupStatus(str, Enum):
    OPEN = "OPEN"
    LOCKED = "LOCKED"
    SETTLED = "SETTLED"
    CANCELLED = "CANCELLED"
    PARTIAL_SETTLEMENT = "PARTIAL_SETTLEMENT"


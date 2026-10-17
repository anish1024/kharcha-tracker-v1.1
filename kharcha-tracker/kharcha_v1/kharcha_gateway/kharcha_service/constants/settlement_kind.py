# Generated from kharcha_tracker.yaml.
# Do not add enum members or configuration values outside the blueprint.

from enum import Enum


class SettlementKind(str, Enum):
    INDIVIDUAL = "INDIVIDUAL"
    PERIODIC = "PERIODIC"
    FINAL = "FINAL"


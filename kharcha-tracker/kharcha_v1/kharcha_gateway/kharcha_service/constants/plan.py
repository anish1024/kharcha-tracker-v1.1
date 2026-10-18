# Generated from kharcha_tracker.yaml.
# Do not add enum members or configuration values outside the blueprint.

from enum import Enum


class Plan(str, Enum):
    NONE = "none"
    MONTHLY = "monthly"
    SIX_MONTH = "6month"
    YEARLY = "yearly"
    LIFETIME = "lifetime"

# Generated from kharcha_tracker.yaml.
# Do not add enum members or configuration values outside the blueprint.

from enum import Enum


class InvitationStatus(str, Enum):
    PENDING = "pending"
    ACCEPTED = "accepted"
    REJECTED = "rejected"
    EXPIRED = "expired"

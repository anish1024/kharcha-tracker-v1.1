"""Schema-agnostic storage contract for Kharcha Tracker."""

from abc import ABC, abstractmethod
from typing import Optional, Union

from .schema.base_record import BaseRecord

FilterValue = Union[list[str], list[int]]
Filters = dict[str, FilterValue]


class StorageInterface(ABC):
    """Abstract persistence contract operating only on BaseRecord."""

    @abstractmethod
    def save_record(self, record: BaseRecord, schema: str) -> BaseRecord:
        """Save a record and return the saved record."""
        raise NotImplementedError

    @abstractmethod
    def get_record(self, record_id: str, schema: str) -> Optional[BaseRecord]:
        """Return a record by ID, or None when it does not exist."""
        raise NotImplementedError

    @abstractmethod
    def list_records(
        self,
        schema: str,
        filters: Optional[Filters] = None,
        sort_by: Optional[str] = None,
        ascending: bool = True,
        limit: Optional[int] = None,
        offset: int = 0,
    ) -> list[BaseRecord]:
        """Return filtered, sorted and paginated records."""
        raise NotImplementedError

    @abstractmethod
    def delete_record(self, record: BaseRecord, schema: str) -> bool:
        """Soft-delete a record and return whether it was found and deleted."""
        raise NotImplementedError

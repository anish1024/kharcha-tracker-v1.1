"""Flat-file implementation of the schema-agnostic storage contract."""

from __future__ import annotations

import importlib
import json
import os
from pathlib import Path
from typing import Any, Mapping, Optional, Type
from uuid import uuid4

from .schema.base_record import BaseRecord
from .storage_interface import FilterValue, Filters, StorageInterface

PAGE_SIZE = 1000
MAX_TRANSACTION_ID = 4_294_967_295

_DEFAULT_TABLE_FILES: dict[str, str] = {
    "user_record": "user_records.json",
    "group_record": "group_records.json",
    "expense_record": "expense_records.json",
    "payout_record": "payout_records.json",
    "settlement_round_record": "settlement_round_records.json",
    "notification_record": "notification_records.json",
    "invitation_record": "invitation_records.json",
    "audit_log": "audit_logs.json",
}


class FlatFileStorage(StorageInterface):
    """Persist arbitrary BaseRecord subclasses in one JSON array per schema.

    Concrete application schemas are deliberately not imported here. A caller
    registers a schema name with its BaseRecord subclass, making this storage
    implementation independent of the application's record types.
    """

    def __init__(
        self,
        db_path: str | Path | None = None,
        schema_registry: Mapping[str, Type[BaseRecord]] | None = None,
    ) -> None:
        self.db_path = (
            Path(db_path)
            if db_path is not None
            else Path(__file__).resolve().parent / "flat_file_db"
        )
        self.db_path.mkdir(parents=True, exist_ok=True)
        self._schema_registry: dict[str, Type[BaseRecord]] = {}
        self._records: dict[str, list[BaseRecord]] = {}

        if schema_registry:
            for schema, record_type in schema_registry.items():
                self.register_schema(schema, record_type, reload=False)
        else:
            self._load_schema_registry_file()

        for schema in self._schema_registry:
            self._records[schema] = self._load(schema)

    @property
    def schema_registry(self) -> dict[str, Type[BaseRecord]]:
        """Return a copy of the active schema registry."""
        return dict(self._schema_registry)

    def register_schema(
        self,
        schema: str,
        record_type: Type[BaseRecord],
        *,
        reload: bool = True,
    ) -> None:
        """Register a schema and its BaseRecord subclass."""
        if not isinstance(schema, str) or not schema.strip():
            raise ValueError("schema must be a non-empty string")
        if not isinstance(record_type, type) or not issubclass(record_type, BaseRecord):
            raise TypeError("record_type must be a BaseRecord subclass")

        self._schema_registry[schema] = record_type
        if reload:
            self._records[schema] = self._load(schema)
        else:
            self._records.setdefault(schema, [])

    def save_record(self, record: BaseRecord, schema: str) -> BaseRecord:
        record_type = self._require_schema(schema)
        if not isinstance(record, BaseRecord):
            raise TypeError("record must be a BaseRecord instance")
        if not isinstance(record, record_type):
            raise TypeError(
                f"record for schema '{schema}' must be an instance of "
                f"{record_type.__name__}"
            )

        record_id = self._record_id(record)
        if not record_id:
            record_id = str(uuid4())
            record = self._with_id(record, record_id)

        records = self._records[schema]
        replaced = False
        for index, existing in enumerate(records):
            if self._record_id(existing) == record_id:
                records[index] = record
                replaced = True
                break
        if not replaced:
            records.append(record)

        self._records[schema] = self._deduplicate(records)
        self._persist(schema)
        return record

    def get_record(self, record_id: str, schema: str) -> Optional[BaseRecord]:
        self._require_schema(schema)
        for record in self._records[schema]:
            if self._record_id(record) == str(record_id) and not self._is_deleted(record):
                return record
        return None

    def list_records(
        self,
        schema: str,
        filters: Optional[Filters] = None,
        sort_by: Optional[str] = None,
        ascending: bool = True,
        limit: Optional[int] = None,
        offset: int = 0,
    ) -> list[BaseRecord]:
        self._require_schema(schema)
        if offset < 0:
            raise ValueError("offset must be >= 0")
        if limit is not None and limit < 0:
            raise ValueError("limit must be >= 0")

        records = [r for r in self._records[schema] if not self._is_deleted(r)]
        if filters:
            records = [r for r in records if self._matches_filters(r, filters)]
        if sort_by:
            records = self._sort_records(records, sort_by, ascending)

        if limit is None:
            return records[offset:]
        return records[offset : offset + limit]

    def delete_record(self, record: BaseRecord, schema: str) -> bool:
        self._require_schema(schema)
        record_id = self._record_id(record)
        if not record_id:
            return False

        records = self._records[schema]
        for index, existing in enumerate(records):
            if self._record_id(existing) == record_id:
                if self._is_deleted(existing):
                    return False
                records[index] = self._with_deleted(existing)
                self._persist(schema)
                return True
        return False

    def _require_schema(self, schema: str) -> Type[BaseRecord]:
        if schema not in self._schema_registry:
            raise KeyError(f"unknown schema: {schema}")
        return self._schema_registry[schema]

    def _table_filename(self, schema: str) -> str:
        return _DEFAULT_TABLE_FILES.get(schema, f"{schema}.json")

    def _table_path(self, schema: str) -> Path:
        return self.db_path / self._table_filename(schema)

    def _registry_path(self) -> Path:
        return self.db_path / "schema_registry.json"

    def _load_schema_registry_file(self) -> None:
        path = self._registry_path()
        if not path.exists():
            return
        try:
            payload = json.loads(path.read_text(encoding="utf-8"))
        except json.JSONDecodeError as exc:
            raise ValueError(f"invalid schema registry JSON: {path}") from exc
        if not isinstance(payload, dict):
            raise ValueError("schema_registry.json root must be an object")

        for schema, definition in payload.items():
            if not isinstance(definition, dict):
                raise ValueError(f"registry entry for '{schema}' must be an object")
            module_name = definition.get("module")
            class_name = definition.get("class")
            if not module_name or not class_name:
                raise ValueError(
                    f"registry entry for '{schema}' requires module and class"
                )
            try:
                module = importlib.import_module(module_name)
                record_type = getattr(module, class_name)
            except (ImportError, AttributeError) as exc:
                raise ValueError(
                    f"cannot load record type for schema '{schema}'"
                ) from exc
            self.register_schema(schema, record_type, reload=False)

    def _load(self, schema: str) -> list[BaseRecord]:
        path = self._table_path(schema)
        if not path.exists():
            return []
        try:
            payload = json.loads(path.read_text(encoding="utf-8"))
        except json.JSONDecodeError as exc:
            raise ValueError(f"invalid JSON table: {path}") from exc
        if not isinstance(payload, list):
            raise ValueError(f"table must contain a JSON array: {path}")

        record_type = self._schema_registry[schema]
        records: list[BaseRecord] = []
        for item in payload:
            if not isinstance(item, dict):
                raise ValueError(f"record in {path} must be a JSON object")
            try:
                records.append(record_type.model_validate(item))
            except Exception as exc:
                raise ValueError(
                    f"invalid record in schema '{schema}': {item!r}"
                ) from exc
        return self._deduplicate(records)

    def _persist(self, schema: str) -> None:
        path = self._table_path(schema)
        path.parent.mkdir(parents=True, exist_ok=True)
        payload = [self._serialize_record(record) for record in self._records[schema]]
        temp_path = path.with_suffix(path.suffix + ".tmp")
        with temp_path.open("w", encoding="utf-8") as handle:
            json.dump(payload, handle, indent=2, ensure_ascii=False, default=str)
            handle.write("\n")
        os.replace(temp_path, path)

    @staticmethod
    def _serialize_record(record: BaseRecord) -> dict[str, Any]:
        if hasattr(record, "model_dump"):
            return record.model_dump(mode="json")
        return record.dict()

    @staticmethod
    def _record_id(record: BaseRecord) -> str | None:
        value = getattr(record, "id", None)
        return None if value is None else str(value)

    @staticmethod
    def _is_deleted(record: BaseRecord) -> bool:
        return bool(getattr(record, "is_deleted", False))

    @staticmethod
    def _with_id(record: BaseRecord, record_id: str) -> BaseRecord:
        if hasattr(record, "model_copy"):
            return record.model_copy(update={"id": record_id})
        return record.copy(update={"id": record_id})

    @staticmethod
    def _with_deleted(record: BaseRecord) -> BaseRecord:
        if hasattr(record, "model_copy"):
            return record.model_copy(update={"is_deleted": True})
        return record.copy(update={"is_deleted": True})

    @staticmethod
    def _deduplicate(records: list[BaseRecord]) -> list[BaseRecord]:
        positions: dict[str, int] = {}
        result: list[BaseRecord] = []
        for record in records:
            record_id = FlatFileStorage._record_id(record)
            if record_id is None:
                result.append(record)
                continue
            if record_id in positions:
                result[positions[record_id]] = record
            else:
                positions[record_id] = len(result)
                result.append(record)
        return result

    @staticmethod
    def _value_matches(actual: Any, expected_values: list) -> bool:
        if actual in expected_values:
            return True
        if actual is None:
            return False
        return str(actual) in {str(value) for value in expected_values}

    @staticmethod
    def _matches_filters(record: BaseRecord, filters: Filters) -> bool:
        for field_name, expected_values in filters.items():
            if not isinstance(expected_values, list):
                raise TypeError("filter values must be lists")
            actual = getattr(record, field_name, None)
            if isinstance(actual, (list, tuple, set)):
                if not any(
                    FlatFileStorage._value_matches(value, expected_values)
                    for value in actual
                ):
                    return False
            elif not FlatFileStorage._value_matches(actual, expected_values):
                return False
        return True

    @staticmethod
    def _sort_records(
        records: list[BaseRecord], sort_by: str, ascending: bool
    ) -> list[BaseRecord]:
        try:
            def sort_key(record: BaseRecord) -> Any:
                value = getattr(record, sort_by)
                if value is None:
                    return 0 if isinstance(sort_by, str) else None
                return value
            return sorted(records, key=sort_key, reverse=not ascending)
        except TypeError as exc:
            raise TypeError(f"records cannot be sorted by field: {sort_by}") from exc

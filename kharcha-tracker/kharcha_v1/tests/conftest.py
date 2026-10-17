"""
kharcha_v1/tests/conftest.py
Fixture providing FlatFileStorage backed by a temporary directory.
"""
from __future__ import annotations

import pytest

from kharcha_v1.kharcha_gateway.kharcha_service.storage_service.flat_file import (
    FlatFileStorage,
)
from kharcha_v1.kharcha_gateway.kharcha_service.storage_service.schema.audit_log import (
    AuditLog,
)
from kharcha_v1.kharcha_gateway.kharcha_service.storage_service.schema.group_record import (
    GroupRecord,
)
from kharcha_v1.kharcha_gateway.kharcha_service.storage_service.schema.user_record import (
    UserRecord,
)

APPLICATION_SCHEMA_REGISTRY = {
    "user_record": UserRecord,
    "group_record": GroupRecord,
    "audit_log": AuditLog,
}


@pytest.fixture
def storage(tmp_path) -> FlatFileStorage:
    """Provides an isolated FlatFileStorage instance for each test."""
    db_dir = tmp_path / "flat_file_db"
    return FlatFileStorage(db_path=db_dir, schema_registry=APPLICATION_SCHEMA_REGISTRY)
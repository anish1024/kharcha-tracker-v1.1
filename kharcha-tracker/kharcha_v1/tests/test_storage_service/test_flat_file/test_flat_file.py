"""Complete unit coverage for storage_service/flat_file.py."""

from datetime import datetime, timezone
import json
from pathlib import Path
from uuid import UUID, uuid4
#from uuid_extensions import uuid7

import pytest
from pydantic import Field

from kharcha_v1.kharcha_gateway.kharcha_service.storage_service.flat_file import (
    FlatFileStorage,
)
from kharcha_v1.kharcha_gateway.kharcha_service.storage_service.schema import (
    BaseRecord,
    UserRecord,
    GroupRecord,
    ExpenseRecord,
)
from kharcha_v1.kharcha_gateway.kharcha_service.storage_service.storage_interface import (
    FilterValue,
    Filters,
    StorageInterface,
)

class NotARecord:
    pass


def make_group(**kwargs):
    required = {
        "name": kwargs.get("name", "TestGroup"),
        "members": kwargs.get("members", []),
        "status": kwargs.get("status", "OPEN"),
        "currency": kwargs.get("currency", "INR"),
        "id": kwargs.get("id"),
    }
    optional = {
        "invite_code": kwargs.get("invite_code"),
        "created_by": kwargs.get("created_by"),
        "created_at": kwargs.get("created_at", datetime.now(timezone.utc)),
        "modified_by": kwargs.get("modified_by"),
        "modified_at": kwargs.get("modified_at"),
    }
    required.update(optional)
    return GroupRecord(**required)


@pytest.fixture
def registry():
    return {"test_user": UserRecord, "test_expense": ExpenseRecord, "test_group": GroupRecord}


@pytest.fixture
def storage(tmp_path, registry):
    return FlatFileStorage(tmp_path, schema_registry=registry)


def make_user(**kwargs):
    required = {
        "name": kwargs.get("name", "TestUser"),
        "password": kwargs.get("password", "testpass123"),
        "email": kwargs.get("email", "test@example.com"),
        "id": kwargs.get("id"),
        "is_active": kwargs.get("is_active", True),
    }
    optional = {
        "avatar_url": kwargs.get("avatar_url"),
        "created_by": kwargs.get("created_by"),
        "created_at": kwargs.get("created_at", datetime.now(timezone.utc)),
        "modified_by": kwargs.get("modified_by"),
        "modified_at": kwargs.get("modified_at"),
    }
    required.update(optional)
    return UserRecord(**required)
    optional = {
        "avatar_url": kwargs.get("avatar_url"),
        "created_by": kwargs.get("created_by"),
        "created_at": kwargs.get("created_at", datetime.now(timezone.utc)),
        "modified_by": kwargs.get("modified_by"),
        "modified_at": kwargs.get("modified_at"),
    }
    required.update(optional)
    return UserRecord(**required)


def make_expense(**kwargs):
    required = {
        "group_id": kwargs.get("group_id", uuid4()),
        "date_time": kwargs.get("date_time", datetime.now(timezone.utc)),
        "expn_ctgry": kwargs.get("expn_ctgry", "Food"),
        "expn_desc": kwargs.get("expn_desc", "Test expense"),
        "expn_amnt": kwargs.get("expn_amnt", 100.0),
        "id": kwargs.get("id"),
        "expn_splt": kwargs.get("expn_splt", []),
        "paid_by": kwargs.get("paid_by", []),
        "status": kwargs.get("status", "Open"),
    }
    optional = {
        "expn_bill_screenshot": kwargs.get("expn_bill_screenshot"),
        "expn_payment_screenshot": kwargs.get("expn_payment_screenshot"),
        "created_by": kwargs.get("created_by"),
        "created_at": kwargs.get("created_at", datetime.now(timezone.utc)),
        "modified_by": kwargs.get("modified_by"),
        "modified_at": kwargs.get("modified_at"),
    }
    required.update(optional)
    return ExpenseRecord(**required)


def test_interface_cannot_be_instantiated():
    with pytest.raises(TypeError):
        StorageInterface()


def test_interface_has_required_methods():
    assert all(
        hasattr(StorageInterface, name)
        for name in ("save_record", "get_record", "list_records", "delete_record")
    )


def test_filter_type_contract():
    filters: Filters = {"name": ["alice"], "age": [20, 30]}
    value: FilterValue = filters["age"]
    assert value == [20, 30]


def test_creates_db_directory(tmp_path, registry):
    db = tmp_path / "nested" / "flat_file_db"
    FlatFileStorage(db, schema_registry=registry)
    assert db.is_dir()


def test_default_database_path_is_under_storage_service(monkeypatch, registry):
    storage = FlatFileStorage(schema_registry=registry)
    assert storage.db_path.name == "flat_file_db"
    assert storage.db_path.parent.name == "storage_service"


def test_empty_db_starts_empty(storage):
    assert storage.list_records("test_user") == []


def test_register_valid_base_record_schema(tmp_path):
    storage = FlatFileStorage(tmp_path)
    storage.register_schema("test_user", UserRecord)
    assert storage.schema_registry["test_user"] is UserRecord


def test_reject_non_base_record_schema(tmp_path):
    storage = FlatFileStorage(tmp_path)
    with pytest.raises(TypeError):
        storage.register_schema("bad", NotARecord)


def test_reject_empty_schema(tmp_path):
    storage = FlatFileStorage(tmp_path)
    with pytest.raises(ValueError):
        storage.register_schema("", UserRecord)


def test_unknown_schema_rejected(storage):
    with pytest.raises(KeyError):
        storage.list_records("unknown")
    with pytest.raises(KeyError):
        storage.get_record("1", "unknown")
    with pytest.raises(KeyError):
        storage.save_record(make_user(), "unknown")
    with pytest.raises(KeyError):
        storage.delete_record(make_user(id=str(uuid4())), "unknown")


def test_multiple_schemas_coexist(storage):
    user = storage.save_record(make_user(name="Alice"), "test_user")
    expense = storage.save_record(make_expense(expn_desc="Lunch"), "test_expense")
    assert isinstance(user, UserRecord)
    assert isinstance(expense, ExpenseRecord)
    assert len(storage.list_records("test_user")) == 1
    assert len(storage.list_records("test_expense")) == 1


def test_save_record(storage):
    record = storage.save_record(make_user(name="Alice"), "test_user")
    assert record.id is not None
    assert storage.get_record(str(record.id), "test_user") == record


def test_generate_uuid_when_id_missing(storage):
    record = storage.save_record(make_user(name="Alice"), "test_user")
    assert UUID(str(record.id))


def test_update_existing_record(storage):
    record_id = str(uuid4())
    first = storage.save_record(make_user(id=record_id, name="Alice"), "test_user")
    updated = storage.save_record(make_user(id=record_id, name="Alicia"), "test_user")
    records = storage.list_records("test_user")
    assert updated.id == first.id
    assert updated.name == "Alicia"
    assert len(records) == 1


def test_different_ids_create_separate_records(storage):
    storage.save_record(make_user(id=str(uuid4()), name="Alice"), "test_user")
    storage.save_record(make_user(id=str(uuid4()), name="Bob"), "test_user")
    assert len(storage.list_records("test_user")) == 2


def test_wrong_schema_type_rejected(storage):
    with pytest.raises(TypeError):
        storage.save_record(make_expense(), "test_user")
    with pytest.raises(TypeError):
        storage.save_record(make_user(), "test_group")


def test_non_base_record_rejected(storage):
    with pytest.raises(TypeError):
        storage.save_record(NotARecord(), "test_user")


def test_get_existing_record(storage):
    saved = storage.save_record(make_user(name="Alice"), "test_user")
    loaded = storage.get_record(str(saved.id), "test_user")
    assert loaded is not None
    assert loaded.name == "Alice"


def test_get_missing_record(storage):
    assert storage.get_record(str(uuid4()), "test_user") is None


def test_string_uuid_lookup_works(storage):
    record_id = uuid4()
    storage.save_record(make_user(id=record_id, name="Alice"), "test_user")
    assert storage.get_record(str(record_id), "test_user") is not None


def test_soft_deleted_record_invisible(storage):
    saved = storage.save_record(make_user(name="Alice"), "test_user")
    assert storage.delete_record(saved, "test_user") is True
    assert storage.get_record(str(saved.id), "test_user") is None


def test_get_does_not_create_audit_entry(storage):
    saved = storage.save_record(make_user(name="Alice"), "test_user")
    before = storage.list_records("test_expense")
    storage.get_record(str(saved.id), "test_user")
    after = storage.list_records("test_expense")
    assert before == after == []


def test_list_all(storage):
    storage.save_record(make_user(name="Alice"), "test_user")
    storage.save_record(make_user(name="Bob"), "test_user")
    assert len(storage.list_records("test_user")) == 2


def test_multiple_schemas_are_isolated(storage):
    user_id = str(uuid4())
    storage.save_record(make_user(id=user_id, name="Alice"), "test_user")
    storage.save_record(make_expense(id=user_id, expn_desc="Lunch"), "test_expense")
    assert storage.get_record(user_id, "test_user").name == "Alice"
    assert storage.get_record(user_id, "test_expense").expn_desc == "Lunch"


def test_string_filter(storage):
    storage.save_record(make_user(name="Alice"), "test_user")
    storage.save_record(make_user(name="Bob"), "test_user")
    result = storage.list_records("test_user", filters={"name": ["Alice"]})
    assert [r.name for r in result] == ["Alice"]


def test_filter_multiple_values_with_or_semantics(storage):
    for name in ("Alice", "Bob", "Carol"):
        storage.save_record(make_user(name=name), "test_user")
    result = storage.list_records("test_user", filters={"name": ["Alice", "Carol"]})
    assert [r.name for r in result] == ["Alice", "Carol"]


def test_integer_filter(storage):
    storage.save_record(make_user(name="A", email="a@example.com"), "test_user")
    storage.save_record(make_user(name="B", email="b@example.com"), "test_user")
    result = storage.list_records("test_user", filters={"email": ["b@example.com"]})
    assert [r.name for r in result] == ["B"]


def test_multiple_filter_fields_use_and_semantics(storage):
    storage.save_record(make_user(name="Alice", email="alice@example.com"), "test_user")
    storage.save_record(make_user(name="Alice", email="alice2@example.com"), "test_user")
    storage.save_record(make_user(name="Bob", email="bob@example.com"), "test_user")
    result = storage.list_records(
        "test_user", filters={"name": ["Alice"], "email": ["alice2@example.com"]}
    )
    assert len(result) == 1
    assert result[0].name == "Alice"
    assert result[0].email == "alice2@example.com"


def test_list_valued_field_overlap_filter(storage):
    member = uuid4()
    storage.save_record(make_group(name="A", members=[member]), "test_group")
    storage.save_record(make_group(name="B", members=[uuid4()]), "test_group")
    result = storage.list_records("test_group", filters={"members": [member]})
    assert [r.name for r in result] == ["A"]


def test_no_filter(storage):
    storage.save_record(make_user(name="A"), "test_user")
    assert len(storage.list_records("test_user", filters=None)) == 1


def test_empty_filter(storage):
    storage.save_record(make_user(name="A"), "test_user")
    assert len(storage.list_records("test_user", filters={})) == 1


def test_ascending_sort(storage):
    for email in ("a@example.com", "b@example.com", "c@example.com"):
        storage.save_record(make_user(email=email), "test_user")
    assert [r.email for r in storage.list_records("test_user", sort_by="email")] == ["a@example.com", "b@example.com", "c@example.com"]


def test_descending_sort(storage):
    for email in ("c@example.com", "a@example.com", "b@example.com"):
        storage.save_record(make_user(email=email), "test_user")
    assert [r.email for r in storage.list_records("test_user", sort_by="email", ascending=False)] == ["c@example.com", "b@example.com", "a@example.com"]


def test_string_sort(storage):
    for name in ("Charlie", "Alice", "Bob"):
        storage.save_record(make_user(name=name), "test_user")
    assert [r.name for r in storage.list_records("test_user", sort_by="name")] == ["Alice", "Bob", "Charlie"]


def test_invalid_sort_field(storage):
    storage.save_record(make_user(name="A"), "test_user")
    with pytest.raises(AttributeError):
        storage.list_records("test_user", sort_by="missing")


def test_incompatible_sort_raises(storage):
    first = make_user(name="A")
    second = make_user(name="B")
    first.email = "a"
    second.email = 1  # Intentionally incompatible type for sorting
    storage.save_record(first, "test_user")
    storage.save_record(second, "test_user")
    with pytest.raises(TypeError):
        storage.list_records("test_user", sort_by="email")


def test_first_page(storage):
    for name in ["david", "alice", "bob", "charlie"]:
        storage.save_record(make_user(name=name), "test_user")
    result = storage.list_records("test_user", sort_by="name", limit=2, offset=0)
    assert [r.name for r in result] == ["alice", "bob"]


def test_second_page(storage):
    for name in ["david", "alice", "bob", "charlie"]:
        storage.save_record(make_user(name=name), "test_user")
    result = storage.list_records("test_user", sort_by="name", limit=2, offset=2)
    assert [r.name for r in result] == ["charlie", "david"]


def test_offset_without_limit(storage):
    for name in ("Alice", "Bob", "Charlie", "David"):
        storage.save_record(make_user(name=name), "test_user")
    result = storage.list_records("test_user", sort_by="name", offset=2)
    assert [r.name for r in result] == ["Charlie", "David"]


def test_offset_beyond_set(storage):
    storage.save_record(make_user(name="Alice"), "test_user")
    assert storage.list_records("test_user", offset=10) == []


def test_limit_zero(storage):
    storage.save_record(make_user(name="Alice"), "test_user")
    assert storage.list_records("test_user", limit=0) == []


def test_negative_offset_rejected(storage):
    with pytest.raises(ValueError):
        storage.list_records("test_user", offset=-1)


def test_negative_limit_rejected(storage):
    with pytest.raises(ValueError):
        storage.list_records("test_user", limit=-1)


def test_filter_before_pagination(storage):
    for name in ("Alice", "Bob", "Charlie", "David"):
        storage.save_record(make_user(name=name), "test_user")
    result = storage.list_records("test_user", filters={"name": ["Bob"]}, limit=1, offset=0)
    assert len(result) == 1
    assert result[0].name == "Bob"


def test_soft_delete(storage):
    saved = storage.save_record(make_user(name="Alice"), "test_user")
    assert storage.delete_record(saved, "test_user") is True
    raw = (storage.db_path / "test_user.json").read_text(encoding="utf-8")
    assert '"is_deleted": true' in raw


def test_deleted_record_excluded_from_get_and_list(storage):
    saved = storage.save_record(make_user(name="Alice"), "test_user")
    storage.delete_record(saved, "test_user")
    assert storage.get_record(str(saved.id), "test_user") is None
    assert storage.list_records("test_user") == []


def test_delete_already_deleted_returns_false(storage):
    saved = storage.save_record(make_user(name="Alice"), "test_user")
    assert storage.delete_record(saved, "test_user") is True
    assert storage.delete_record(saved, "test_user") is False


def test_delete_nonexistent_returns_false(storage):
    assert storage.delete_record(make_user(id=str(uuid4())), "test_user") is False


def test_delete_without_id_returns_false(storage):
    assert storage.delete_record(make_user(), "test_user") is False


def test_delete_persists_across_storage_instances(tmp_path, registry):
    first = FlatFileStorage(tmp_path, schema_registry=registry)
    saved = first.save_record(make_user(name="Alice"), "test_user")
    assert first.delete_record(saved, "test_user") is True
    second = FlatFileStorage(tmp_path, schema_registry=registry)
    assert second.get_record(str(saved.id), "test_user") is None
    assert second.list_records("test_user") == []


def test_json_file_created(storage):
    storage.save_record(make_user(name="Alice"), "test_user")
    assert (storage.db_path / "test_user.json").exists()


def test_reload_from_disk(tmp_path, registry):
    first = FlatFileStorage(tmp_path, schema_registry=registry)
    saved = first.save_record(make_user(name="Alice"), "test_user")
    second = FlatFileStorage(tmp_path, schema_registry=registry)
    loaded = second.get_record(str(saved.id), "test_user")
    assert loaded is not None
    assert loaded.name == "Alice"


def test_json_structure_is_array(storage):
    storage.save_record(make_user(name="Alice"), "test_user")
    payload = json.loads((storage.db_path / "test_user.json").read_text(encoding="utf-8"))
    assert isinstance(payload, list)
    assert isinstance(payload[0], dict)


def test_datetime_serialization(storage):
    now = datetime.now(timezone.utc)
    saved = storage.save_record(make_user(created_at=now), "test_user")
    second = FlatFileStorage(storage.db_path, schema_registry={"test_user": UserRecord})
    loaded = second.get_record(str(saved.id), "test_user")
    assert loaded is not None
    assert loaded.created_at == now


def test_duplicate_id_keeps_last_record(tmp_path):
    path = tmp_path / "test_user.json"
    record_id = str(uuid4())
    path.write_text(
        '[{"id":"%s","name":"old","password":"pw","email":"old@example.com"},'
        '{"id":"%s","name":"new","password":"pw","email":"new@example.com"}]'
        % (record_id, record_id),
        encoding="utf-8",
    )
    storage = FlatFileStorage(tmp_path, schema_registry={"test_user": UserRecord})
    records = storage.list_records("test_user")
    assert len(records) == 1
    assert records[0].name == "new"


def test_same_id_across_schemas_does_not_collide(storage):
    record_id = str(uuid4())
    storage.save_record(make_user(id=record_id, name="Alice"), "test_user")
    storage.save_record(make_expense(id=record_id, expn_desc="Lunch"), "test_expense")
    assert len(storage.list_records("test_user")) == 1
    assert len(storage.list_records("test_expense")) == 1


def test_valid_registry_json_loads(tmp_path):
    registry = tmp_path / "schema_registry.json"
    registry.write_text(
        '{"test_user":{"module":"kharcha_v1.tests.test_storage_service.test_flat_file.test_flat_file","class":"UserRecord"}}',
        encoding="utf-8",
    )
    storage = FlatFileStorage(tmp_path)
    assert storage.schema_registry["test_user"].__name__ == "UserRecord"


def test_invalid_registry_root(tmp_path):
    (tmp_path / "schema_registry.json").write_text("[]", encoding="utf-8")
    with pytest.raises(ValueError):
        FlatFileStorage(tmp_path)


def test_registry_missing_module(tmp_path):
    (tmp_path / "schema_registry.json").write_text(
        '{"test_user":{"class":"UserRecord"}}', encoding="utf-8"
    )
    with pytest.raises(ValueError):
        FlatFileStorage(tmp_path)


def test_registry_missing_class(tmp_path):
    (tmp_path / "schema_registry.json").write_text(
        '{"test_user":{"module":"kharcha_v1.tests.test_storage_service.test_flat_file.test_flat_file"}}', encoding="utf-8"
    )
    with pytest.raises(ValueError):
        FlatFileStorage(tmp_path)


def test_registry_non_base_record_class_rejected(tmp_path):
    (tmp_path / "schema_registry.json").write_text(
        '{"bad":{"module":"kharcha_v1.tests.test_storage_service.test_flat_file.test_flat_file","class":"NotARecord"}}',
        encoding="utf-8",
    )
    with pytest.raises(TypeError):
        FlatFileStorage(tmp_path)


def test_schema_agnostic_with_multiple_base_record_subclasses(storage):
    user = storage.save_record(make_user(name="Alice"), "test_user")
    expense = storage.save_record(make_expense(expn_desc="Lunch"), "test_expense")
    loaded_user = storage.get_record(str(user.id), "test_user")
    loaded_expense = storage.get_record(str(expense.id), "test_expense")
    assert isinstance(loaded_user, UserRecord)
    assert isinstance(loaded_expense, ExpenseRecord)


def test_flat_file_has_no_concrete_application_schema_imports():
    # From kharcha_v1/tests/test_storage_service/test_flat_file/test_flat_file.py -> kharcha_gateway/kharcha_service/storage_service/flat_file.py
    source = (
        Path(__file__).resolve().parents[3]
        / "kharcha_gateway"
        / "kharcha_service"
        / "storage_service"
        / "flat_file.py"
    )
    text = source.read_text(encoding="utf-8")
    forbidden = (
        ".schema.group_record",
        ".schema.user_record",
        ".schema.expense_record",
        ".schema.payout_record",
        ".schema.settlement_round_record",
        ".schema.notification_record",
        ".schema.audit_log",
    )
    assert not any(item in text for item in forbidden)


def test_malformed_json_table_is_rejected(tmp_path):
    (tmp_path / "test_user.json").write_text("{not-json", encoding="utf-8")
    with pytest.raises(ValueError, match="invalid JSON table"):
        FlatFileStorage(tmp_path, schema_registry={"test_user": UserRecord})


def test_non_array_json_table_is_rejected(tmp_path):
    (tmp_path / "test_user.json").write_text("{}", encoding="utf-8")
    with pytest.raises(ValueError, match="JSON array"):
        FlatFileStorage(tmp_path, schema_registry={"test_user": UserRecord})


def test_invalid_record_json_is_rejected(tmp_path):
    (tmp_path / "test_user.json").write_text(
        '[{"id":"abc","age":"not-an-int"}]', encoding="utf-8"
    )
    with pytest.raises(ValueError, match="invalid record"):
        FlatFileStorage(tmp_path, schema_registry={"test_user": UserRecord})


def test_record_without_id_can_be_saved_and_gets_id(storage):
    saved = storage.save_record(make_user(name="Alice"), "test_user")
    assert saved.id is not None
    assert storage.get_record(str(saved.id), "test_user") is not None


def test_nested_values_round_trip(tmp_path):
    registry = {"test_expense": ExpenseRecord}
    storage = FlatFileStorage(tmp_path, schema_registry=registry)
    user_id = uuid4()
    saved = storage.save_record(
        make_expense(expn_desc="Dinner", expn_splt=[{"user_id": user_id, "percentage": 100.0}]),
        "test_expense",
    )
    reloaded = FlatFileStorage(tmp_path, schema_registry=registry)
    loaded = reloaded.get_record(str(saved.id), "test_expense")
    assert loaded is not None
    assert [share.user_id for share in loaded.expn_splt] == [user_id]


def test_filter_values_must_be_lists(storage):
    storage.save_record(make_user(name="Alice"), "test_user")
    with pytest.raises(TypeError):
        storage.list_records("test_user", filters={"name": "Alice"})
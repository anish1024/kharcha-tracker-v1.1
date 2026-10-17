# Architecture & Storage Guide: Pydantic DTOs & Database Agnosticism

## 1. Pydantic DTO Conventions & Concepts

Every data-transfer and schema record under `kharcha_v1/kharcha_gateway/kharcha_service/storage_service/schema` is built using **Pydantic** (`BaseModel`, `Field`) alongside standard Python typing primitives (`Optional`, `List`, `UUID`, `datetime`).

Pydantic acts as a strict parsing and data validation layer: it guarantees that any dictionary passing into or out of the persistence layer matches the declared schema types before reaching business logic or disk.

---

### What Does `Optional` Mean?

In Python type annotations:

* `name: str` represents a mandatory string that cannot be `None`.
* `avatar_url: Optional[str]` (equivalent to `str | None`) allows the value to be a valid string or `None`.

#### Does `Optional` directly reflect the database schema?

**Yes, directly.**

* **Non-Optional** (`name: str`) $\rightarrow$ Maps to a **`NOT NULL`** column in a relational table (or a required key in JSON/document stores).
* **`Optional[...]`** (`avatar_url: Optional[str]`) $\rightarrow$ Maps to a **`NULLABLE`** column. If omitted or null, Python accepts `None`.

---

### What Does `Field(...)` Do?

`Field` specifies metadata, validation constraints, and default behaviors for model attributes:

```python
name: str = Field(..., min_length=1, max_length=100, description="Group name")

```

* **The Ellipsis (`...`)**: The first positional argument indicates that the field is **strictly required**.
* **`default=None` / `default=False**`: Marks the field as optional on input; Pydantic populates the default value automatically if not provided.
* **`default_factory=list`**: Prevents mutable default bugs in Python by generating a new list instance for each record (e.g., `members: List[UUID] = Field(default_factory=list)`).
* **Validation Rules** (`min_length=1`, `pattern=r"^[A-Z]{3}$"`): Enforces constraints equivalent to SQL column checks (e.g., `VARCHAR(100)` or regex patterns) before records are written.

---

### Why is `id` Declared as `Optional` When It Is the Primary Key?

Declaring primary keys as `Optional[UUID] = None` resolves the entity lifecycle problem across creation and persistence:

1. **Pre-Save Lifecycle State**:
* When creating a new entity (e.g., during signup or group creation), the primary key does not yet exist.
* Generating IDs is delegated to the persistence layer (`FlatFileStorage`, SQLite, or PostgreSQL) upon executing `save_record()`.




2. **Storage Layer ID Assignment**:
`FlatFileStorage` inspects the record and assigns a new UUID if missing:


```python
record_id = self._record_id(record)
if not record_id:
    record_id = str(uuid4())  # Storage auto-generates ID on insert
    record = self._with_id(record, record_id)

```



If `id` were defined as `id: UUID = Field(...)`, every service or UI caller would be forced to generate IDs manually before saving. Setting `id: Optional[UUID] = None` allows:

```python
new_user = UserRecord(name="Alice", email="alice@test.com", password="hashed_pw")

```

3. **Blueprint Alignment**:
`kharcha-tracker.yaml` explicitly documents this behavior:


```yaml
- name: id
  type:
    type: optional
    of:
      type: uuid
  required: false
  notes: Primary key - auto-generated if null (read-only)

```



---

### Summary of Schema Conventions

| Code Pattern | Table Schema Mapping | Architectural Purpose |
| --- | --- | --- |
| `id: Optional[UUID] = None` | Primary Key (`UUID`)

 | Allows creating new unsaved records without an ID; storage assigns it upon save.

 |
| `name: str = Field(...)` | `NOT NULL VARCHAR` | Mandatory value; instantiation fails if omitted or `None`. |
| `avatar_url: Optional[str] = None` | `NULLABLE VARCHAR` | Optional attribute; defaults safely to `None`. |
| `is_deleted: bool = False` | `BOOLEAN NOT NULL DEFAULT FALSE`<br> | Soft-delete flag; records start active.

 |
| `created_at: Optional[datetime] = None` | `TIMESTAMP NULLABLE / DEFAULT NOW()`<br> | System timestamp stamped on creation.

 |

---

## 2. Storage Abstraction & Database Agnosticism

The Pydantic record models under `storage_service/schema` will hold without modification if switching between **flat file**, **SQLite**, and **PostgreSQL**.

### The Decoupled Architecture

In `kharcha-tracker.yaml`, `storage_interface` is defined as a contract that accepts and returns any record inheriting from `base_record`:

```yaml
id: storage_interface
notes: >-
  The 'storage_interface' contract must be schema agnostic and accepts any
  schema type derived from below defined base_record.

```

Application services (`auth_service`, `group_service`, `expense_service`) never communicate with database drivers or file paths directly. They rely purely on the abstract methods defined in `StorageInterface`:

```
[ Application / Services Layer ]
  (auth_service, group_service, expense_service)
                 │
                 │ Calls: save_record(record, schema), get_record(id, schema)
                 ▼
      [ StorageInterface ] (Contract)
                 │
   ┌─────────────┼─────────────┐
   ▼             ▼             ▼
[FlatFile]   [SQLite]     [PostgreSQL]
(JSON files) (SQL file)   (Remote DB)

```

All storage engines implement the same interface methods:

* `save_record(record: BaseRecord, schema: str) -> BaseRecord`

* `get_record(record_id: str, schema: str) -> Optional[BaseRecord]`

* `list_records(schema: str, filters: Optional[Filters], ...) -> list[BaseRecord]`

* `delete_record(record: BaseRecord, schema: str) -> bool`


Switching the active backend is controlled by the environment variable:

```bash
KHARCHA_STORAGE=flat_file|sql_lite|postgres

```

---

### Type Mapping Across Storage Engines

Because Pydantic uses standard Python primitives, the record properties map cleanly across JSON and relational SQL dialects:

| Pydantic Field Type | Flat-File JSON Representation | SQLite Column Type | PostgreSQL Column Type |
| --- | --- | --- | --- |
| `UUID` | String (`"c1a2..."`) | `TEXT` | `UUID` (or `VARCHAR(36)`) |
| `str` | String (`"Alice"`) | `TEXT` | `VARCHAR` or `TEXT` |
| `int` | Number (`42`) | `INTEGER` | `INTEGER` / `BIGINT` |
| `float` | Number (`10.50`) | `REAL` | `DOUBLE PRECISION` or `NUMERIC` |
| `bool` | Boolean (`true`/`false`) | `INTEGER` (`0` or `1`) | `BOOLEAN` |
| `datetime` | ISO 8601 String | `TEXT` (ISO format) | `TIMESTAMPTZ` |
| `List[...]` (nested) | JSON Array | `TEXT` (JSON encoded) | `JSONB` or `TEXT` |

---

### Implementation Details for SQLite & PostgreSQL

While the Pydantic schema models remain identical, `sql_lite.py` and `postgres.py` handle dialect translation as follows:

1. **Complex Nested Structures (`paid_by`, `expn_splt`, `members`)**:
* **SQLite**: Does not have a native array type. The `sql_lite.py` adapter serializes complex fields to JSON strings (`json.dumps(...)`) on write, and relies on Pydantic to deserialize them on read.


* **PostgreSQL**: Supports native `JSONB` or array types (`UUID[]`). Storing them as JSON strings or `JSONB` works directly using Pydantic’s `model_dump(mode="json")` and `model_validate(...)`.




2. **Deserialization Pattern**:
Relational adapters follow the same deserialization pattern used in `FlatFileStorage`:


```python
# Raw row fetched from SQLite or PostgreSQL cursor as a dict
raw_row = dict(cursor.fetchone())

record_type = self._schema_registry[schema]
record = record_type.model_validate(raw_row)

```



Pydantic parses string UUIDs back into `UUID` objects, ISO strings into `datetime` objects, and JSON strings/dicts into nested DTOs automatically.

---

### Stability of Pydantic DTOs

* The Pydantic records represent the **canonical shape of application data**.


* Database-specific logic (SQL queries, migrations, table definitions, connection pooling) remains encapsulated inside the specific storage engine modules (`flat_file.py`, `sql_lite.py`, `postgres.py`).


* As long as each adapter fulfills `StorageInterface`, the schema models remain reusable across all backends.
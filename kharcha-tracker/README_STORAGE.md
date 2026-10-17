# Kharcha Tracker — Storage Service Implementation

This package contains the YAML-derived storage implementation and its complete unit-test suite.

## YAML-defined test location

```text
kharcha_v1/
└── kharcha_gateway/
    └── kharcha_service/
        ├── storage_service/
        │   ├── schema/
        │   │   └── base_record.py
        │   ├── storage_interface.py
        │   └── flat_file.py
        └── tests/
            └── test_storage_service/
                └── test_flat_file/
                    └── test_flat_file.py
```

The `tests` folder is a sibling of `storage_service`, exactly as defined by the latest blueprint. The `test_flat_file` node is a folder and is required to contain all unit tests for `storage_service/flat_file.py`.

## Included implementation

- `storage_interface.py` — abstract schema-agnostic contract.
- `flat_file.py` — generic JSON flat-file implementation. It imports only `BaseRecord` and the storage contract; concrete application schema classes are not hard-coded.
- `schema/base_record.py` — the common BaseRecord dependency required by the two storage modules. The blueprint defines the common fields but does not define a common `id`, so test/application record types provide their own `id` field.
- `test_flat_file/test_flat_file.py` — 69 pytest unit tests covering the current contract.
- `pytest.ini` — test discovery and package path configuration.

## Covered behavior

- abstract storage contract
- BaseRecord schema registration and validation
- schema isolation and schema agnosticism
- UUID generation when a record has no ID
- save/update/get/delete
- soft deletion
- persistence and reload
- JSON-array storage format
- datetime serialization
- deduplication by record ID, retaining the newest/last record
- string and integer filters
- multi-value OR filter semantics
- multiple-filter AND semantics
- list-valued filter overlap
- sorting
- offset/limit pagination
- filter → sort → offset → limit ordering
- schema registry JSON loading and validation
- malformed JSON and invalid record handling
- regression protection against concrete application-schema imports
- nested list round-tripping

## Explicitly out of current contract scope

Per the current implementation agreement, these are not implemented or tested as storage-contract behavior:

- cascade delete
- schema-specific transaction IDs / auto-increment transaction ID generation
- audit-log creation from storage operations

The storage interface does not provide the relationship or actor context required to invent those behaviors without changing the contract.

## Run tests

From the extracted project root:

```bash
pytest -q
```

Expected result for this package version:

```text
69 passed
```

## Dependencies

The implementation uses Pydantic v2 for `BaseRecord` validation/model serialization and pytest for tests.

---
### YAML interpretation + the rules in AGENTS.md mismatch/gaps. Revised 22-point table

Find my responses under the Feedback column. If the 

| # | Area | Node(s) | Classification | Current YAML situation / decision required | Feedback |
|---:|---|---|---|---|---|
| **1** | Record identity / `id` | `base_record`, `storage_interface` | **Contract Gap** | `flat_file` relies on `record.id`, but `BaseRecord` does not explicitly define it. Need to decide whether `id: uuid` is part of the universal `BaseRecord` contract. | `base_record.id` can be added only if `id` can be either `int` or `string` |
| **2** | `save_record()` identity behavior | `storage_interface` | **Contract Gap** | Need to explicitly define missing-ID behavior, UUID generation, existing-ID behavior, and insert vs. update semantics. These can be expressed through existing `notes`, `contract.methods`, and type descriptors. | `insert` in case of missing `id` or `id` is NULL, else `update`. In case of `id` not found during update then throw `IdNotFoundException |
| **3** | `save_record()` timestamp behavior | `storage_interface`, `base_record` | **Contract Gap** | `created_at` / `modified_at` exist, but the contract does not clearly specify when they are generated or changed. | The class implementating the contract updates `created_at` / `modified_at` before saving to relevant schema |
| **4** | Soft-delete semantics | `storage_interface`, `flat_file` | **Contract Clarification** | `flat_file.notes` already describes soft delete. Need to make the intended visibility and repeated-delete behavior unambiguous. Existing `notes` can express this; no schema extension is necessarily required. | This is part of implementation  and not contract gap |
| **5** | Filter semantics | `storage_interface` | **Contract Gap** | Existing filter parameter types do not establish whether values within a field are OR'ed, fields are AND'ed, or list-valued fields use overlap. Semantics should be explicitly stated. | Values within field are OR’ed and fields are AND’ed |
| **6** | Sorting semantics | `storage_interface` | **Contract Gap** | `sort_by` and `ascending` exist, but default ordering, invalid field behavior, `None` handling, and operation ordering are not fully specified. | `sort_by` must be ‘ascending’ by default. In case the `sort_by` field does not exist then throw ‘InvalidColumnError’. If `sort_by` is `None`, NULL or empty then `sort_by` must use `base_record`.`id` |
| **7** | Pagination semantics | `storage_interface` | **Contract Gap** | `limit` and `offset` exist, but minimum/zero/negative/out-of-range behavior and ordering of filter → sort → pagination need to be explicit. | ```text
pagination:
  offset:
    minimum: 0
  limit:
    minimum: 0
  pipeline:
    - filter
    - sort
    - offset
    - limit
```
|
| **8** | Schema resolution | `storage_interface`, `flat_file` | **Contract Gap** | Schema names and concrete `BaseRecord` types are used, but behavior for unknown schemas and registry resolution needs explicit definition. | |
| **9** | Schema/type compatibility | `storage_interface`, `flat_file` | **Contract Gap** | Need to define behavior when the supplied record is a `BaseRecord` but its concrete type does not match the registered type for the requested schema. | |
| **10** | Persistence / atomic write semantics | `flat_file` | **Schema Extension Required?** | YAML already states that writes immediately update memory and the relevant table. However, atomicity, rollback, and partial-write behavior are not represented by a dedicated schema field. We should **not invent an `atomicity` field**. Decide whether existing `notes` are sufficient or whether the graph schema itself should be extended. | |
| **11** | Deduplication semantics | `flat_file` | **Contract Clarification** | YAML already says duplicate `record.id` entries keep the "newest (last)" record. The exact meaning of "last" should be clarified, preferably without inventing a new YAML field. | |
| **12** | `schema_registry.json` structure | `flat_file` | **Contract Gap** | YAML requires a schema registry but does not establish its exact JSON structure. This can potentially be specified in `notes`; if a formal structured YAML representation is desired, that would require a schema extension. | |
| **13** | Error/result semantics | `storage_interface`, `flat_file` | **Contract Gap / Possible Schema Extension** | `flat_file.notes` says "success/failure with message", while the actual contract returns records/lists/boolean. Need to decide whether the intended contract is exceptions or result objects. A new result-wrapper concept cannot simply be invented because the YAML schema is closed. | |
| **14** | Audit logging | `storage_interface`, `flat_file` | **Schema Extension Required? / Contract Mismatch** | YAML describes audit requirements, but the generic `StorageInterface` has no actor/context information required to construct those audit records. We must decide whether audit is outside this contract, can be handled through existing mechanisms, or requires a deliberate schema/interface change. We must **not invent parameters or fields silently**. | |
| **15** | Cascade delete | `flat_file` | **Scope Conflict** | Existing YAML notes describe cascade delete, but you explicitly confirmed cascade delete is out of scope for the current contract. The YAML must be corrected so generation does not treat it as an implementation requirement. | |
| **16** | Schema-specific transaction IDs | `flat_file` | **Scope Conflict** | Existing YAML specifies auto-incrementing `uint32` transaction IDs, but you explicitly confirmed these are out of scope for the current contract. YAML must explicitly remove/de-scope that requirement. | |
| **17** | `FlatFileStorage` public class/API | `flat_file` | **Contract Gap** | The `class` node can formally describe its fields and `implements`, but the current YAML does not fully establish the public constructor/API of the implementation. Need to specify the intended class surface using the existing class/function mechanisms. | |
| **18** | Schema → filename mapping | `flat_file` | **Contract Clarification** | YAML already lists the table filenames. What is missing is an unambiguous mapping between schema names and those filenames. This can be documented in the node's existing `notes`; no new field should be invented. | |
| **19** | JSON storage structure | `flat_file` | **Contract Ambiguity** | The YAML's description of the JSON representation is internally ambiguous. Need one canonical representation. This can be clarified in `notes` unless you want a formal new schema construct. | |
| **20** | Master/transaction loading strategy | `flat_file` | **Implementation Gap** | YAML already specifies master tables as fully cached and transaction tables as lazy-loaded by `group_id`. Current implementation loads all records into memory. This is an implementation issue, not a missing contract requirement. | |
| **21** | 1000-record transaction paging | `flat_file` | **Implementation Gap** | YAML already specifies transaction pages of 1000. Current code defines `PAGE_SIZE = 1000` but does not implement the corresponding lazy/chunked loading strategy. | |
| **22** | Unit-test specification | `test_flat_file` | **Contract Gap** | `tests` is an explicitly supported node field containing `{id, description, given, expect | expect_raises}` cases. The current `test_flat_file` node has no test cases, so the 69 tests are not yet represented as YAML requirements. | |
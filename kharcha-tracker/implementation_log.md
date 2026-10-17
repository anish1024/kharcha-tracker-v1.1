# Implementation Log — Kharcha Tracker Frontend + User/Group Sprint

Companion to `./auth_group_implementation_plan.md`. One entry is appended per completed phase.

---

## Phase 0 — Baseline  ✅  (done)

**Goal:** Get a runnable, green test baseline before any feature work.

**Actions**
- Installed `httpx` (0.28.1) into the shared dev venv (`../.venv`, which already had fastapi 0.142.2, pydantic 2.13.5, pytest 9.1.1, uvicorn 0.54.0). This unblocks `fastapi.testclient.TestClient`.
- Ran the full suite: `python -m pytest -q` from the repo root.

**Result**
- **90 passed**, 67 warnings.
- Warnings are pre-existing `PydanticSerializationUnexpectedValue` notices in `test_flat_file.py` (string-vs-uuid round-trips); they are not failures and predate this sprint.
- No code changes made to application source in this phase.

**Baseline verified green.** Proceeding to Phase 1.

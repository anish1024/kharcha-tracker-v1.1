# Audit Log Screen — Implementation Plan

**Status: APPROVAL PENDING.** No code will be written until this plan is
approved. This document covers only the four requested enhancements (audit
log screen, activity-sorted group drop-down, date range, pagination + CSV
export).

Authoritative sources: `kharcha-tracker.yaml` (nodes `get_group_activity`,
`audit_log`, `list_groups_for_user`, `main`, `frontend_js`/`app_js`), the
closed-schema rules in `AGENTS.md`, and the current state documented in
`README.md` §7/§8. Where this plan restates the YAML it is summarising,
never extending; no new protocol, enum value, route path, or schema kind is
introduced.

---

## 1. Requirements (as given)

1. **Audit log screen** — a new screen showing all activity log entries for a
   selected group, in a table sorted by date/time descending (latest on top).
   The group drop-down lists only groups the logged-in user belongs to.
2. **Drop-down ordering** — groups ordered by latest activity first, least
   active last; the top item is auto-selected by default.
3. **Date range** — user-selectable date range to limit activity retrieval;
   controls placed next to the group drop-down.
4. **Table features** — pagination and CSV export.

UI must be mobile-friendly first, resizable for tablet and desktop.

## 2. Current state (what exists today)

**Backend**

- `GET /api/v1/groups/{group_id}/activity` already exists
  (`app/main.py:577`): membership-gated (non-member → 403 via
  `verify_membership`), accepts `limit` (1–1000, default 50) and `offset`,
  returns `List[AuditLog]` **sorted `created_at` DESC**
  (`group_service.get_group_activity` → `list_records(sort_by="created_at",
  ascending=False)`). Requirement 1's ordering and requirement 4's paging
  primitive therefore already exist.
- `GET /api/v1/groups` (`main.py:345`) returns the user's groups
  (`list_groups_for_user`, membership-filtered — requirement 1's "only my
  groups" is already guaranteed) in **storage order — no activity sort**.
- Audit rows are written **only by `group_service._record_audit`** for group
  events: create, join, add-member, leave, lock, unlock, delete. Fields
  written: `event`, `entity_type="group"`, `entity_id=<group_id>`,
  `action`, `old_value`/`new_value` text, `actor_user_id`, timestamps.
  **`actor_name` is not populated.** No expense/payout audit writes exist.
- `storage.list_records` supports equality (IN-list) filters + `sort_by` +
  `limit/offset` — **no date-range filtering**.
- The group list endpoint has **no sort parameter**.

**Frontend**

- Four nav items (Dashboard, Groups, Expenses, Settings); no `#/activity`
  route, no `<table>` anywhere in the SPA, no pagination widget, no CSV
  export, no date-range inputs anywhere.
- Reusable pieces: group-select pattern (`#expenses-group-select`), state +
  `routeHandlers` + `bindXScreen()` boot wiring (Phase E4/E5 pattern),
  `formatDate()`, `toLocalInputValue()`, `membersCache` (member id→name per
  group), `Icons.clock`, `.stat-card`/`.group-card`/card CSS, mobile-first
  breakpoints at 640 px / 1024 px.

**Tests**: suite baseline **228 passed**. One existing activity test
(`test_group_service.py::test_group_activity_logs`).

## 3. Design decisions & interpretations

| # | Decision | Rationale |
|---|---|---|
| D1 | Route `#/activity`, nav label **Activity**, nav icon **reuse `Icons.clock`** | No new icon file churn; clock = history. (Alternative: a dedicated `Icons.activity` pulse glyph — trivial if preferred.) |
| D2 | Page size fixed at **20 rows** | Keeps state/tests small; Prev/Next + "Page x of y" satisfies "pagination". Easy to add a size selector later. |
| D3 | **Date range sent as full ISO instants** (`date_from`/`date_to`, inclusive). The frontend converts the picked calendar days to *local* day-start/day-end → UTC ISO before sending; backend compares `created_at` (UTC) directly. `date_from > date_to` → 422. | No timezone ambiguity; plain HTML `<input type="date">` on the UI; comparison stays a pure service-layer filter (storage has no range filters). |
| D4 | Date filtering + pagination slicing happen **in the service layer** after fetching the group's audit rows (flat-file loads the table into memory anyway; audit volume is low) | No storage-interface/schema-kind change; `list_records` contract untouched. |
| D5 | Total count returned via **`X-Total-Count` response header** (new `count_group_activity` service function), keeping `response_model=List[AuditLog]` | Non-breaking; enables real "Page 2 of 7" UI. Changing the response body shape would break the existing contract. |
| D6 | Activity ordering computed **backend** via new optional `GET /groups?sort=activity` query param (`default` keeps current behaviour) | One extra query (`audit_log` filtered by the user's group ids, sorted desc, first-seen order; groups with no audit rows fall back to `time_created` DESC). Frontend N+1 (one `limit=1` activity call per group) was rejected: request storms, no atomicity. |
| D7 | Activity screen keeps its **own `activityGroups` array** (fetched with `sort=activity`), not `groupsCache` | Other screens' drop-downs keep their current ordering. |
| D8 | Actor column resolves `actor_user_id` → member name via `membersCache` (fetched for the selected group); unresolvable ids show short UUID (`xxxxxxxx…`) | Legacy rows have no `actor_name`. Going forward `_record_audit` will populate `actor_name` (best-effort) so new rows are self-describing and CSV carries the real name. |
| D9 | Details column prettifies the known `_record_audit` text patterns ("User joined {id}", "Added member {id}", "User {id} left group", create-name) against `membersCache`; unknown text passes through raw | Audit text contains raw UUIDs today; raw display would be noise. |
| D10 | CSV export is **client-side**: fetch *all* rows matching the current filter (chunked `limit=1000` loops up to `X-Total-Count`, hard cap 10 000 rows with a toast if clipped), build CSV, download via Blob | No new endpoint; export covers every filtered row, not just the visible page. RFC4180 quoting + UTF-8 BOM (Excel-safe). |
| D11 | Mobile-first table: semantic `<table>`; **below 640 px rows render as stacked cards** (CSS `data-label` technique), ≥640 px a real 4-column table | Requirement is "table"; horizontal-scroll tables are hostile on phones. Tablet (640+) and desktop (1024+) get progressively roomier columns; the existing `.main > .view { max-width: 1080px }` constrains desktop width. |
| D12 | Column set: **When · Event · Actor · Details** (4 columns) | Event pill (coloured) carries the `event` kind; entity ids are not user-meaningful and stay out of the table (CSV adds an `entity_type` column for completeness). |

## 4. Pushbacks / flagged issues (your call at approval)

1. **Audit coverage gap (affects what the screen shows).** The screen will
   display only *group-structure* events (create/join/add-member/leave/
   lock/unlock/delete) — expense and payout activity is **not audited at
   all** today, even though `get_group_activity`'s YAML notes say "entries
   for the group **and its expenses/payouts**" and `audit_log.entity_type`
   allows `expense`/`payout`. → **Optional Phase A4** adds expense
   CREATE/UPDATE/DELETE audit rows (plus a `group_id` field on `audit_log`
   so they can be found per group — see A4 for the exact design and its one
   data-model field addition). *Recommendation: include A4 — without it the
   screen never shows the most common activity in the app. A4b (payout
   lifecycle events) is a further stretch I'd defer.*
2. **`X-Total-Count` header vs hasMore-only.** Header needs one small new
   service function (`count_group_activity`) and YAML node. Alternative:
   no total, just Prev/Next enabled by "got a full page". → recommend the
   header (better UX, still non-breaking).
3. **"Latest active" definition.** Defined as *most recent audit event*
   (D6). With A4 declined this means structural events only — a group where
   members only add expenses would rank by its last join/lock. `group.last_updated`
   was rejected (it is not touched by expense activity either).
4. **Ordering of one thing I will not change:** the existing `GET /groups`
   response body/order for callers that do not pass `sort=activity`.

## 5. Backend specification

### A1 — date range + totals on the activity route

```python
# group_service.py
def _filter_activity(logs, date_from: datetime | None, date_to: datetime | None) -> list[AuditLog]
    # keep rows with date_from <= created_at <= date_to (inclusive; None = open end)
    # raises ValueError if date_from and date_to and date_from > date_to

def get_group_activity(storage, group_id, limit=50, offset=0,
                       date_from: datetime | None = None,
                       date_to: datetime | None = None) -> List[AuditLog]
    # storage fetch WITHOUT limit/offset (entity_id filter + created_at desc),
    # drop is_deleted, _filter_activity, then slice [offset : offset + limit]

def count_group_activity(storage, group_id,
                         date_from: datetime | None = None,
                         date_to: datetime | None = None) -> int
    # same pipeline, returns len(matched)
```

```python
# app/main.py — GET /groups/{group_id}/activity
date_from: datetime | None = Query(None),
date_to:   datetime | None = Query(None),
# try: rows = get_group_activity(...); total = count_group_activity(...)
#      except ValueError -> HTTPException 422
# response.headers["X-Total-Count"] = str(total)
# response_model stays List[AuditLog]; limit/offset semantics unchanged
```

Storage `list_records` and `storage_interface` are untouched (D4).

### A2 — activity-sorted groups + actor_name

```python
# group_service.py
def list_groups_for_user(storage, user_id, sort: str = "default") -> List[Group]
    # sort == "activity":
    #   1. ids = user's group ids (existing membership filter)
    #   2. audits = list_records("audit_log", filters={"entity_id": [str(i) for i in ids]},
    #                            sort_by="created_at", ascending=False)   # all rows
    #   3. order = first-seen group id in that stream
    #   4. groups sorted by (0, -recency index) if seen, else (1, time_created desc)
    # sort == "default": current behaviour exactly.
    # any other value -> ValueError (route maps to 422)

# _record_audit: populate actor_name (best-effort storage lookup of the
# acting user's display name; failure leaves it None — audit must never
# break the mutation it accompanies)
```

```python
# app/main.py — GET /groups
sort: str = Query("default")   # validated {"default", "activity"}, else 422
```

### A4 (optional) — expense audit coverage

- `audit_log` schema gains one optional field **`group_id: UUID | None`**
  (a `model_fields` addition to an existing `data-model` node — recorded by
  normal YAML back-update; not a schema-kind change).
- `expense_service.add_expense` / `update_expense` / `delete_expense` write
  an `AuditLog` (`entity_type="expense"`, `entity_id=<expense id>`,
  `group_id=<group id>`, `event=CREATE|UPDATE|DELETE`, description in
  `new_value`, actor + `actor_name`). Group events keep `entity_id=group`
  and also start setting `group_id` for uniformity.
- `get_group_activity` / `count_group_activity` query becomes a **union of
  two storage reads** — `entity_id IN [group]` (all legacy + current group
  rows) and `group_id IN [group]` (new rows) — merged, de-duplicated by
  `id`, re-sorted `created_at` desc, then filtered/sliced as before. Old
  data needs no migration.
- **A4b (stretch, not recommended now):** payout SETTLE/CANCEL/RECOVER
  events from the settlement engine — separate decision later.

## 6. Frontend specification (mobile-first)

### Registration (mirrors Phase E4)

- `index.html`: new `<section id="view-activity" class="view" hidden>` after
  `#view-expenses`.
- `NAV_ITEMS` += `{route:"activity", label:"Activity", icon: Icons.clock}`;
  `ROUTE_TITLES.activity = "Activity log"`;
  `PROTECTED_ROUTES` += `"activity"`;
  `routeHandlers.activity = async () => { await loadActivityScreen(); }`;
  `bindActivityScreen()` called once in `boot()`.

### Structure

```text
┌ View header: "Activity log" + sub-line (selected group name)        ┐
│ toolbar (mobile: stacked · ≥640px: one wrapped row)                │
│   [ Group ▾ (my groups, latest-activity first, top auto-selected) ]│
│   [ From date ] [ To date ] [ Clear ]                              │
│                              [ ⬇ Export CSV ]                      │
│ table / stacked cards                                              │
│   When            | Event   | Actor        | Details               │
│   Oct 19, 14:32   | UPDATE  | Alice        | User Bob joined …     │
│   Oct 18, 09:10   | CREATE  | Alice        | Created "Trip"        │
│ pager: [◀ Prev]  Page 1 of 7 · 124 rows  [Next ▶]                 │
└ empty states: no groups / no rows in range / load error ┘
```

### State & functions (`js/app.js`)

```js
let activityGroups = [];      // fetched with ?sort=activity (D7)
let activityGroupId = "";     // selected; defaults to activityGroups[0].id
let activityFrom = "";        // "YYYY-MM-DD" as picked ("" = open)
let activityTo = "";
let activityPage = 0;         // 0-based
const ACTIVITY_PAGE_SIZE = 20;
let activityTotal = 0;

loadActivityScreen()      // GET /groups?sort=activity → render options →
                          // auto-select first (req 2) → ensureGroupMembers(id)
                          // → activityPage=0 → fetchActivityPage()
fetchActivityPage()       // GET /groups/{id}/activity?limit=20&offset=page*20
                          // &date_from=&date_to= (ISO, from local day bounds)
                          // → read X-Total-Count → renderActivityTable +
                          // renderActivityPager; 403/404/422 → empty/error states
renderActivityTable(rows) // .activity-table + event pill colours
renderActivityPager()     // prev/next enabled states, "Page x of y · n rows"
activityRowHtml(log)      // when (local format), pill, actor resolve (D8),
                          // details prettify (D9)
prettifyActivity(text)    // D9 pattern rules against membersCache
exportActivityCsv()       // D10 chunked fetch of ALL filtered rows → CSV → Blob
bindActivityScreen()      // boot: group change, from/to change (page reset,
                          // from>to guard → toast), Clear, Prev/Next, Export
```

Behaviour rules: changing group or either date resets `activityPage` to 0;
`from > to` shows a toast and refuses the fetch; re-entering the route
refreshes the sorted group list but keeps the session's last group/range
selection when still valid.

### CSS (`css/app.css`)

- `.activity-toolbar` — mobile: stacked, full-width controls, dates in a
  2-column grid, export button full-width (`.btn`); ≥640 px: inline flex
  wrap with the group select `flex: 1 1 240px`, dates auto, export pushed
  right.
- `.table-wrap` — no horizontal scrolling on mobile (cards instead).
- `.activity-table` — base: rows/blocks via `display:block` with
  `td::before { content: attr(data-label) }` stacked card layout (top line
  = When + Event pill); ≥640 px: `display:table` with real columns
  (`When` ~170px, `Event` ~110px, `Actor` 1fr, `Details` 2fr), hover row,
  `border-collapse`.
- `.event-pill` — colour variants: CREATE (green), UPDATE (blue), DELETE
  (red), LOCK (amber), UNLOCK (teal), fallback grey.
- `.pager` — mobile: 3-part full-width row; ≥640 px: right-aligned inline.
- Empty/error blocks reuse the existing `.empty-state` pattern.

## 7. CSV export spec

- Columns: `created_at,event,actor,details,entity_type` (ISO UTC
  timestamps; `actor` = resolved name when available else raw id;
  `details` = same prettified text as the table).
- Rows: every row matching current group + date range (ignores current
  page), fetched in `limit=1000` chunks until `X-Total-Count` is reached or
  10 000-row safety cap (toast warns on clip).
- Escaping: RFC4180 (`"` → `""`, quote when containing `,`/`"`/newline),
  leading UTF-8 BOM for Excel.
- Filename: `activity_<group-slug>_<from|all>_<to|all>.csv`.
- Button: disabled when no rows match; `setBusy()` while fetching (icon
  buttons never get a busy label).

## 8. Test plan (baseline 228)

| Phase | Tests | Cases (indicative) |
|---|---|---|
| A1 | `test_group_service.py` +`test_api_main.py` | date_from only, date_to only, both, inclusive boundary rows, empty range result, from>to → ValueError, count parity with list; API: `X-Total-Count` correct across pages, inverted range → 422, malformed date → 422, non-member → 403, offset/limit unchanged |
| A2 | same files | `sort=activity` orders by audit recency incl. audit-less group last (time_created fallback), `sort=default` unchanged, invalid sort → 422, `_record_audit` sets `actor_name` |
| A4 (if approved) | `test_expense_service.py` + API | expense add/update/delete write audit rows with `group_id`; activity lists expense + group rows merged newest-first, union de-dup; legacy group rows (no `group_id`) still listed |
| A5 | live smoke script | full HTTP E2E (below) + `node --check` on all JS |

**Live smoke (A3 partial, A5 full):** uvicorn on a side port → create 2
users/groups → generate audit rows across several days (back-date one row
directly in storage while the server is *down*, per Phase 6 lesson) →
`GET /groups?sort=activity` ordering → activity page 1/2 sizes +
`X-Total-Count` math → date-range slice → non-member 403 → `#/activity`
serves → CSV content check (header row, quoting, row count). Manual
responsive pass at 360 px / 768 px / 1280 px (cannot be automated here).

## 9. Phases (each phase logs its own `implementation_log.md` entry)

| Phase | Scope | Verification before logging |
|---|---|---|
| **A1** | Backend: date range + `count_group_activity` + `X-Total-Count` + ValueError→422 wiring | pytest green (est. 228 → 235) |
| **A2** | Backend: `sort=activity` on `GET /groups` + `actor_name` fill in `_record_audit` | pytest green (est. → 239) |
| **A3** | Frontend: route/nav/toolbar/table/pagination (no CSV yet) | `node --check` clean; live smoke: rows render, pager math, date filter, sort order |
| **A4** | *(optional, only if approved)* expense audit writes + `group_id` field + union query | pytest green (est. → 244) |
| **A5** | CSV export + full live smoke incl. CSV content + responsive manual pass | smoke green end-to-end |
| **A6** | Docs & sync: README, YAML back-update, root mirror, release notes (see §10) | YAML parses, §11-style checklist 0 errors, root == blueprint, suite re-run green |

Log entries follow the existing convention (Goal / Actions / Result),
appended after each phase; a declined A4 will be recorded as "declined".

## 10. YAML / README / release-notes back-update (Phase A6)

- **`kharcha-tracker/kharcha-tracker.yaml`:**
  - `get_group_activity` — `interface.params` += `date_from`/`date_to`
    (`optional datetime`), `returns` unchanged, notes gain date-filter +
    `X-Total-Count` sentence.
  - new function node **`count_group_activity`** (parent `group_service`,
    `implementation` block, tests listed from A1).
  - `list_groups_for_user` — params += `sort` (`string`); notes describe
    the `activity` ordering rule; `main` route notes for `GET /groups`
    += `?sort=` and for `/activity` += `date_from, date_to,
    X-Total-Count`.
  - `app_js` stub (both copies) += activity view; `icons_js` note left as
    is (clock icon already listed by behaviour, not exhaustively).
  - if A4: `audit_log.model_fields` += `group_id` + notes; `add_expense`/
    `update_expense`/`delete_expense` notes mention audit writes;
    `group_service`/`expense_service` `implementation.functions` lists
    updated.
  - status ceiling stays `implemented` (no `tests:` back-fill — §11 item
    10 rule), §11 checklist re-run.
- **`kharcha-tracker/README.md`:** §8 route table gains `GET
  /groups?sort=activity` and the `/activity` query params + `X-Total-Count`
  note; Frontend SPA views line gains "activity log"; feature notes gain an
  **Activity log** bullet (sorted drop-down, date range, pagination, CSV
  export); §7 unchanged (no new service areas beyond audit reads).
- **`./kharcha-tracker.yaml` (repo root):** re-synced byte-identical to the
  blueprint (established mirror rule from the Phase E6 cross-check).
- **`implementation_log.md`:** one entry per phase (A1–A6) as above.
- **`release_notes.md`:** created if absent; index section at the top of
  the file listing every section; new section headed with the **next
  version number you provide** (prompted at A6 if not given yet), body with
  **Features** (user-facing summaries of this whole enhancement set) and
  **Bugs** (e.g. "activity endpoint previously ignored date filters — new"
  is a feature, not a bug; genuine bug-fix candidates: none known unless
  A4 reveals one — any found during implementation gets listed here).

## 11. Out of scope / non-goals

- No changes to `deployment/`, no commits unless requested.
- No new route *paths*, enum values, protocol, layout, or schema kinds;
  no storage-interface changes; no total-count in the response *body*.
- No per-row actions on the audit table (read-only screen).
- No real-time push — refresh happens on route enter / control change.
- A4b payout-event auditing and a page-size selector are deferred.

## 12. Acceptance criteria (traceable to the 4 requirements)

1. `#/activity` exists in nav; only the caller's groups appear in the
   drop-down; table sorted `created_at` desc; membership enforced 403.
2. Drop-down ordered by most recent audit activity (groups without audit
   rows last, newest first); top entry auto-selected on first load.
3. From/To controls sit beside the group drop-down; range filters the
   served rows (inclusive); empty-range and inverted-range handled
   (empty state / 422-with-toast).
4. Prev/Next paging with "Page x of y · n rows" driven by
   `X-Total-Count`; Export CSV downloads *all* filtered rows, quoted
   correctly, opens in a spreadsheet.
5. Layout usable at 360 px (stacked cards), 768 px and 1280 px (table).
6. Suite green; `node --check` clean; YAML + README + root mirror +
   release notes updated; progress logged per phase.

## 13. Open questions for approval

1. **Approve the plan as written?** (phases A1–A3, A5, A6 are the core.)
2. **Include optional Phase A4** (expense CREATE/UPDATE/DELETE auditing +
   `group_id` field on `audit_log`)? *Recommended yes — without it the
   screen never shows expense activity, which is most activity.*
3. Confirm **`X-Total-Count`** header approach (vs. totals-less paging).
4. Confirm nav label/route **"Activity" / `#/activity`** and **page size 20**.
5. **Version number** for the `release_notes.md` section (needed by A6).

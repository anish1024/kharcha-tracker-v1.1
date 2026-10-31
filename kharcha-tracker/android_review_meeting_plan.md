# Android Review Meeting Plan — 6 Enhancements

> **Status:** PROPOSAL (approval gate). **No code is written until this plan is approved.**
> This is the step-by-step design for the 6 enhancements requested for the Kharcha Tracker
> (FastAPI + vanilla-SPA). It is driven by the blueprint `kharcha-tracker.yaml` per
> `AGENTS.md` (closed-schema rules). Every blueprint extension (new enum value, new node,
> new DTO field) is explicitly flagged and needs your approval before the YAML is touched.

---

## 0. How to read this

- **§1** Ground rules + current baseline.
- **§2** One section per enhancement (E1–E6): *current state → target → concrete changes
  (backend / frontend / YAML / tests) → flags*.
- **§3** Cross-cutting work (UI, docs, release notes, environment).
- **§4** Implementation phase plan (order + exit criteria).
- **§5** Decisions / pushback requested from you.
- **§6** Risks & open questions.

**Legend for flags**
- 🔶 **Schema extension** — a closed-schema addition (new node / enum value / DTO field).
  Per `AGENTS.md §0` this needs explicit approval before I edit `kharcha-tracker.yaml`.
- ❓ **Decision needed** — an ambiguity or product choice I cannot make for you.
- ⚠️ **Visible behavior change** — something that will change what a user already sees.

---

## 1. Ground rules & current baseline

**Repos / paths (all under `/home/sudeep/Projects/kharcha-tracker-v1.1/`)**
- Blueprint (authoritative): `kharcha-tracker/kharcha-tracker.yaml`
- Root mirror (kept **byte-identical** to the blueprint): `kharcha-tracker.yaml`
- Rules: `kharcha-tracker/AGENTS.md` (closed schema §0, types §6, layout §8, status §10, checklist §11)
- App code: `kharcha-tracker/kharcha_v1/kharcha_gateway/` (backend) + `.../frontend_service/` (SPA)
- Tests: `kharcha-tracker/kharcha_v1/tests/` (run from `kharcha-tracker/`: `python -m pytest -q`)
- Docs to keep current: `README.md`, `implementation_log.md`, `release_notes.md`

**Baseline (per `implementation_log.md` final state A6)**
- Test suite: **240 passed**.
- Blueprint: **128 nodes**.
- Baseline is the "green bar" I must keep after every phase.

**Environment note (discovered during recon)**
- The shared virtualenv referenced in the log (`.venv`) is **not present** right now, and the
  system Python has no `fastapi`/`pydantic`/`pytest`. `requirement.txt` lists: `fastapi`,
  `uvicorn[standard]`, `pydantic`, `pytest`.
- → **Phase 0** will create `kharcha-tracker/.venv` and install `requirement.txt` before any
  test run, so the 240-test baseline can actually be re-established. (I will confirm network
  access for `pip install` when I start.)

**Conventions I will follow**
- Backend functions take `storage` first (closed module contract).
- Error contract: `LookupError→404`, `PermissionError→403`, `ValueError→422` (mapped in `main`).
- JS: classic scripts, no build step; validate with `node --check`.
- UI: **mobile-first but resizable** for tablet/desktop (existing `css/app.css` is responsive; I will keep that).

---

## 2. Enhancements

### E1 — Invite card on the Groups screen shows the **group name**

**Requirement:** "The invite message on the Groups screen must show the group name — make it
same as the notification message."

**Current state**
- The pending-invite card (`js/app.js` → `invitationCardHtml`, L1901) reads:
  *"Group invitation"* / *"You were invited to join a group … expires …"* — **no group name, no inviter**.
- The `Invitation` DTO (`models/invitation.py`) has **no `group_name`** field.
  `_to_invitation_dto` (`invitation_service.py` L21) does not resolve the name.
- The **notification to the owner** already contains the group name
  (`invitation_service._resolve`, L158–174):
  `"{actor} ({email}) {accepted|rejected} the invite code for \"{group.name}\""`.
- The invitee is **not** a member, so the frontend cannot resolve the name client-side — it
  must come from the server.

**Target**
- The pending-invite card shows the **group name** (and who invited), matching the level of
  detail the owner-facing notification carries.

**Changes**
1. 🔶 **DTO field (schema extension):** add `group_name: string` to the `invitation`
   data-model node (`models/invitation.py`). Bump `contract_version`.
2. **Backend** (`invitation_service.py`): resolve the group name where `storage` is in scope
   and populate `group_name`:
   - `_to_invitation_dto(record, group_name=None)` (optional param, default `None`).
   - `list_invitations_for_email` (L88): cache group lookups by `group_id` while mapping.
   - `list_invitations_for_group` (L110): resolve the single group once.
   - `create_invitation` (L39): group already loaded at L51 → pass `group.name`.
   - `_resolve` (L125): group already loaded at L161 → pass `group.name`.
3. **Frontend** (`app.js`): update `invitationCardHtml` to render `inv.group_name`
   (fallback to "a group") + inviter name/email when present + expiry.
4. **Tests:** extend `test_invitation_service.py` and `test_api_main.py` —
   `GET /users/me/invitations` and `POST /groups/{id}/invitations` responses include `group_name`.

**Flags**
- 🔶 Adding `group_name` to the `Invitation` DTO is a closed-schema extension — **needs approval**.
- ❓ **Interpretation:** the owner notification is past-tense ("X accepted the invite code for
  'Y'") and addressed to the *owner*; it doesn't literally fit a *pending invitee* card. I read
  your instruction as *"show the group name (as the notification does)"* and will also show the
  **inviter**. Confirm, or tell me you want the literal notification phrasing on the card.

---

### E2 — Default group: **"Personal Expenses"**

**Requirement:** Create a default group called *Personal Expenses*.

**Current state**
- No default group exists; users create groups manually (`POST /groups`).
- `create_group(storage, name, currency, created_by)` (`group_service.py` L80) **requires a
  3-letter currency**. The `Group` DTO has no `is_default` marker.
- Signup is `auth_service.signup`; auto-signin paths also exist (magic-link verify).

**Target**
- Every user has a *Personal Expenses* group. It is idempotent, lazy-safe, and appears in the
  group list + expenses selector.

**Design (proposed)**
1. 🔶 **New service function (schema extension):** `ensure_default_group(storage, user_id)`
   in `group_service` (new `function` node under `group_service` + add to `exports`).
   - Idempotent: if the user already owns a **non-deleted** group named exactly
     `"Personal Expenses"`, return it; else create one.
   - Uses `create_group(...)` with a **default currency** constant (below).
2. 🔶 **New constant (schema extension):** `DEFAULT_GROUP_CURRENCY` in the `system_config`
   node → `constants/system_config.py`. ❓ **value = `INR`** (matches the app's INR examples and
   "kharcha" naming) — *please confirm or give a different code.*
3. **Trigger points (❓ pick one or both, I recommend both):**
   - `auth_service.signup` → after the user record is created.
   - `main.list_groups` (`GET /groups`) → `ensure_default_group` before listing (covers
     pre-existing users and magic-link auto-signin).
4. **Frontend:** none strictly required — the group simply appears in the list and in the
   expenses selector (and becomes the natural E3 default).
5. **Tests:**
   - signup creates exactly one *Personal Expenses* group;
   - `GET /groups` ensures it for a pre-existing user (idempotent — no duplicates on repeat);
   - currency is the configured default.

**Flags**
- 🔶 Two new nodes (`ensure_default_group`, `DEFAULT_GROUP_CURRENCY`) — **needs approval**.
- ❓ **Default currency** value (proposed `INR`).
- ❓ **Recreation-after-delete:** if a user deletes their *Personal Expenses* group, should the
  next `GET /groups` **re-create** it, or leave it gone? (I propose **re-create** — it's the
  personal bucket; say the word if you'd rather not.)
- ❓ Should *Personal Expenses* be **protected from deletion**? (I propose **no** — keep it
  deletable to avoid surprising behavior.)

---

### E3 — Expenses screen: **last-used group selected by default**

**Requirement:** On the Expenses screen, pre-select the last used group; else the first group.

**Current state**
- `expensesGroupId` (`app.js` L1155) is a module-level `let`, **not persisted**.
- On load with nothing selected → empty state "Select a group…".
- `selectExpensesGroup(groupId)` (L1207) is the single place a group is chosen.

**Target**
- Returning to Expenses auto-selects the group you last used; if it's gone (or you're no longer
  a member), fall back to the **first** group in the list.

**Design (frontend-only, no schema change)**
1. Persist the chosen group id in `localStorage` under key **`kharcha_last_expense_group`**.
   - Write on `selectExpensesGroup(groupId)` when a real group is chosen.
2. In `renderExpensesScreen()` (L1157):
   - Read the stored id;
   - If it is still in `groupsCache` (still a member) → select it;
   - Else fall back to `groupsCache[0]` (the first group).
3. **Tests:** frontend-only → `node --check` on `app.js`. (No pytest dependency.)

**Flags**
- Pure frontend; no blueprint change. (Optionally bump the `app_js` node's
  `contract_version`/note to record the behavior — non-schema.)

---

### E4 — Bar chart becomes a **stacked bar** showing **expense-category colors**

**Requirement:** The dashboard bar chart should be a stacked bar whose segments use the
expense-category colors (shared with the pie).

**Current state**
- `charts.js`:
  - `barChart(series)` (L30) — one solid color (`#4f46e5`), `series = [{label, value, valueText}]`.
  - `pieChart(slices)` (L71) — colors via `color(index)` (i.e. **by slice order**, not by category).
  - `color(index)` (L11) — `PALETTE[index % PALETTE.length]`.
- `app.js`:
  - `buildRangeSeries` (L538) — per-bucket totals only (no per-category breakdown).
  - `renderExpenseCharts` (L588) — calls `Charts.barChart(series)`; pie legend uses `Charts.color(i)`.

**Target**
- Dashboard bar = **stacked** by expense category, each segment colored by a **stable
  category→color** mapping that is also used by the pie (so a category has the same color
  in both charts).

**Design**
1. `charts.js`:
   - Add `colorFor(label)` — stable `label → PALETTE` index (deterministic hash of the label).
   - **Refactor `pieChart`** to color slices via `colorFor(slice.label)` (⚠️ this changes the
     *existing* pie's colors to be stable per category — intended, but visible).
   - Add `stackedBarChart(series)` where each bucket is
     `{label, value, valueText, parts:[{label, value, valueText, color}]}`;
     render stacked rects (per-part color + hover `<title>`), gridlines, y-max =
     `niceMax(bucket total)`, x labels.
2. `app.js`:
   - Extend `buildRangeSeries` (or add `buildStackedSeries`) to accumulate **per-category
     per-bucket** amounts, producing the `parts` array (reuse the pie's category key
     `e.expn_ctgry`).
   - `renderExpenseCharts` → call `Charts.stackedBarChart(...)`; keep the pie (now stable colors).
3. 🔶 **YAML:** update the `charts_js` node `stub_behavior` (free text) to mention
   `stackedBarChart` + `colorFor`. (No structural schema change.)
4. **Tests:** `node --check` on `charts.js` + `app.js`; optional tiny assertion that
   `colorFor` is stable for the same label.

**Flags**
- ⚠️ The **pie chart's colors will change** (from order-based to category-based). That's the
  point of "shared with pie," but flag it in case you prefer the pie unchanged.
- No new enum/node/field — low-risk.

---

### E5 — New Groups field: **Notes** (entries of `[date, message, tags]`)

**Requirement:** A **Notes** field on a group — a list of `{date, message, tags}` entries, used
for an itinerary / description.

**Current state**
- No notes anywhere. The blueprint itself says: *"Not covered: FR-11 group notes (group model
  has no notes field)"* (`group_service` node note). `Group` DTO has no `notes`.

**Design (proposed — this is the largest new surface)**
1. 🔶 **New DTO node** `group_note` (`models/group_note.py`):
   - `id` (uuid), `group_id` (uuid), `message` (string), `tags` (list of string),
     `date` (datetime), `created_by`, `created_at`, `modified_by`, `modified_at`.
   - ❓ **`tags`: list-of-strings** (comma-separated in the UI) — confirm.
   - ❓ **`date`:** separate author-date field (as you wrote `[date, message, tags]`), with
     `created_at` as the system timestamp — confirm (vs. just using `created_at`).
2. 🔶 **New schema node** `group_note_record` (`storage_service/schema/group_note.py`)
   → new flat-file table `group_note_records.json` (registered in the `flat_file` node notes +
   `storage_interface`).
3. 🔶 **New service module** `group_note_service` with:
   `create_group_note`, `list_group_notes`, `update_group_note`, `delete_group_note`
   (each takes `storage` first; membership check via `group_service.is_member`).
4. **Routes** (in `main`, prefix `/api/v1`):
   - `POST   /groups/{group_id}/notes`
   - `GET    /groups/{group_id}/notes`
   - `PATCH  /groups/{group_id}/notes/{note_id}`
   - `DELETE /groups/{group_id}/notes/{note_id}`
5. 🔶 **Audit:** write audit rows for note create/update/delete. The `audit_log` node's
   `entity_type` allowed-list is currently `group, expense, user, payout, audit_log` — adding
   **`group_note`** to that list is an extension → flag.
6. **Frontend:** a **Notes** section inside the group card (`groupCardHtml`) —
   a list of notes (date + message + tag chips) + an "Add note" form (message + tags input) +
   edit/delete. Mobile-first, collapsible if long.
7. **Tests:** service CRUD + membership gate; API CRUD; audit row written.

**Flags**
- 🔶 Multiple new nodes (`group_note`, `group_note_record`, `group_note_service` + 4 functions,
  new routes, `audit_log` entity_type extension) — **needs approval**. This is the biggest
  blueprint change in the set.
- ❓ **Scope confirmations:** tags shape, separate `date` field, whether notes are audited,
  max length, and that **only members** can view/edit (I assume yes).

---

### E6 — **Corpus fund** of a group (think of it as an expense category)

**Requirement:** "The corpus fund of a group = collection of all orphan transactions or
overpaid balances. Think of corpus as the expense category itself."

**Current state**
- No corpus concept. In `settlement_engine`:
  - `orphan_payouts` = ad-hoc payments **not linked** to any expense
    (`p.is_adhoc and not p.expense_ids`, L516).
  - `calculate_net_dues` **ignores** orphan ad-hoc payments (L347–356).
  - `net_dues` = `map[user] → float` (positive = user owes; negative = user is owed / overpaid).
  - `settlement_preview` already exposes `net_dues` + `orphan_payouts`
    (`GET /groups/{id}/settle`).

**My interpretation (❓ most ambiguous item — please push back / refine)**
I read "corpus" as a **group-held pool** that is *not* owed to anyone yet. I propose to model it
with **minimal new backend** by reusing existing data:

1. 🔶 **Enum value (schema extension):** add `CORPUS` to the `expense_category` node
   (`constants/expense_category.py` → `ExpenseCategory.CORPUS`).
2. **Corpus balance = derived, not a new table:**
   `corpus(group) = Σ settled orphan ad-hoc payments  +  Σ max(0, −net_dues[user])`
   (i.e. money parked in the group from unlinked payments + members who overpaid).
   - Compute on the **frontend** from the existing `GET /groups/{id}/settle` preview
     (no new endpoint), or add a thin read-only `GET /groups/{id}/corpus` if you prefer. ❓
3. **Corpus deposit = an expense with category `CORPUS`**, paid by the depositor and split
   equally across members (so it enters the shared pool). This makes "corpus is an expense
   category" literal.
4. **Frontend:** a **Corpus** panel (group card and/or dashboard) showing the corpus total +
   the `CORPUS`-category expenses; `CORPUS` appears as a selectable category in the expense form.
5. **Tests:** enum member; corpus-balance derivation (orphan + overpaid); corpus-deposit expense
   flows through settlement unchanged.

**Flags**
- 🔶 New enum value `CORPUS` — **needs approval**.
- ❓ **Biggest open item.** Please confirm or correct:
  - Should corpus be a **separately-tracked fund** (new tables) or **derived** from existing
    orphan + overpaid data (my proposal, minimal)?
  - Exactly what counts as an **"overpaid balance"** — is it `net_dues[user] < 0`?
  - Can corpus be **withdrawn / used to offset dues**, or is it read-only for now?
  - Should `CORPUS` be a **user-selectable** category in the expense form (I assume yes)?

---

## 3. Cross-cutting work

### 3.1 UI
- **Mobile-first but resizable** for tablet/desktop. All new UI (E1 card, E5 notes, E6 corpus
  panel, E4 chart) reuses existing card/button/section patterns and `css/app.css` responsive rules.
- No new build tooling; keep classic scripts + `node --check`.

### 3.2 Documentation (after the code phases)
- **Blueprint back-update:** apply all 🔶 extensions to `kharcha-tracker/kharcha-tracker.yaml`,
  then **re-sync the root mirror** `kharcha-tracker.yaml` to be **byte-identical**.
  Re-run the `AGENTS.md §11` pre-flight checklist mentally (no dangling refs, types resolve,
  layouts legal).
- **`README.md`:** add the new features + new endpoints (E1, E2, E5, E6) to the feature/endpoint
  lists.
- **`implementation_log.md`:** append a progress entry **after each phase** (what changed,
  tests, files touched).
- **`release_notes.md`:** append a new version section (header = next version, ❓ **propose
  `v1.2.0`**) with user-friendly **Features** and **Bugs** sub-sections; **(re)create the index
  of all sections at the top** (the log claims an index exists, but the file currently has none).

### 3.3 Definition of done (per enhancement)
- Code + tests; `python -m pytest -q` stays **≥ 240 passed** (baseline) with new tests added.
- `node --check` clean on every touched JS file.
- YAML + root mirror updated and in sync; README + log updated.

---

## 4. Implementation phase plan (proposed order)

> I will implement **phase by phase**, append to `implementation_log.md` **after each phase**,
> and keep the suite green between phases. I will stop and report after each phase if you want
> incremental review.

| # | Phase | Enhancements | Notes |
|---|-------|--------------|-------|
| 0 | **Environment** | — | Create `kharcha-tracker/.venv`, `pip install -r requirement.txt`, re-establish the 240-test baseline. |
| 1 | **E1** | Invite card group name | DTO field + service resolve + `app.js` card + tests. |
| 2 | **E2** | Default *Personal Expenses* group | `ensure_default_group` + `DEFAULT_GROUP_CURRENCY` + signup/`GET /groups` hook + tests. |
| 3 | **E3** | Last-used group on Expenses | `localStorage` persist/restore (frontend). |
| 4 | **E4** | Stacked bar + category colors | `charts.js` (`colorFor`, `stackedBarChart`, pie refactor) + `app.js` + `charts_js` note. |
| 5 | **E5** | Group **Notes** | DTO + schema + service + routes + UI + audit + tests. |
| 6 | **E6** | **Corpus** fund | `CORPUS` enum + derived corpus balance + deposit-as-CORPUS + UI + tests. |
| 7 | **Docs** | — | YAML + root mirror sync, README, `release_notes.md` (new version + index), final log entry. |

**Suggested order rationale:** E1→E2→E3 are small and mostly backend/frontend plumbing; E4 is
isolated frontend; E5 and E6 are the two big new surfaces (E5 first as it's more concrete, E6
last as it's the most ambiguous). Each phase is independently shippable.

---

## 5. Decisions / pushback requested from you

Please confirm or correct the following before I start:

1. **Approve the overall plan + phase order** (§4)?
2. **E1 interpretation** — show **group name + inviter** on the pending-invite card (not the
   literal past-tense owner notification phrasing)? ✅/❌
3. **E2 default currency** — `INR`? And **re-create after delete**? (I propose re-create, not protected.)
4. **E2 trigger** — on **signup + `GET /groups`** (both)?
5. **E4 pie color change** — OK to make the pie use a **stable per-category** color (visible change)?
6. **E5 scope** — `tags` = list of strings? separate `date` field? audit notes? members-only?
7. **E6 corpus semantics** — derived (orphan + overpaid) vs new tracked fund? what counts as
   "overpaid"? withdrawable? `CORPUS` selectable in the form? (See §2 E6 flags.)
8. **Release notes version** — **`v1.2.0`**? (or give me a number.)

> I'll proceed only after you approve. If you'd like, I can start with **Phase 0 (environment)**
> immediately since it's risk-free, but I won't touch any product code until you say go.

---

## 6. Risks & open questions

- **Blueprint is closed.** E1 (DTO field), E2 (2 new nodes), E5 (many new nodes + audit
  entity_type), and E6 (enum value) are all **schema extensions** requiring your approval
  before I edit the YAML (`AGENTS.md §0`). I've listed each with 🔶.
- **E6 is underspecified.** My interpretation may not match your mental model. I've proposed the
  minimal, no-new-table version and invited pushback — this is the one item I most want your
  input on.
- **Environment not set up.** No venv/deps present; Phase 0 must install `fastapi`,
  `uvicorn[standard]`, `pydantic`, `pytest`. I'll verify network/`pip` availability when I begin.
- **Root mirror sync.** Forgetting to keep `kharcha-tracker.yaml` byte-identical to the blueprint
  is a known failure mode; it's an explicit step in Phase 7 and in every YAML-touching phase.
- **Release-notes index missing.** The log claims an index was created; the file has none. I'll
  (re)create it in Phase 7 to satisfy the "index of all sections at top" rule.

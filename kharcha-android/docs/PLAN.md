# Kharcha Android - Staged Implementation Plan

## Goal
Build the Android app progressively in stages so we can test deploy on smartphone at each stage.

## Stage 1: Blank App (DONE)
- Create minimal Android project structure
- Basic "Hello World" Compose app
- Verify toolchain compiles and builds successfully
- **Status**: COMPLETE - APK builds successfully

## Stage 2: Basic Login Form → Blank Dashboard (DONE)
- Add navigation (Compose Navigation)
- Create Login screen with basic form (email/password fields, login button)
- Create Dashboard screen (blank/simple)
- Navigate from Login → Dashboard on button click
- Test compile and build
- **Status**: COMPLETE - Navigation, Login + Dashboard screens implemented, Login button navigates to Dashboard; `assembleDebug` builds successfully (APK produced)

## Stage 3: API Integration Setup (DONE)
- Add Retrofit, OkHttp dependencies
- Create API service interfaces
- Add base network configuration
- Basic error handling
- **Status**: COMPLETE - Dependencies (Retrofit 2.11, OkHttp 4.12, Gson converter) added; `ApiService` interface (login/register/healthCheck) created; `NetworkModule` with base URL, timeouts, logging + Gson configured; basic error handling implemented — `safeCall` (for `Response<T>` endpoints) and `safeRun` (for plain suspend calls) map HTTP/IO/unknown failures to `NetworkResult.Error` with status code and FastAPI `detail`/`message` extraction; covered by 8 unit tests (`SafeCallTest`, all passing)

## Stage 4: Authentication (DONE)
- Connect login form to API
- Add auth state management
- Persist tokens (DataStore)
- Handle auth flow
- **Status**: COMPLETE - Login form wired to `POST /auth/login` (`AuthViewModel` → `AuthRepository`); session persisted in DataStore (`TokenStore` with volatile token cache for the interceptor); `AuthInterceptor` attaches `Authorization: Bearer`, single-flight refresh with exactly one replay on 401 (refresh via a dedicated OkHttp client; backend refresh tokens are single-use/rotated), and force-signs-out on refresh failure via `sessionInvalidated` → UI returns to Login; auth-driven navigation (splash while restoring, Login ↔ Dashboard with `popUpTo(0)`, Sign-out on Dashboard); base URL overridable at build time with `-PapiBaseUrl` (default emulator host bridge `http://10.0.2.2:8000/`); `assembleDebug` green and 18 unit tests passing (DTO shape tests + `SafeCallTest` + MockWebServer round-trip of login/me endpoints)

## Stage 5: Dashboard with Basic Data (DONE)
- Display user info
- Show basic expense summary
- Add navigation between screens
- "Continue with email" button on Login (below "Login") — passwordless login via magic-link, no password check
- **Status**: COMPLETE - Dashboard shows the signed-in profile (`GET /users/me`) and the user's groups (`GET /groups`); new group-detail screen (`GET /groups/{id}`) with total spent, expense count, and the user's net balance (`GET /groups/{id}/expenses` + `GET /groups/{id}/settle`; `net_dues` sign convention per `settlement_engine.calculate_net_dues`: positive = owed, negative = owes); "Continue with email" chains `POST /auth/magic-link` (202, single-use token in body, no mail transport) → `POST /auth/magic-link/verify` (server auto-provisions new accounts); per-back-stack `group/{groupId}` route with its own `GroupDetailViewModel`; pure-function `Format` helpers (date/dateTime/amount/net position) keep the UI free of parsing; `assembleDebug` green and 36 unit tests passing (18 baseline + 18 new: `FormatTest`, `DashboardDtosTest`, `DashboardClientIntegrationTest` incl. MockWebServer round-trips and the magic-link 202/verify flow)

## Stage 6: Groups & Expenses (DONE)
- Groups list screen (with create-group and join-by-invite)
- Group members list, leave group, invite-code sharing
- Add/create expense functionality (payer + equal or percentage split)
- **Status**: COMPLETE - Dashboard overflow menu: create group (`POST /groups`, navigates straight to the new group) and join by invite code (`POST /groups/{id}/invite/accept`); group-detail screen gains the members list (`GET /groups/{id}/members`), invite-code sharing (`POST /groups/{id}/invite`, code shown with copy-to-clipboard) and leave-group (`DELETE /groups/{id}/members/me`; server-side gating — outstanding balance / open payouts / settled group — surfaces its 422 message inline in the confirm dialog); add-expense screen with payer selection and EQUAL or PERCENTAGE split (percentages seeded to sum to exactly 100 for any member count, live total with 0.01 tolerance, exact validation messages), submitting `POST /expenses` (EQUAL omits `expn_splt` so the gateway's equal-split default applies; PERCENTAGE sends `expn_splt` shares; blank description/category dropped); `extractErrorMessage` now decodes FastAPI object `detail` (403 subscription limits → inner message) and Pydantic array `detail` (422 → first `msg`); `ReloadOnResume` re-fetches dashboard/group-detail on in-app back-navigation and app foregrounding; `assembleDebug` green and 71 unit tests passing (36 baseline + 35 new: `SafeCallTest` +2, `GroupMutationsDtosTest`, `GroupMutationsClientIntegrationTest` incl. MockWebServer round-trips, `AddExpenseFormTest` pure-function seed/validate/build)

## Stage 7a: Expense edit/delete + settlements & payouts (DONE)
- Tap an expense row to edit it: date, category, description, payer (the amount is read-only - the gateway's `ExpenseUpdate` has no amount field)
- Delete an expense from the edit screen
- Settlement preview (proposed "A pays B" payouts) + "Settle group" action
- Payout list with role-gated actions: mark paid, confirm settled, mark failed, cancel (evidence optional; reason required for failed/cancel)
- Group lock/unlock
- **Status**: COMPLETE - expense edit (`GET /expenses/{transId}`, `PATCH /expenses/{transId}` with the gateway's absent-key-means-no-change contract - an unedited form sends `{}`, an empty description clears it, a new payer sends the full amount on that one payer, and a non-blank category can't be blanked) and delete (`DELETE /expenses/{transId}`; server refusals surface inline in the confirm dialog); group-detail screen gains the settlement section (`POST /groups/{id}/settle`, PERIODIC default) and the payouts section (`GET /groups/{id}/payouts` + `POST .../paid|settled|failed|cancel` with the `settlement_engine.py` permission matrix as a pure, unit-tested `PayoutActions` helper; busy scope per payout row, confirm dialogs with evidence + mandatory reason); overflow "Lock group" / "Unlock group" (`POST /groups/{id}/lock|unlock`, offered only for OPEN / LOCKED); edit-expense route `group/{groupId}/expense/{transId}` (per-back-stack VM via `EditExpenseViewModelFactory`); pure edit-form logic (`initialDateOf`, leap-year-aware `validateEditDate`, `validateEditExpenseForm`, `hasChanges`, `buildExpenseUpdateRequest`) all unit tested; `assembleDebug` green and 134 unit tests passing (71 baseline + 63 new: `SettlementDtosTest`, `SettlementClientIntegrationTest` incl. MockWebServer round-trips of all 11 endpoints and the 422/403 error surfaces, `PayoutActionsTest` role matrix, `EditExpenseFormTest` pure-function seed/validate/build)

## Stage 7b: Activity + settings + polish (DONE)
- Activity (recent changes feed), settings
- Polish UI
- **Status**: COMPLETE - activity screen (`GET /groups/{id}/activity` with 0-based `offset` pagination - the VM's 1-based page maps to `offset = (page-1) * pageSize`, and `X-Total-Count` (read via the new `safeCallWithHeaders`) drives the "Page x of y" pager; group selector, from/to date-range with web-verbatim validation messages, event pills colour-coded by kind, actor names resolved from the group-members list, and a `prettifyActivity` equivalent as the pure, unit-tested `ActivityTexts`); settings screen (profile card with name/avatar editing -> `PATCH /users/me` under the gateway's absent-key-means-no-change contract, read-only email; subscription banner + the 4 web catalogue plan cards with a confirm-plan dialog -> `POST /subscription` including the 422 "Invalid plan; choose one of..." surfacing; notifications list (`GET /users/me/notifications` with unread emphasis + `POST .../read`); appearance (System/Light/Dark persisted via the DataStore-backed `ThemePreferenceStore` and applied in `MainActivity`); session card -> sign-out through `AuthViewModel.signOut()`); navigation gains the `activity/{groupId}` + `settings` routes and the matching dashboard overflow entries; `assembleDebug` green and 200 unit tests passing (134 baseline + 66 new: `ActivityTextsTest`, `SettingsTextsTest`, `ActivityDtosTest`, `SettingsDtosTest`, `ActivityClientIntegrationTest` + `SettingsClientIntegrationTest` with MockWebServer round-trips); note - hit a Kotlin 2.0.20 compiler quirk (a spurious `No parameter with name 'eventType' found` on the `entityType` named argument in files with several distinct string literals, reproduced standalone on both the K1 and K2 frontends) and worked around it in tests via the `activityLog(...)` fixture that builds `ActivityLogDto` with purely positional arguments

## Stage 8: Review-meeting enhancements E1-E6 (8a/8b/8c/8d DONE; 8e/8f PENDING)

**Source of truth:** `android_review_meeting_plan.md`.
**Why this stage exists:** Stages 1-7b built the Android baseline (auth, dashboard,
groups/expenses, edit/delete, settlements/payouts, activity, settings). The six
review-meeting enhancements (E1-E6) were implemented in the gateway + web SPA but
were never carried over to the Android app. Stage 8 closes that gap. It is an
Android-only stage: E1 (invitation `group_name`), E2 (default group), E5 (group
notes) and the `CORPUS` enum already exist server-side; E3/E4 are purely
client-side. **No backend, YAML or web-frontend changes are planned.** If an
endpoint shape differs from what is documented below, stop and flag it rather
than inventing a client-side workaround.

### 8.0 Gap analysis

| Item | Review-meeting point | Android today | Stage 8 outcome |
|---|---|---|---|
| **E1** | Pending-invite card must show the **group name** (like the owner notification) | No invitation model/API/UI at all; only join-by-group-id + invite-code | Pending invitations on the Dashboard, using `group_name`, with accept/decline; email invite from group detail |
| **E2** | Every user gets a default **"Personal Expenses"** group | Nothing to build: the gateway creates it on signup and lazily on `GET /groups`; Android already lists whatever `GET /groups` returns | Verify + document; use it naturally as the E3 fallback first group. **No client-side group creation** (that would duplicate it) |
| **E3** | Expenses screen pre-selects the **last-used** group, else the first | Android has no Expenses screen; expenses are only per-group inside group detail | ✅ New top-level Expenses screen with a group selector, persisted last-used group, first-group fallback |
| **E4** | Dashboard bar chart becomes a **stacked bar** with **expense-category colours** shared with the pie | Android has no charts at all today | ⏳ Add chart data + Compose stacked bar **and** pie/legend using one stable category colour map |
| **E5** | Group **Notes** = `[date, message, tags]` entries | No notes model, API or UI | ⏳ Note DTOs/API/repository, notes section in group detail, add/edit/delete |
| **E6** | Group **Corpus fund** derived from orphan/overpaid balances; `CORPUS` expense category | No corpus panel and no category picker (category is free text) | ⏳ Pure corpus calculator from `GET /groups/{id}/settle`, corpus card in group detail, category picker including `CORPUS` |

### 8.0.1 Shared conventions for every sub-stage

- Keep the existing architecture: `ApiService` -> repository -> `NetworkResult`/`safeCall`
  -> ViewModel `StateFlow` -> Compose screen. No new DI framework.
- New DTOs stay nullable/default-tolerant (plain Gson skips Kotlin defaults), with
  `xxxRaw` + `val xxx get() = xxxRaw.orEmpty()` where a JSON list may be absent.
- All user-facing server errors keep using `NetworkResult.Error.message` inline or in a
  snackbar; no new error envelope.
- Charts use Compose `Canvas` only - **no MPAndroidChart / no new dependency**.
- Date handling must stay minSdk-24 safe: reuse `Format` / `validateEditDate` and
  string/`java.util.Calendar` arithmetic; do not introduce `java.time` without
  core-library desugaring (currently absent).
- Pure logic goes in top-level functions next to its ViewModel so it is JVM-unit-tested
  (same pattern as `AddExpenseFormTest`, `EditExpenseFormTest`, `PayoutActionsTest`).
- Tests: DTO shape tests + `MockWebServer` client-integration tests + pure-function tests.
  Keep the **200-test baseline green** (`testDebugUnitTest`) and `assembleDebug` green.

### 8a - E1: Invitations (group name on the invite card) [DONE]

**Wire contract (already live):**
- `GET  /api/v1/users/me/invitations` -> `List<Invitation>`
- `POST /api/v1/groups/{groupId}/invitations` body `{ "email": "..." }` -> `Invitation` (201)
- `POST /api/v1/invitations/{invitationId}/accept` -> `Invitation`
- `POST /api/v1/invitations/{invitationId}/decline` -> `Invitation`

**Data**
- New `InvitationDtos.kt`: `InvitationDto` (`id`, `group_id`, `inviter_id`,
  `invitee_email`, `invite_code`, `status`, `created_at`, `expires_at`, `group_name`)
  and `EmailInvitationRequest(email)`.
- New `InvitationRepository` using `safeCall`; keep it separate from `GroupRepository`
  so the dashboard can load invitations without depending on group detail.
- `ApiService`: add the four calls above.

**UI**
- Dashboard (Android's equivalent of the SPA "Groups screen"): a new
  "Invitations (n)" section above "Your groups". Each card shows
  `group_name ?: "a group"`, "You were invited to join {group_name}", and the expiry
  date. Accept / Decline buttons call the matching endpoint, then refresh the
  dashboard (accepted groups come in through the existing `GET /groups` refresh).
- The `inviter_id` is on the wire but **no inviter name is provided** by the gateway.
  Do not invent one: show the group name + expiry (matching the shipped SPA). If the
  review-meeting phrase "who invited" genuinely requires a name, flag a backend DTO
  extension first.
- Group detail overflow gains **"Invite by email"** (dialog: email -> `POST
  /groups/{id}/invitations` -> snackbar). This is what creates the pending invitation
  that the invitee sees on their dashboard.

**State/tests**
- Extend `DashboardViewModel` with invitation state (`loading`, list, accept/decline
  busy/error) or add a small `InvitationsViewModel` used by the same screen; one
  `DashboardViewModel` is preferred to avoid a second group fetch.
- `InvitationDtosTest` (JSON with/without `group_name`, null `expires_at`);
  `InvitationClientIntegrationTest` (paths, methods, bodies, accept/decline);
  pure `invitationStatusLabel`/expiry helper if the card needs one.

**Acceptance:** a pending invitation shows the group name (not "a group"); accept adds
the group to the dashboard list; decline removes the card; an owner can invite by email
from group detail.

### 8b - E2 + E3: Default group verification + last-used group on Expenses

**E2 (verification only, no production code)**
- The gateway already calls `group_service.ensure_default_group` on signup and on
  `GET /groups`; Android must not create another default group.
- Smoke/integration check: a fresh account's `GET /groups` contains exactly one
  *Personal Expenses* group, and reloading does not duplicate it.
- E3 uses the first group as the fallback, so a brand-new user lands on *Personal
  Expenses* without special-casing the name.

**E3 (new screen)**
- New route `expenses` + Dashboard overflow entry "Expenses".
- New `ExpensesScreen` + `ExpensesViewModel`:
  - loads `GET /groups`;
  - resolves the selected group with a pure
    `resolveInitialExpenseGroupId(groups, storedId): String?`
    (**stored id if it is still in the list, else `groups.firstOrNull()?.id`**);
  - loads `GET /groups/{id}/expenses` for the selected group;
  - group selector writes the new id and persists it;
  - "Add expense" navigates to the existing `group/{groupId}/add-expense` route;
    tapping a row navigates to the existing edit route.
- New `LastExpenseGroupStore` (DataStore Preferences, same pattern as
  `ThemePreferenceStore`) with key **`kharcha_last_expense_group`** - same string as
  the SPA key for behavioural parity (the stores are physically separate on Android).
- Empty state when there are no groups; selector disabled/blank when none.
- Tests: `ExpenseGroupSelectionTest` for stored-present / stored-missing / empty-list
  cases; client integration for the expenses fetch if not already covered.

**Acceptance:** reopening Expenses selects the last-used group across process
restarts; if that group disappeared, the first group is selected; no groups -> empty
state.

### 8c - E4: Dashboard stacked bar + shared category colours

Because Android has no chart today, this sub-stage adds the whole dashboard
"Expense summary" surface, not just a restyle of an existing bar.

**Pure data layer (`ui/dashboard/ChartData.kt`)**
- `ChartColors`: the exact SPA palette
  (`#4f46e5`, `#0ea5e9`, `#14b8a6`, `#f59e0b`, `#f43f5e`, `#8b5cf6`, `#84cc16`,
  `#ec4899`, `#64748b`, `#0891b2`) and `colorFor(label)` using the SPA's
  deterministic hash (`h = h*31 + charCode`, lowercase/trimmed label, `h % palette`).
- `ChartRange` = `DAILY` (14 days), `WEEKLY` (8 ISO weeks), `MONTHLY` (6 months),
  mirroring `rangeBuckets()` in `app.js`.
- `buildRangeSeries(expenses, range, currency, now): List<StackedBucket>`:
  bucket by date, accumulate per-category totals, sort parts descending, produce
  `StackedBucket(label, total, valueText, parts = List<ChartPart(label, value, color)>)`.
- `buildCategorySlices(expenses, currency)` for the pie/legend.
- Buckets must be computed without `java.time`; inject `now` (epoch/day key) so tests
  are deterministic.

**UI**
- New Compose `StackedBarChart` and `PieChart` (+ legend) in `ui/dashboard/Charts.kt`,
  drawn with `Canvas`: gridlines, y-max from bucket totals, per-segment fill and a
  tap/hover-equivalent label; legend dots use the same `colorFor` mapping.
- Dashboard gains a chart section with its own group selector and
  Daily/Weekly/Monthly chips. Default group resolution reuses the E3 last-used/first
  rule; fetch with `GET /groups/{id}/expenses?limit=...` (server clamps 1..1000; use a
  large enough window for the chart, e.g. 500).
- Empty state when the group has no expenses or the selected range has no totals.
- Tests: `ChartDataTest` (bucket labels/counts, per-category stacking, descending
  parts, daily/weekly/monthly boundaries with injected `now`) and `ChartColorsTest`
  (same label -> same colour; case/whitespace-insensitive; palette bounds).

**Acceptance:** dashboard bar is stacked by category; the same category has the same
colour in bar, pie and legend; range switches recompute buckets; totals equal the sum
of visible parts.

### 8d - E5: Group Notes

**Wire contract (already live):**
- `GET    /api/v1/groups/{groupId}/notes` -> `List<GroupNote>`
- `POST   /api/v1/groups/{groupId}/notes` -> `GroupNote` (201)
- `PATCH  /api/v1/groups/{groupId}/notes/{noteId}` -> `GroupNote`
- `DELETE /api/v1/groups/{groupId}/notes/{noteId}` -> `bool`

**Data**
- New `GroupNoteDtos.kt`: `GroupNoteDto` (`id`, `group_id`, `date`, `message`, `tags`,
  `created_by`, `created_at`, `modified_by`, `modified_at`) and
  `CreateGroupNoteRequest(message, tags, date)` /
  `UpdateGroupNoteRequest(message?, tags?, date?)`.
- `tags` is a JSON list of strings; the UI edits it as comma-separated text.
- `date` is the author-facing date (`yyyy-MM-dd` is accepted; the server stores it as
  a datetime and returns ISO-8601). `created_at` remains the system timestamp.
- New `GroupNoteRepository`; add the four calls to `ApiService`.

**UI**
- `GroupDetailScreen`: a "Notes (n)" section (between members/payouts and the expense
  list is fine) with:
  - rows: formatted date, message, tag chips, "(edited)" when `modified_at` is set;
  - Add note form/dialog: message (required, max 500), date (default today), tags
    (comma separated);
  - edit/delete actions per row, delete behind a confirm dialog.
- `GroupDetailViewModel`: load notes in `fetchDetail()` (degrade independently: a failed
  notes fetch hides the section, like members/payouts); create/update/delete actions with
  busy/error state; after success refetch notes and snackbar.
- Pure `NoteForm.kt`: `parseTags(input)` (split comma, trim, drop blanks, dedupe) and
  `validateNoteForm(message, date)` (blank message, date via the existing
  `validateEditDate`, tag limits 50 chars/20 tags to match the gateway). Reuse the
  pure helpers in tests and in the VM.
- Tests: `GroupNoteDtosTest` (tags absent -> empty list, nullable modified fields),
  `GroupNoteClientIntegrationTest` (all four endpoints, 422/403 surfaces),
  `NoteFormTest` (tag parsing/dedupe, blank message, invalid date, limits).

**Acceptance:** members can add, edit and delete notes; tags render as chips; the list is
newest author-date first (server order); validation matches the gateway's limits.

### 8e - E6: Corpus fund

**Model (derived, no new endpoint/table):**
- `corpus = SUM(status == "SETTLED" orphan ad-hoc payouts) + SUM(max(0, net_dues[user]))`.
  `orphan_payouts` and `net_dues` already come from `GET /groups/{id}/settle`; only
  `SETTLED` orphans count (PENDING/PAID/FAILED/CANCELLED do not). The two terms are
  disjoint because orphan payouts never enter `net_dues`.
- Add a pure `Corpus.kt`: `corpusFromPreview(preview): CorpusTotals` with
  `orphanTotal`, `overpaidTotal`, `total`. Unit-test with positive/negative net dues and
  mixed orphan statuses.
- `GroupDetailUiState.Ready` gains `corpus: CorpusTotals` and
  `corpusDeposits: List<ExpenseDto>` (expenses whose category is `CORPUS`), both empty
  when the preview/expenses fetch failed; hide the card in that case.

**Category picker**
- New `ExpenseCategory` enum/object with the gateway values:
  `GROCERY, UTILITY, TRAVEL, ENTERTAINMENT, FOOD_N_DINING, OP_EXP, CAP_EXP,
  ACCOMMODATION, CUSTOM, CORPUS` (labels replace `_` with spaces).
- Replace the free-text category field in Add Expense and Edit Expense with a
  dropdown/chip picker backed by that list, so `CORPUS` is genuinely selectable.
  Keep `CUSTOM` for free-form use; keep the existing edit rule that a non-blank initial
  category cannot be blanked.
- A corpus deposit is therefore just a normal `POST /expenses` with category `CORPUS`
  (existing equal/percentage split flow; no new endpoint).

**UI**
- Group detail gains a "Corpus fund" card: total, "Orphan payments (settled)" and
  "Overpaid balances" lines, plus a "Deposits" list (description, payer(s), amount).
  Read-only, matching the SPA's E6 panel.
- Tests: `CorpusTest` (settled-only, overpaid only, mixed, empty, currency formatting
  at the call site), `ExpenseCategoryTest` (list/labels/`CORPUS` present).

**Acceptance:** corpus total matches the formula; changing an orphan payout to SETTLED
changes the corpus; `CORPUS` deposits appear in the card; `CORPUS` is selectable in both
add and edit expense.

### 8f - Cross-cutting polish, docs and final regression

**UI**
- Mobile-first but resizable, as in the review plan: cap content width / use a
  single-column tablet layout where it improves readability; no phone-only assumptions
  in the new screens. Reuse `MaterialTheme` spacing and existing card patterns.
- New strings stay inline as the rest of the app does today; keep wording aligned with
  the SPA ("You were invited to join ...", "Personal Expenses", "Corpus fund").

**Docs / status**
- Update this Stage 8 entry to DONE per sub-stage with a short status paragraph
  (files, endpoints, tests, build result), matching the style of Stages 1-7b.
- Append one line per sub-stage to `android_implementation.log`.
- Update `ANDROID_IMPLEMENTATION_GUIDE.md` "Current Status" (it is stale) to mention
  invitations, expenses screen, charts, notes and corpus.
- No root-mirror/YAML work in this stage (the gateway/web branch already made those
  changes); if a backend contract is found missing, stop and raise it as a separate
  backend change.

**Final regression**
- `./gradlew testDebugUnitTest` green with the 200 baseline tests + the new tests.
- `./gradlew assembleDebug` green.
- Manual device/emulator smoke: pending invite -> accept -> group appears; Expenses
  screen remembers the last group; stacked bar/pie colours match per category; notes
  CRUD; corpus card and `CORPUS` deposit.

### 8.1 Suggested sub-stage order and exit criteria (8a completed)

| # | Sub-stage | Depends on | Exit criteria |
|---|---|---|---|
| 8a | E1 invitations | - | ✅ DONE (InvitationDtos, ApiService, InvitationRepository, tests green, build green) |
| 8b | E2 verification + E3 Expenses screen | 8a (dashboard refresh pattern) | ✅ Default group present; last-used group restores, first-group fallback |
| 8c | E4 charts | 8b (group selection) | ⏳ IN PROGRESS — `ChartData.kt` + `Charts.kt` compile green; `ChartDataTest`/`ChartColorsTest` + `DashboardScreen` chart section pending |
| 8d | E5 notes | 8a/8b (detail screen patterns) | Notes CRUD + validation green |
| 8e | E6 corpus | 8d (detail section pattern) | Corpus formula + `CORPUS` picker green |
| 8f | Polish/docs/regression | all | Tests/build green; docs updated; smoke test passed |

**Total expected new test classes:** `InvitationDtosTest`, `InvitationClientIntegrationTest`,
`ExpenseGroupSelectionTest`, `ChartDataTest`, `ChartColorsTest`, `GroupNoteDtosTest`,
`GroupNoteClientIntegrationTest`, `NoteFormTest`, `CorpusTest`, `ExpenseCategoryTest`
(plus additions to existing DTO/client tests where a DTO is extended).

**Explicit non-goals:** new backend endpoints/tables, schema or YAML edits, offline
cache/Room, CSV export, payout remind/escalate, group deletion, and any change to the
login/auth flow.

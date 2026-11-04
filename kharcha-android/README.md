# Kharcha Android — Implementation Status (Updated from `docs/android_frontend_plan.md`)

> This document updates the original `docs/android_frontend_plan.md` proposal with the actual build status as of `docs/PLAN.md` (Stages 1–7b complete, Stage 8 E1–E6 complete, 234 JVM unit tests passing). The original proposal described a high-level roadmap with Room, Koin, MPAndroidChart, WorkManager, and generated models; the actual code uses Compose + Retrofit 2.11 / OkHttp 4.12 / Gson + DataStore, with pure Kotlin ViewModels, no Room, no Koin, no background sync service, and no MPAndroidChart dependency.

---

## Executive Summary

This is a native Android application for the Kharcha Tracker system. The app communicates with the Python FastAPI backend (`/api/v1`) via REST using Retrofit. The UI is built with Jetpack Compose (Material 3), state flows through Kotlin `StateFlow` in ViewModels, tokens are persisted with DataStore (`DataStore Preferences`, incl. `TokenStore`, `ThemePreferenceStore`, `LastExpenseGroupStore`), and charts are drawn with Compose `Canvas` — no external chart libraries.

- **Platform:** Android (minSdk 24)
- **UI Framework:** Jetpack Compose (Material 3)
- **Networking:** Retrofit 2.11 + OkHttp 4.12 + Gson converter
- **State / Persistence:** Kotlin `Flow`, `StateFlow`, `DataStore Preferences` (`TokenStore`, `ThemePreferenceStore`)
- **Architecture:** MVVM with per-screen ViewModels; `NetworkResult` wrapper (`safeCall`, `safeRun`) for error mapping
- **Build:** Gradle Kotlin DSL; `assembleDebug` green

---

## Architecture (Actual Code Structure)

```
app/src/main/kotlin/com/kharcha/tracker/
├── Navigation.kt                 # Routes: login, dashboard, group/{id}, group/{groupId}/expense/{transId},
│                                 # group/{groupId}/add-expense, group/{groupId}/settle, settings,
│                                 # group/{groupId}/activity, expenses (proposed 8b)
├── MainActivity.kt                # Theme application (System/Light/Dark via ThemePreferenceStore)
│
├── ui/
│   ├── login/                     # Login + "Continue with email" (magic-link) screens
│   ├── dashboard/                 # Dashboard (profile card, groups, invitation cards proposed 8a),
│   │                              # group detail (members, invite code, leave, expenses, settlements,
│   │                              # payouts, notes proposed 8d, corpus card proposed 8e),
│   │                              # settings (profile, subscription, notifications, appearance)
│   │                              # Charts (8c) DONE — StackedBarChart + PieChart + ChartLegend in GroupDetailScreen
│   └── settings/                  # Profile edit (PATCH /users/me), notification read,
│                                  # appearance toggle, session sign-out
│
├── data/
│   ├── remote/
│   │   ├── ApiConstants.kt        # base URL, API version = "api/v1"
│   │   ├── ApiService.kt          # Retrofit interface (endpoints for auth, users, groups,
│   │                              # expenses, invitations (8a), notes (8d), settlements, payouts,
│   │                              # activity, settings)
│   │   ├── AuthInterceptor.kt     # Bearer token injection; single-flight 401 refresh; replay once
│   │   ├── NetworkModule.kt       # Retrofit + OkHttp + Gson + logging
│   │   └── safeCall.kt            # `safeCall<T>` (Response<T>) and `safeRun<T>` (suspend T)
│   │                              # mapping to NetworkResult.Success / NetworkResult.Error
│   │
│   ├── repository/
│   │   ├── AuthRepository.kt      # Login / magic-link / sign-out
│   │   ├── GroupRepository.kt     # GET /groups, GET /groups/{id}, members, invite, leave,
│   │                              # create group, join by invite code
│   │   ├── ExpenseRepository.kt   # GET /groups/{id}/expenses, POST /expenses,
│   │                              # GET /expenses/{transId}, PATCH /expenses/{transId}, DELETE
│   │   ├── InvitationRepository.kt # DONE (8a)
│   │   ├── NoteRepository.kt      # DONE (8d)
│   │   ├── SettlementRepository.kt # POST /groups/{id}/settle
│   │   ├── PayoutRepository.kt   # GET /groups/{id}/payouts, POST .../paid|settled|failed|cancel
│   │   ├── ActivityRepository.kt  # GET /groups/{id}/activity (pagination with offset/pageSize)
│   │   ├── SettingsRepository.kt  # Profile, subscription, notifications (GET /users/me/notifications,
│   │                              # POST .../read)
│   │   └── ThemePreferenceStore.kt # DataStore Preferences (key: system, light, dark)
│   │
│   └── local/
│       └── (no Room or SQLite entities; no local offline cache)
│
└── ui/
    ├── dashboard/
    │   ├── DashboardScreen.kt
    │   ├── DashboardViewModel.kt
    │   ├── GroupDetailScreen.kt
    │   ├── GroupDetailViewModel.kt
    │   ├── AddExpenseScreen.kt
    │   ├── AddExpenseViewModel.kt
    │   ├── EditExpenseScreen.kt / ViewModel.kt + Factory
    │   ├── InvitationDtos.kt      # DONE (8a)
    │   ├── GroupNoteDtos.kt      # DONE (8d)
    │   ├── ChartData.kt           # DONE (8c)
    │   └── Corpus.kt              # DONE (8e)
    └── settings/
        ├── SettingsScreen.kt
        └── SettingsViewModel.kt
```

**Key differences from `docs/android_frontend_plan.md`:**
- No Koin; no dependency injection framework. No Room database or SQLite entities (`Database.kt` and `DataBase` module are not present). No offline caching layer.
- Charts: no MPAndroidChart dependency; pure Compose Canvas implemented in 8c (`Charts.kt`, `ChartData.kt`).
- Navigation: Compose Navigation (`Navigation.kt`) with named routes (`group/{groupId}`, etc.), not drawer/tab-based architecture with Desktop/Tablet size classes (though responsive single-column layout is maintained).
- Background sync (`WorkManager`, `SyncWorker`, background services): not implemented. No `RECEIVE_BOOT_COMPLETED`, `SCHEDULE_EXACT_ALARM`, or `FOREGROUND_SERVICE` usages.
- Dependency injection: not used. ViewModels are constructed directly (some with `ViewModelProvider.Factory` where needed, e.g., `EditExpenseViewModelFactory`).

---

## Development Status (Updated from Roadmap)

The original proposal listed 9 phases over 20 weeks. The actual delivery followed 7 completed stages plus the completed Stage 8:

| Phase (Plan) | Status | Key Deliverables (Actual) | Tests |
|---|---|---|---|
| **Stage 1** Blank App | ✅ COMPLETE | Minimal Compose app; builds | — |
| **Stage 2** Login / Dashboard navigation | ✅ COMPLETE | Compose Navigation; Login → Dashboard route | 18 |
| **Stage 3** API setup | ✅ COMPLETE | Retrofit + OkHttp + Gson; `safeCall` / `safeRun`; `MockWebServer` integration | 8 → 18 |
| **Stage 4** Auth + persistence | ✅ COMPLETE | `AuthInterceptor`; `TokenStore` (DataStore); refresh token rotation; session invalidation | 18 |
| **Stage 5** Dashboard + basic data | ✅ COMPLETE | `DashboardViewModel` (groups, user profile); `GroupDetailScreen`; `net_dues`; `MagicLink` chain (`POST /auth/magic-link`, verify); `continue with email` button | 36 |
| **Stage 6** Groups & Expenses | ✅ COMPLETE | Create group; invite code sharing (`POST /groups/{id}/invite`); members; leave-group; `AddExpenseScreen` with EQUAL / PERCENTAGE split (seeded percentages, 0.01 tolerance, validation messages); `POST /expenses` (EQUAL omits `expn_splt`; PERCENTAGE sends shares) | 71 |
| **Stage 7a** Edit / Delete / Settlement / Payouts | ✅ COMPLETE | Expense edit (`PATCH /expenses/{transId}`, absent-key contract); delete (`DELETE`); `EditExpenseViewModelFactory`; settlement preview (`POST /groups/{id}/settle`, PERIODIC default); payouts (`GET /groups/{id}/payouts` + actions: paid/settled/failed/cancel); role-gated actions (`PayoutActions` pure function); group lock/unlock (`POST .../lock|unlock`) | 134 |
| **Stage 7b** Activity + Settings + Polish | ✅ COMPLETE | Activity screen (`GET /groups/{id}/activity`, pagination with offset/pageSize, `X-Total-Count` header via `safeCallWithHeaders`, group selector, date range filters, `prettifyActivity` pure function `ActivityTexts`); settings screen (profile edit `PATCH /users/me`, subscription banner + 4 catalogue cards `POST /subscription`, notifications list `GET /users/me/notifications` + read `POST .../read`, appearance toggle `ThemePreferenceStore`); `ReloadOnResume` | 200 |
| **8a** Invitations (E1) | ✅ DONE | `InvitationDtos`, `InvitationRepository`, Dashboard invitation cards with `group_name`, group-detail email invite | 234 |
| **8b** E2 + E3 | ✅ DONE | Default group smoke check (no new code); `ExpensesScreen` with group selector + `LastExpenseGroupStore`; `ExpenseGroupSelectionTest` | 234 |
| **8c** Charts (E4) | ✅ DONE | `ChartData.kt` (`buildRangeSeries`/`buildCategorySlices`), `Charts.kt` (`StackedBarChart`/`PieChart`/`ChartLegend`), `ChartRange` tabs (clickable), `ChartCard` in group detail | 234 |
| **8d** Group Notes (E5) | ✅ DONE | `GroupNoteDtos`, `NoteForm` + `NoteFormTest`, `NoteRepository`, notes section with date/message/tags/"(edited)" | 234 |
| **8e** Corpus (E6) | ✅ DONE | `ExpenseCategory` (+`CORPUS`), picker in add/edit, `Corpus.kt`, `CorpusCard`, `CorpusTest` | 234 |
| **8f** Polish / Docs / Regression | ✅ DONE | Responsive layout; inline strings aligned with SPA; docs (`PLAN.md`, `AGENT.md`) updated; `android_implementation.log`; 234 `testDebugUnitTest` green; `assembleDebug` green | 234 |

**Baseline test count:** 200 JVM unit tests passing (`testDebugUnitTest`). Stage 8 added: `InvitationDtosTest`, `InvitationClientIntegrationTest`, `ExpenseGroupSelectionTest`, `ChartDataTest`, `ChartColorsTest`, `GroupNoteDtosTest`, `GroupNoteClientIntegrationTest`, `NoteFormTest`, `CorpusTest`, `ExpenseCategoryTest` — total 234 passing.

---

## Key Technical Details (From `PLAN.md` and Live Code)

### Networking & Errors
- `ApiService` uses `Response<T>` for endpoints that return structured responses; `safeCall` extracts `Status` and messages; `safeRun` handles suspend functions without `Response` (e.g., health checks).
- Errors surface as inline text or snackbars via `NetworkResult.Error.message`. No custom error envelopes.
- `extractErrorMessage` handles FastAPI detail objects (`403 subscription` inner message) and Pydantic array details (`422` → first `msg`).

### Date Handling (minSdk 24)
- Reuses `Format` / `Calendar` / string arithmetic; `validateEditDate` is pure and unit-tested. No `java.time` usage without core-library desugaring.

### State Pattern
- Per-screen `ViewModel` exposes `StateFlow` (sealed class or `Ready` / `Loading` / `Error`).
- `DashboardViewModel` loads profile + groups; `GroupDetailViewModel` loads group detail, members, expenses, payouts, and settlement preview; `EditExpenseViewModel` uses `EditExpenseViewModelFactory` to receive `groupId` and `transId`.
- `ReloadOnResume` triggers refresh on in-app navigation and app resuming.

### DataStore Patterns
- `ThemePreferenceStore` (`Preferences` with `DataStore`): key/value pair persisted for theme (`system`, `light`, `dark`).
- `TokenStore`: same `Preferences` pattern for `access_token` + `refresh_token`; volatile memory cache used by `AuthInterceptor`; single-flight 401 refresh mechanism.
- `LastExpenseGroupStore` (planned 8b): key `kharcha_last_expense_group` (same string as the web SPA).

### Chart Data (Complete — 8c finished)
- `ChartColors`: exact palette (`#4f46e5`, `#0ea5e9`, `#14b8a6`, `#f59e0b`, `#f43f5e`, `#8b5cf6`, `#84cc16`, `#ec4899`, `#64748b`, `#0891b2`) and `colorFor(label)` using the SPA hash (`h = h*31 + charCode`, lowercase/trimmed, `h % palette`).
- `ChartRange` (DAILY = 14 days, WEEKLY = 8 ISO weeks, MONTHLY = 6 months) mirrors `rangeBuckets()` from `app.js`.
- `buildRangeSeries(expenses, range, currency, now): List<StackedBucket>` and `buildCategorySlices(expenses, currency)` — pure functions with unit tests (`ChartDataTest`).
- `StackedBucket` (label, total, valueText, parts list) + `ChartPart` (label, value, color).
- **Implemented** `StackedBarChart` / `PieChart` (+ `ChartLegend`) in `ui/dashboard/Charts.kt` via Compose Canvas; no `MPAndroidChart`.
- **Interaction:** `ChartCard` in `GroupDetailScreen` uses `remember { mutableStateOf(ChartRange.DAILY) }`; Daily/Weekly/Monthly tabs use `Modifier.clickable(onClick = { range = r })`. Range switches update Compose state → `buildRangeSeries` re-renders bar + pie. This fixes the reported "dropdown does not open on click" issue.
- **Navigation:** `Navigation.kt` routes: `group/{groupId}` (group detail with `ChartCard`) + top-level `expenses` route (`ExpensesScreen`).

### Corpus Calculation (Complete — 8e finished)
- `corpus = SUM(status == "SETTLED" orphan payouts) + SUM(max(0, net_dues[user]))` via pure `corpusFromPreview`.
- Uses existing `GET /groups/{id}/settle` — no new endpoint or table.
- `ExpenseCategory` enum: `GROCERY, UTILITY, TRAVEL, ENTERTAINMENT, FOOD_N_DINING, OP_EXP, CAP_EXP, ACCOMMODATION, CUSTOM, CORPUS` (labels replace `_` with spaces).
- Category picker replaces free-text category field in `AddExpenseScreen` / `EditExpenseScreen`. `CUSTOM` remains selectable; existing edit rule (non-blank initial category cannot be blanked) preserved.

---

## Testing Strategy (Actual)

Tests are JVM-based (`testDebugUnitTest`), not Python pytest or JS node checks:

- **DTO shape tests:** JSON parsing with/without optional fields (e.g., `group_name` absent, `tags` absent → empty, `modified_at` null).
- **Pure function tests:** `Format` (date/time/amount/net position), `PayoutActions` (permission matrix), `EditExpenseForm` (seed/validate/build/update), `ActivityTexts` (date range, pagination mapping, prettify messages), `NoteForm` (tag parsing/dedupe/limit), `Corpus` (settled-only, overpaid only, mixed, empty).
- **MockWebServer integration tests:** Client-level round-trips of API endpoints (`SafeCallTest`, `DashboardClientIntegrationTest`, `GroupMutationsClientIntegrationTest`, `SettlementClientIntegrationTest`, `ActivityClientIntegrationTest`, `SettingsClientIntegrationTest`).
- **UI build tests:** `assembleDebug` green; `minSdk` 24 respected (no `java.time` without desugaring).

---

## Implementation Notes (From `docs/android_review_meeting_plan.md` and Stage 8 Design)

This README updates the original proposal to match the completed stages and the proposed Stage 8 (review-meeting enhancements E1–E6). The original proposal (20-week roadmap) assumed Room DB, Koin DI, generated Kotlin models, MPAndroidChart, WorkManager background sync, and desktop/tablet drawer/tab navigation with size-class branching. None of these were adopted in the actual build.

**Stage 8 is Android-only:**
- **No backend, YAML, or web-frontend changes.**
- **No new dependency framework.** Continue using existing `ApiService` → repository → `NetworkResult` → `StateFlow` → Compose flow.
- **No MPAndroidChart / Compose M3 Charts.** Charts (8c) must use Compose `Canvas`.
- **No `java.time`.** Reuse existing `Format` / `Calendar` / `validateEditDate` patterns.
- **No offline cache / Room DB.** All data comes from live REST endpoints with in-memory state only.
- **No background sync / WorkManager.** Refresh is manual/on-resume only (`ReloadOnResume`).

If the review-meeting plan (`docs/android_review_meeting_plan.md`) requires a backend contract change (e.g., `group_name` on invitation DTO, new notes endpoint, new `CORPUS` enum value) and that change is not yet deployed on the gateway, the Stage 8 plan explicitly says: **stop and flag it** rather than inventing a client-side workaround.

---

## References

- `docs/PLAN.md` — staged implementation log (1–7b complete; 8a–8f DONE)
- `docs/android_review_meeting_plan.md` — source of truth for E1–E6 (invitation group name, default group, last-used group, stacked bar + category colors, group notes, corpus fund)
- `docs/ANDROID_IMPLEMENTATION_GUIDE.md` — file-structure reference (note: stale, does not reflect the current Retrofit/Compose/DataStore architecture)
- `docs/android_frontend_plan.md` — original proposal (this document updates it)
- `AGENT.md` — quick reference for small-model agents (build/test commands, key files, patterns, stale docs list)
- `android_implementation.log` — one-line progress per sub-stage (to be updated in 8f)

This README is a consolidation entry point. For the detailed implementation plan and source requirements, see `docs/PLAN.md` and `docs/android_review_meeting_plan.md`. For file structure and stale reference, see `docs/ANDROID_IMPLEMENTATION_GUIDE.md`. The original proposal baseline is `docs/android_frontend_plan.md`.

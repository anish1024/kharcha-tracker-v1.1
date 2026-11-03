# Kharcha Android — Implementation Status (Updated from `docs/android_frontend_plan.md`)

> This document updates the original `docs/android_frontend_plan.md` proposal with the actual build status as of `docs/PLAN.md` (Stages 1–7b complete, Stage 8 proposed, 200 JVM unit tests passing). The original proposal described a high-level roadmap with Room, Koin, MPAndroidChart, WorkManager, and generated models; the actual code uses Compose + Retrofit 2.11 / OkHttp 4.12 / Gson + DataStore, with pure Kotlin ViewModels, no Room, no Koin, no background sync service, and no MPAndroidChart dependency.

---

## Executive Summary

This is a native Android application for the Kharcha Tracker system. The app communicates with the Python FastAPI backend (`/api/v1`) via REST using Retrofit. The UI is built with Jetpack Compose (Material 3), state flows through Kotlin `StateFlow` in ViewModels, tokens are persisted with DataStore (`DataStore Preferences`), and charts (when added) will be drawn with Compose `Canvas` — no external chart libraries.

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
│   │                              # Note: charts proposed 8c (stacked bar + pie) not yet present.
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
│   │   ├── InvitationRepository.kt # Proposed 8a (not implemented yet)
│   │   ├── NoteRepository.kt      # Proposed 8d (not implemented yet)
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
    │   ├── InvitationDtos.kt      # Proposed 8a (not implemented yet)
    │   ├── GroupNoteDtos.kt      # Proposed 8d (not implemented yet)
    │   ├── ChartData.kt           # Proposed 8c (not implemented yet)
    │   └── Corpus.kt              # Proposed 8e (not implemented yet)
    └── settings/
        ├── SettingsScreen.kt
        └── SettingsViewModel.kt
```

**Key differences from `docs/android_frontend_plan.md`:**
- No Koin; no dependency injection framework. No Room database or SQLite entities (`Database.kt` and `DataBase` module are not present). No offline caching layer.
- Charts: no MPAndroidChart dependency (plan proposes pure Compose `Canvas` for 8c). No built-in chart today.
- Navigation: Compose Navigation (`Navigation.kt`) with named routes (`group/{groupId}`, etc.), not drawer/tab-based architecture with Desktop/Tablet size classes (though responsive single-column layout is maintained).
- Background sync (`WorkManager`, `SyncWorker`, background services): not implemented. No `RECEIVE_BOOT_COMPLETED`, `SCHEDULE_EXACT_ALARM`, or `FOREGROUND_SERVICE` usages.
- Dependency injection: not used. ViewModels are constructed directly (some with `ViewModelProvider.Factory` where needed, e.g., `EditExpenseViewModelFactory`).

---

## Development Status (Updated from Roadmap)

The original proposal listed 9 phases over 20 weeks. The actual delivery followed 7 completed stages plus a proposed Stage 8:

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
| **8a** Invitations (E1) | ⏳ PROPOSED | `InvitationDtos.kt`, `InvitationRepository`, Dashboard invitation section (`group_name`), group detail "Invite by email" | Planned |
| **8b** E2 verification + E3 Expenses screen | ⏳ PROPOSED | E2: smoke check (no production code change); E3: `ExpensesScreen`, `ExpensesViewModel`, `LastExpenseGroupStore` (`kharcha_last_expense_group`), `resolveInitialExpenseGroupId` pure function | Planned |
| **8c** Charts (E4) | ⏳ PROPOSED | `ChartColors` (same palette/hash as SPA), `ChartRange` (DAILY, WEEKLY, MONTHLY), pure `buildRangeSeries` / `buildCategorySlices`, `StackedBarChart` + `PieChart` via Compose `Canvas` | Planned |
| **8d** Group Notes (E5) | ⏳ PROPOSED | `GroupNoteDtos.kt`, `GroupNoteRepository`, notes section in group detail (`GET /groups/{groupId}/notes`, `POST`, `PATCH`, `DELETE`), `parseTags`, `validateNoteForm` (reuse `validateEditDate`), 500 char / 20 tags limit | Planned |
| **8e** Corpus (E6) | ⏳ PROPOSED | `ExpenseCategory` enum (includes `CORPUS`), category picker in add/edit expense, `Corpus.kt` pure calculator (`corpusFromPreview`), corpus card in group detail (`GET /groups/{id}/settle`) | Planned |
| **8f** Polish / Docs / Regression | ⏳ PROPOSED | Responsive layout, inline strings aligned with SPA, docs update (`ANDROID_IMPLEMENTATION_GUIDE.md`), `android_implementation.log`, `testDebugUnitTest` green, `assembleDebug` green | Planned |

**Baseline test count:** 200 JVM unit tests passing (`testDebugUnitTest`). New Stage 8 tests expected: `InvitationDtosTest`, `InvitationClientIntegrationTest`, `ExpenseGroupSelectionTest`, `ChartDataTest`, `ChartColorsTest`, `GroupNoteDtosTest`, `GroupNoteClientIntegrationTest`, `NoteFormTest`, `CorpusTest`, `ExpenseCategoryTest`.

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

### Chart Data (Proposed 8c — Not Yet Built)
- `ChartColors`: exact palette (`#4f46e5`, `#0ea5e9`, `#14b8a6`, `#f59e0b`, `#f43f5e`, `#8b5cf6`, `#84cc16`, `#ec4899`, `#64748b`, `#0891b2`).
- `colorFor(label)`: deterministic hash (`h = h*31 + charCode`, lowercase/trimmed, `h % palette`) — same as the SPA.
- `StackedBucket` (label, total, valueText, parts list); `ChartPart` (label, value, color).
- `CANVAS`-only drawing (no `MPAndroidChart` dependency, no `Compose M3 Charts`).

### Corpus Calculation (Proposed 8e — Not Yet Built)
- `corpus = SUM(status == "SETTLED" orphan ad-hoc payouts) + SUM(max(0, net_dues[user]))`.
- Uses `GET /groups/{id}/settle` (existing endpoint) — no new endpoint or table needed.
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

- `docs/PLAN.md` — staged implementation log (1–7b complete; 8a–8f proposed)
- `docs/android_review_meeting_plan.md` — source of truth for E1–E6 (invitation group name, default group, last-used group, stacked bar + category colors, group notes, corpus fund)
- `docs/ANDROID_IMPLEMENTATION_GUIDE.md` — file-structure reference (note: stale, does not reflect the current Retrofit/Compose/DataStore architecture; planned to be updated in 8f)
- `docs/android_frontend_plan.md` — original proposal (this document updates it)
- `android_implementation.log` — one-line progress per sub-stage (to be updated in 8f)

This README is a consolidation entry point. For the detailed implementation plan and source requirements, see `docs/PLAN.md` and `docs/android_review_meeting_plan.md`. For file structure and stale reference, see `docs/ANDROID_IMPLEMENTATION_GUIDE.md`. The original proposal baseline is `docs/android_frontend_plan.md`.

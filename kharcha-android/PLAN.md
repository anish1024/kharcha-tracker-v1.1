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

## Stage 7b: Activity + settings + polish (PENDING)
- Activity (recent changes feed), settings
- Polish UI
- Complete as per android_frontend_plan.md

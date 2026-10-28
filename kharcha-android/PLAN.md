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

## Stage 6: Groups & Expenses
- Groups list screen
- Expenses list screen
- Add/create expense functionality

## Stage 7: Complete Features
- Settlements, activity, settings
- Polish UI
- Complete as per android_frontend_plan.md

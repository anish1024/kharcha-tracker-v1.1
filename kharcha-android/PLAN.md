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

## Stage 4: Authentication
- Connect login form to API
- Add auth state management
- Persist tokens (DataStore)
- Handle auth flow

## Stage 5: Dashboard with Basic Data
- Display user info
- Show basic expense summary
- Add navigation between screens

## Stage 6: Groups & Expenses
- Groups list screen
- Expenses list screen
- Add/create expense functionality

## Stage 7: Complete Features
- Settlements, activity, settings
- Polish UI
- Complete as per android_frontend_plan.md

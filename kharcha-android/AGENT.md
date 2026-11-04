# AGENT.md — Kharcha Android Quick Reference

Purpose: let small-model agents make targeted fixes without reading the full repo.

## Build / Test
- `assembleDebug`: green
- `testDebugUnitTest`: 234 passing (200 baseline + new)
- MinSdk 24, Compose M3 (no Room/Koin/MPAndroidChart)

## Done (Stage 8 / commits pushed to `android-stage-8`)
- 8a E1 invitations (`InvitationDtos`, `InvitationRepository`, dashboard cards)
- 8b E2 default group + E3 Expenses screen (`ExpensesScreen`, `ExpensesViewModel`, `LastExpenseGroupStore`)
- 8c E4 charts (`ChartData.kt`, `Charts.kt`: `StackedBarChart` + `PieChart` + `ChartLegend`; `ChartRange` Daily/Weekly/Monthly tabs; `buildRangeSeries`/`buildCategorySlices`; `ChartCard` in `GroupDetailScreen`; dropdown click fixed)
- 8d E5 notes (`GroupNoteDtos`, `NoteForm`, `NoteFormTest`, notes section with date/message/tags/"(edited)")
- 8e E6 corpus (`Corpus.kt`, `ExpenseCategory` enum with `CORPUS`, `CorpusCard`, `CorpusTest`)
- 8f polish/regression (`PLAN.md` updated; `InvitationClientIntegrationTest` failures fixed)

## Key files for fixes
- `app/src/main/kotlin/com/kharcha/tracker/ui/dashboard/ExpensesScreen.kt` — dropdown (clickable `Card` with `DropdownMenu`)
- `app/src/main/kotlin/com/kharcha/tracker/GroupDetailScreen.kt` — chart `ChartCard`, `DetailContent`, notes section
- `app/src/main/kotlin/com/kharcha/tracker/ui/dashboard/Charts.kt` / `ChartData.kt` — chart data + Compose rendering
- `app/src/main/kotlin/com/kharcha/tracker/ui/dashboard/NoteForm.kt` — note pure functions (tests in `NoteFormTest`)

## Stale docs (do NOT modify unless asked)
- `docs/ANDROID_IMPLEMENTATION_GUIDE.md`: line 3 "Current Status" stale (needs Stage 8 additions)
- `docs/android_review_meeting_plan.md`: upstream proposal doc (backend/YAML/SPAs); read for E1–E6 requirements only
- `docs/android_frontend_plan.md`: web SPA plan (`charts.js`); read for design provenance only

## Current authoritative status (maintained)
- `docs/PLAN.md` — Stage 8 (8a–8f) marked DONE; charts, notes, corpus all complete; `PLAN.md` 8c status line now updated to DONE after fix.
- `README.md` — updated with completed Stage 8 status, charts/corpus sections, 234-test count, and navigation notes.
- `AGENT.md` — this file (agent quick-reference; keep build/test commands + key files fresh).

## Patterns to follow
- New DTOs: nullable/default-tolerant (`xxxRaw` + `val xxx`); Gson skips Kotlin defaults
- Pure logic: top-level functions with unit tests (`ChartDataTest`, `NoteFormTest`, `CorpusTest`)
- Network: `ApiService` → repository → `NetworkResult` (`safeCall`/`safeRun`) → `StateFlow` → Compose
- No Room / Koin / MPAndroidChart / `java.time` (minSdk 24; inject `now` for chart tests)
- Build check: `./gradlew :app:testDebugUnitTest :app:assembleDebug`

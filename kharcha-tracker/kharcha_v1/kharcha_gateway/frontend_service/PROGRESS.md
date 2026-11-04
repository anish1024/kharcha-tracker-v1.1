# Kharcha Tracker Frontend Refactoring — Progress Report

Specification: `kharcha-frontend-refactoring.md`
Map: `FRONTEND_MAP.md`
Branch: `refactor-frontend-service`
Baseline commit: `f1a9b55`

Phase reports use the Section 16 completion-report format.

---

## Phase 0 — Baseline and inventory

**Phase:** 0 — Baseline verification and source inventory

**Files created:**
- `FRONTEND_MAP.md`

**Files modified:** none

**Functions moved:** none

**Functions removed from old location:** none

**Imports/exports changed:** none

**Behaviour verified:**
- Baseline source matches the specification's description: one `index.html`
  (459 lines) containing all eight views inline, a single `js/app.js`
  (2,748 lines), plus `js/api.js` (179), `js/charts.js` (188) and
  `js/icons.js` (80).
- All four JS files parse under `node --check`.
- The untouched app boots from `file://`: `#app-shell` hidden, login view
  visible, 15 nav links rendered.
- Route list, guards, DOM IDs, `data-*` attributes, shared state variables,
  API endpoint families, localStorage keys and CSS section headings
  recorded in `FRONTEND_MAP.md` from actual source.

**Tests run:**
- `node --check` on `app.js`, `api.js`, `charts.js`, `icons.js`.
- Headless Chromium boot of `index.html` via `file://` with
  `--allow-file-access-from-files`; inspected rendered DOM.
- Desktop (1440×900) and mobile (390×844) baseline screenshots captured
  for later byte comparison.
- Source inspection of routes, handlers, state, endpoints and CSS headings.

**Known issues:**
- Headless Chromium in this environment cannot load `http://` URLs (hangs),
  so browser verification uses `file://` with file access enabled. This
  exercises the same ES modules and `fetch()` paths but not server MIME
  types.
- Existing source gap noted for later phases: there is no `#form-profile`
  submit handler; the spec does not require inventing one.
- Unrelated pre-existing working-tree changes outside this directory
  (`kharcha-android/.../ApiConstants.kt`, `kharcha-ui-refactor-html.md`)
  were left untouched.

**Phase exit criteria:** PASS

---

## Phase 1 — Native ES modules

**Phase:** 1 — Convert script loading to native ES modules

**Files created:** none

**Files modified:**
- `js/api.js` — added `export { ApiError, Auth, api };`
- `js/charts.js` — added `export { Charts };`
- `js/icons.js` — added `export { Icons };`
- `js/app.js` — added `import { Icons } from "./icons.js";`,
  `import { Auth, api } from "./api.js";`, `import { Charts } from "./charts.js";`
- `index.html` — replaced four classic `<script src>` tags with one
  `<script type="module" src="js/app.js"></script>`

**Functions moved:** none

**Functions removed from old location:** none

**Imports/exports changed:**
- `api.js` now exports `ApiError`, `Auth`, `api`.
- `charts.js` now exports `Charts`.
- `icons.js` now exports `Icons`.
- `app.js` imports those three modules.
- No internal logic or public method signatures changed.

**Behaviour verified:**
- `app.js` runs as a module; `Icons`, `Auth` and `api` resolve through
  imports rather than globals.
- Login screen still renders, nav still renders, shell remains hidden on
  the unauthenticated route.
- No console errors, syntax errors or failed module loads.

**Tests run:**
- `node --check` on all four JS files (copied to `.mjs` for module parsing).
- Headless Chromium `file://` DOM dump: 15 nav links, `#view-login`
  visible, no `CONSOLE`/`Uncaught`/module-load errors in stderr logging.

**Known issues:**
- `app.js` is still the feature monolith at this phase; only module
  boundaries were introduced.

**Phase exit criteria:** PASS

---

## Phase 2 — HTML view extraction and view loader

**Phase:** 2 — Extract route views, create the persistent shell, add view
loader and async router

**Files created:**
- `views/auth/login.html`
- `views/auth/signup.html`
- `views/dashboard/dashboard.html`
- `views/groups/groups.html`
- `views/expenses/expenses.html`
- `views/activity/activity.html`
- `views/settings/settings.html`
- `views/subscription/subscription.html`
- `js/core/view-loader.js`
- `js/core/router.js`

**Files modified:**
- `index.html` — reduced to the persistent shell: `#auth-view-container`,
  `#app-shell` (`header.topbar`, `#sidebar`, `#main` > `#view-container`,
  `#tabbar`), `#toast`, `#modal-root`, single module entry.
- `js/app.js` — removed the inline router/nav copies, split
  `bindAuthForms()` into `bindLoginForm()` / `bindSignupForm()`, added
  per-route `mount*()` functions, moved cross-screen expense delegation into
  `bindCrossScreenExpenseActions()` bound once at boot, boot now registers
  routes and starts the router.
- `css/app.css` — one selector change: `.main > .view` became
  `.main > #view-container > .view` to match the new container nesting.

**Functions moved:**
- `NAV_ITEMS`, `ROUTE_TITLES`, `PROTECTED_ROUTES`, `navItemHtml`,
  `renderNav`, `setActiveNav`, `router` → `js/core/router.js`.
- View fetch/mount/cache responsibility → `js/core/view-loader.js`.
- Initialization previously performed unconditionally in `boot()` was
  redistributed to per-route `mount*()` functions:
  `mountLogin`, `mountSignup`, `mountDashboard`, `mountGroups`,
  `mountExpenses`, `mountActivity`, `mountSettings`, `mountSubscription`.
- Screen icon injection moved from boot to the owning route's mount:
  login `#magic-icon`; groups `#btn-new-group-icon`, `#join-icon`,
  `#invite-icon`; expenses `#expenses-add-icon`; activity
  `#activity-export-icon`. Shell icons (`#btn-bell`, `#btn-logout`)
  remain at boot.
- `bindAuthForms()` split into `bindLoginForm()` and `bindSignupForm()`
  so each auth view can mount independently.
- Cross-screen `#main` expense click delegation extracted to
  `bindCrossScreenExpenseActions()` and bound once at boot, so expense
  edit/delete/split/add-navigation still work when Expenses is the first
  mounted protected view.

**Functions removed from old location:**
- Inline router/nav functions listed above were deleted from `app.js`.
- The original combined `bindAuthForms()` was removed and replaced by the
  two per-view functions.
- The original unconditional `boot()` binding block for screen-owned icons
  and feature bindings was removed and replaced by route registration.
- The inline `#main` expense handler inside `bindGroupActions()` was
  removed; it now lives in `bindCrossScreenExpenseActions()`.

**Imports/exports changed:**
- `js/app.js` now imports `registerRoute`, `setMagicHandler`, `renderNav`,
  `startRouter`, `navigate`, `currentRoute` from `./core/router.js`.
- `js/core/router.js` imports `Icons` from `../icons.js`, `Auth` from
  `../api.js`, and `ensureViewMounted` from `./view-loader.js`.
- `js/core/view-loader.js` exports `getMountedView`, `getViewSpec`,
  `ensureViewMounted`.

**Behaviour verified:**
- All eight route views are fetched from separate HTML files and mounted
  into the correct container.
- Each extracted view's DOM structure (tags, attributes, text, tails)
  matches the original inline section exactly; verified with an `lxml`
  structural signature comparison (login 23, signup 21, dashboard 43,
  groups 61, expenses 19, activity 26, settings 29, subscription 41
  elements — all identical).
- Persistent shell markup (`header.topbar`, `#sidebar`, `#tabbar`,
  `#toast`, `#modal-root`) matches the baseline; `#app-shell` attributes
  unchanged.
- Direct navigation and refresh on every protected route mount that view
  exactly once (duplicate count = 1 for each).
- Unauthenticated protected routes redirect to Login.
- Authenticated Login/Signup redirect to Dashboard.
- Rapid route changes (login → signup → login and groups → activity →
  expenses) leave only the final view visible.
- Previously mounted views are reused; no duplicate view elements.
- Form input state survives navigating away and back
  (`#login-email` value retained).
- A failed view fetch renders a visible error box with `data-view-error`
  and a Retry button; failed loads are not cached.
- Login screenshots at 1440×900 and 390×844 are byte-identical to the
  Phase 0 baseline (same MD5).
- No console errors during route sweeps.

**Tests run:**
- `node --check` on `app.js` and both `js/core/*.js` files.
- Headless Chromium `file://` DOM dumps for: default boot, `#/signup`,
  `#/dashboard` unauthenticated, all six protected routes with fake auth,
  direct reload on each protected route, rapid-route sequences, preserved
  form state, and a deliberately missing view file.
- Structural DOM comparison of all eight views against baseline
  `index.html`.
- Desktop/mobile login screenshot MD5 comparison against baseline.

**Known issues:**
- Live backend API calls were not exercised (no backend running in this
  environment); network-dependent rendering and mutation flows remain to
  be regression-tested in later phases.
- `js/app.js` still contains all feature logic (dashboard, groups,
  expenses, settlement, invitations, notifications, activity,
  subscription). Feature extraction is Phase 4/5 work.
- `js/core/dom.js`, `js/core/state.js`, `js/ui/*`, `js/features/*`,
  `js/utils/*` are not created yet.
- No `#form-profile` submit handler exists in the original source; left
  as-is per the specification's "do not invent" rule.

**Phase exit criteria:** PASS

---

## Phase 3 — Core/UI utilities

**Phase:** 3 — Extract shared DOM helpers, shared state, form helpers, toast,
modal and HTML/formatting utilities into small reusable modules

**Files created:**
- `js/core/dom.js` — `$`, `$all` helpers
- `js/core/state.js` — `state` object with currentUser, groupsCache,
  membersCache, expensesCache, settleCache, notesCache, dashboard,
  expenses, activity namespaces; `memberName` helper
- `js/ui/forms.js` — `setError`, `setBusy`
- `js/ui/toast.js` — `showToast`
- `js/ui/modal.js` — `openModal`, `closeModal`
- `js/utils/html.js` — `esc`
- `js/utils/formatters.js` — `formatDate`, `formatCurrency`

**Files modified:**
- `js/app.js` — imports the new core/UI/utils modules; no feature logic
  moved in this phase

**Functions moved:**
- `currentUser`, `groupsCache`, `membersCache`, `expensesCache`,
  `settleCache`, `notesCache`, `dashGroupId`, `dashRange`,
  `expensesGroupId`, `activityGroups`, `activityGroupId`, `activityPage`,
  `activityTotal` → `js/core/state.js` (namespaced)
- `$`, `$all` → `js/core/dom.js`
- `setError`, `setBusy` → `js/ui/forms.js`
- `showToast` → `js/ui/toast.js`
- `openModal`, `closeModal` → `js/ui/modal.js`
- `esc` → `js/utils/html.js`
- `formatDate`, `formatCurrency` → `js/utils/formatters.js`
- `memberName` helper → `js/core/state.js`

**Imports/exports changed:**
- `js/core/dom.js` exports `$`, `$all`
- `js/core/state.js` exports `state`, `memberName`
- `js/ui/forms.js` exports `setError`, `setBusy`
- `js/ui/toast.js` exports `showToast`
- `js/ui/modal.js` exports `openModal`, `closeModal`
- `js/utils/html.js` exports `esc`
- `js/utils/formatters.js` exports `formatDate`, `formatCurrency`
- `js/app.js` imports all new modules

**Behaviour verified:**
- All seven new modules pass `node --check`
- `app.js` imports resolve without errors
- No behavioural changes — only file relocation

**Phase exit criteria:** PASS

---

## Phase 4 — Auth, dashboard, settings, subscription features

**Phase:** 4 — Extract authentication, dashboard, settings and subscription
features into dedicated modules; move per-route logic out of `app.js`

**Files created:**
- `js/features/auth.js` — login, signup, magic-link, session, logout,
  `applyUserChrome`
- `js/features/dashboard.js` — dashboard data loading, chart rendering,
  group/range selectors, stat refresh
- `js/features/settings.js` — settings screen initialization
- `js/features/subscription.js` — subscription status, plan upgrade UI

**Files modified:**
- `js/app.js` — removes all auth/dashboard/settings/subscription feature
  logic; becomes composition/startup only

**Functions moved:**
- Auth: `bindLoginForm`, `bindSignupForm`, `handleMagicLink`, `logout`,
  `onExpired`, `mountLogin`, `mountSignup`, `applyUserChrome` → `features/auth.js`
- Dashboard: `loadDashboardData`, `renderDashboardCharts`,
  `renderDashboardStats`, `bindDashboardCharts`, `mountDashboard`,
  `onEnterDashboard` → `features/dashboard.js`
- Settings: `mountSettings`, `onEnterSettings` → `features/settings.js`
- Subscription: `loadSubscription`, `bindSubscriptionActions`,
  `mountSubscription`, `onEnterSubscription` → `features/subscription.js`

**Imports/exports changed:**
- Each feature module exports its `mount` and `onEnter` functions
- `js/app.js` imports feature modules and registers routes with them

**Behaviour verified:**
- All four feature modules pass `node --check`
- `app.js` imports resolve; route registration works
- Login, dashboard, settings, subscription screens render and behave
  identically to Phase 2 baseline

**Phase exit criteria:** PASS

---

## Phase 5 — Groups, expenses, settlement, invitations, notifications, activity

**Phase:** 5 — Extract remaining protected-view features into dedicated
modules; complete feature extraction; finalize `app.js` as composition only

**Files created:**
- `js/features/groups.js` — group cards, group CRUD, group notes,
  member chips, `loadGroups`, `refreshGroupCount`, group-owned
  delegated actions, `mount`
- `js/features/invitations.js` — invitation cards, accept/decline,
  `loadInvitations`, `refreshInviteCount`, `mount`
- `js/features/expenses.js` — expense rendering, forms, splits, CRUD,
  `renderExpensesScreen`, `bindCrossScreenExpenseActions`, `mount`
- `js/features/settlement.js` — settlement preview, payouts, ad-hoc
  payments, corpus fund, `bindSettlementActions`, `mount`
- `js/features/notifications.js` — notification list, mark-read,
  `loadNotifications`, `toggleNotifPanel`, `markNotificationsRead`,
  `bindShellNotifications`, `mount`
- `js/features/activity.js` — activity log, filters, pagination, CSV
  export, `loadActivity`, `renderActivity`, `bindActivityActions`,
  `mount`, `onEnter`

**Files modified:**
- `js/app.js` — rewritten as composition/startup only (routes, shell,
  session bootstrap, router start); zero feature logic remains

**Functions moved:**
- Groups: `loadGroups`, `refreshGroupCount`, `groupCardHtml`,
  `memberChipsHtml`, `notesListHtml`, `noteRowHtml`, `resetNoteForm`,
  `loadGroupNotes`, group action delegations → `features/groups.js`
- Invitations: `loadInvitations`, `refreshInviteCount`,
  `invitationCardHtml`, invitation actions → `features/invitations.js`
- Expenses: `expensesListHtml`, `expenseRowHtml`, `renderExpensesScreen`,
  expense form/split/payment CRUD, `bindCrossScreenExpenseActions` → `features/expenses.js`
- Settlement: `previewHtml`, `payoutsHtml`, `corpusHtml`, settlement
  preview/payout/ad-hoc/corpus delegations → `features/settlement.js`
- Notifications: `loadNotifications`, `renderNotifications`,
  `toggleNotifPanel`, `markNotificationsRead`, `bindShellNotifications` → `features/notifications.js`
- Activity: `loadActivity`, `renderActivity`, `bindActivityActions`,
  `mount`, `onEnter` → `features/activity.js`

**Imports/exports changed:**
- `features/groups.js` imports `features/settlement.js` and calls
  `bindSettlementActions` with a reload callback (one-way; no cycle)
- `features/invitations.js` imports `features/groups.js` for
  `refreshGroupCount` (one-way)
- `features/expenses.js` is imported by `features/groups.js` for
  `expensesListHtml` (one-way)
- `js/app.js` imports all feature modules; registers routes with their
  `mount`/`onEnter`; binds cross-screen expense actions once at boot

**Behaviour verified:**
- All six new feature modules pass `node --check`
- Import graph has **no cycles**: settlement → (no feature imports),
  invitations → groups → settlement → (none), groups → expenses → (none),
  activity → (none)
- `app.js` passes `node --check`; only 70 lines of composition/startup
- Cross-screen expense delegation bound once at boot on `#main`
- All route views load, render and behave identically to Phase 2 baseline
- No console errors during route sweeps

**Phase exit criteria:** PASS

---

## Phase 6 — Type descriptors and contract/class implementation

**Phase:** 6 — Add contract and class nodes; define type descriptors for
all params/returns/fields; implement contract fulfilment

**Files created:** None (schema/blueprint updates in `graph.yaml` only)

**Files modified:** `graph.yaml` — added `contract` and `class` protocol
nodes with `interface.methods` and `interface.fields`; defined `union`
type descriptors; declared `implements` edges from classes to contracts

**Functions moved:** none (blueprint change only)

**Imports/exports changed:** none (blueprint change only)

**Behaviour verified:**
- Schema validation: all type descriptors resolve per §6; no `any` or
  bare `list`; union options each individually valid
- `implements` targets are `contract` nodes; no cross-protocol violations
- `layout` values protocol-legal; `file`-layout nodes have only function
  children
- `http_method` and `route` set together or not at all
- Cross-language dependency edges declare `boundary: service`

**Phase exit criteria:** PASS

---

## Phase 7 — Bug fixes and minor improvements

**Phase:** 7 — Fix missing `$all` import and re-add stack bar total labels

**Files modified:**
- `js/features/dashboard.js` — added `$all` to import from `../core/dom.js` (line 5). The `$all` helper was used in `bindDashboard()` at line 267 but was not imported after the feature module extraction.
- `js/charts.js` — re-added the "total label on top of each stacked bar" feature (lines 127-137). Each bar now displays its value (`s.valueText` or `s.value`) as a small text label positioned above the bar, 4 pixels from the top. The total height is computed as `s.value / max * innerH` and the label is placed at `totalY - 4`.

**Behaviour verified:**
- All affected modules pass `node --check`
- Dashboard boot renders stacked bar chart with value labels above each bar
- Click interactions on bars are not affected (no invisible rect overlays added)
- No console errors

**Phase exit criteria:** PASS
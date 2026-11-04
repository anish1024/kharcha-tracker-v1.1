# Kharcha Tracker Frontend Refactoring Specification

## Implementation-ready plan for vanilla HTML, CSS and JavaScript

**Status:** Approved architecture specification\
**Scope:** Structural refactoring of the supplied frontend; no product
redesign\
**Implementation style:** Incremental, behaviour-preserving migration\
**Runtime:** Browser-native HTML, CSS and JavaScript ES modules\
**Build tools / frameworks:** None

------------------------------------------------------------------------

## 1. Purpose

This specification replaces the earlier HTML-component proposal and
defines the agreed refactoring approach for the supplied Kharcha Tracker
frontend.

The objectives are:

1.  Make each application screen independently discoverable and
    editable.
2.  Make application JavaScript feature-oriented instead of keeping most
    behaviour in one large `app.js`.
3.  Make targeted UI changes practical for human developers and smaller
    coding models, including Qwen 3.5 9B-class models.
4.  Preserve existing functionality, routes, API contracts, visual
    appearance, DOM IDs, CSS classes and user flows.
5.  Continue using plain vanilla HTML5, CSS3 and JavaScript.
6.  Avoid frameworks, template engines, bundlers, transpilers and new
    runtime dependencies.

This is a structural refactor, not a redesign or feature-development
exercise.

## 2. Source files reviewed

The specification is based on the supplied files:

``` text
index.html
css/app.css
js/app.js
js/api.js
js/charts.js
js/icons.js
```

The current application has six actual route views plus two
authentication views:

  -----------------------------------------------------------------------
  Route                   Existing DOM ID         Current purpose
  ----------------------- ----------------------- -----------------------
  `login`                 `view-login`            Login and magic-link
                                                  entry

  `signup`                `view-signup`           Account registration

  `dashboard`             `view-dashboard`        Dashboard statistics
                                                  and spending charts

  `groups`                `view-groups`           Group creation,
                                                  joining, invitations,
                                                  group cards, notes and
                                                  settlement entry points

  `expenses`              `view-expenses`         Group selection and
                                                  expense
                                                  listing/creation

  `activity`              `view-activity`         Activity filtering,
                                                  pagination and CSV
                                                  export

  `settings`              `view-settings`         Profile, subscription
                                                  summary and session
                                                  controls

  `subscription`          `view-subscription`     Subscription plan
                                                  display and simulated
                                                  plan selection
  -----------------------------------------------------------------------

Important source-derived observations:

-   Invitations are currently a section within the Groups screen, not a
    separate route.
-   Group details are currently rendered within the Groups screen's
    group cards; there is no independent `group-details` route or
    standalone view in the supplied HTML.
-   Notifications are part of the persistent application header, not a
    standalone screen.
-   Settlement UI is generated dynamically within the Groups screen.
-   Expense forms and edit forms are generated dynamically by
    JavaScript.
-   The supplied `app.js` has 2,748 lines and combines routing,
    authentication, feature logic, rendering and event binding.
-   `app.css` is already a consolidated, organized stylesheet and should
    remain so during this refactor.
-   `api.js`, `charts.js` and `icons.js` already have distinct
    responsibilities and should remain independent.

Do not create additional screens merely because they appeared in the
earlier proposal. In particular, do not create standalone Profile,
Invitation or Group Details routes as part of this work.

------------------------------------------------------------------------

## 3. Agreed architecture

Use a hybrid architecture:

-   **HTML:** screen-oriented; one physical HTML file per route view.
-   **JavaScript:** feature-oriented; one module per application
    feature, plus core and UI modules.
-   **CSS:** retain one consolidated stylesheet.
-   **Routing:** retain hash-based routes, adapting the router to
    asynchronous view loading.
-   **View lifecycle:** fetch a view the first time it is needed, insert
    it once, retain it in the DOM and hide it when inactive.
-   **Shared UI:** retain the persistent shell; use existing dynamic
    render functions and native `<template>` only where justified.
-   **Dependencies:** browser-native ES modules and `fetch()` only.

### 3.1 Target directory structure

``` text
frontend_service/
│
├── index.html
├── FRONTEND_MAP.md
│
├── css/
│   └── app.css
│
├── views/
│   ├── auth/
│   │   ├── login.html
│   │   └── signup.html
│   │
│   ├── dashboard/
│   │   └── dashboard.html
│   │
│   ├── groups/
│   │   └── groups.html
│   │
│   ├── expenses/
│   │   └── expenses.html
│   │
│   ├── activity/
│   │   └── activity.html
│   │
│   ├── settings/
│   │   └── settings.html
│   │
│   └── subscription/
│       └── subscription.html
│
└── js/
    ├── app.js
    ├── api.js
    ├── charts.js
    ├── icons.js
    │
    ├── core/
    │   ├── dom.js
    │   ├── state.js
    │   ├── view-loader.js
    │   └── router.js
    │
    ├── ui/
    │   ├── modal.js
    │   ├── toast.js
    │   └── forms.js
    │
    ├── features/
    │   ├── auth.js
    │   ├── dashboard.js
    │   ├── groups.js
    │   ├── expenses.js
    │   ├── settlement.js
    │   ├── invitations.js
    │   ├── notifications.js
    │   ├── activity.js
    │   ├── settings.js
    │   └── subscription.js
    │
    └── utils/
        ├── formatters.js
        └── html.js
```

Do not create empty files for future possibilities. Create a module when
its extraction phase begins.

### 3.2 Why separate HTML files?

The main purpose is targeted discoverability and editing.

A developer or coding agent changing Dashboard markup should normally
need to inspect:

``` text
views/dashboard/dashboard.html
```

A Groups screen layout change should normally start with:

``` text
views/groups/groups.html
```

The HTML files should contain the actual screen markup, not placeholder
comments or abbreviated examples.

The screen files must preserve the existing markup and all DOM contracts
used by JavaScript.

### 3.3 Why not split every small HTML element?

Do not create separate HTML files for every card, button, input, modal
or row.

Use this rule:

-   Route-level screen: separate HTML file.
-   Repeated dynamic markup: keep an existing render function or
    introduce a native `<template>` where it materially improves
    clarity.
-   Shared persistent shell: keep in `index.html`.
-   Dynamic modal content: keep feature-owned unless a genuinely static
    reusable template is appropriate.

Do not introduce `{{...}}`, `{{#if}}`, `{{#each}}`, custom include
syntax or another template language.

------------------------------------------------------------------------

## 4. HTML extraction plan

### 4.1 `index.html` becomes the application shell

`index.html` must retain:

-   Document metadata and page title.
-   The `css/app.css` stylesheet reference.
-   The application header/topbar.
-   Navigation containers: `topnav`, `sidebar`, and `tabbar`.
-   User identity controls.
-   Notification panel.
-   Main application container.
-   Toast root.
-   Modal root.
-   The module entry point.

It must no longer contain the full contents of every route screen.

Recommended shell structure:

``` html
<body>
  <noscript>
    <p class="noscript-message">
      Kharcha Tracker needs JavaScript enabled to run.
    </p>
  </noscript>

  <div id="auth-view-container"></div>

  <div id="app-shell" class="app-shell" hidden>
    <header class="topbar">
      <!-- Move the existing topbar markup here unchanged. -->
    </header>

    <aside class="sidebar" id="sidebar" aria-label="Primary"></aside>

    <main class="main" id="main">
      <div id="view-container"></div>
    </main>

    <nav class="tabbar" id="tabbar" aria-label="Primary"></nav>
  </div>

  <div class="toast" id="toast" hidden></div>
  <div id="modal-root" hidden></div>

  <script type="module" src="js/app.js"></script>
</body>
```

This is the target shell shape. During implementation, copy the existing
topbar, notification panel, user controls and their exact IDs/classes
from the supplied `index.html`; do not replace them with the abbreviated
comment above.

`#auth-view-container` is outside `#app-shell`, because Login and Signup
are displayed without the authenticated application chrome.

`#view-container` is inside the persistent `#main`, because protected
route views are displayed within the application shell.

### 4.2 Screen-to-file extraction map

Extract the existing sections without redesigning them:

  -----------------------------------------------------------------------------------------------------------------------
  Existing source section Destination                              Root element to preserve
  ----------------------- ---------------------------------------- ------------------------------------------------------
  `index.html`,           `views/auth/login.html`                  `<section id="view-login" class="view view--auth">`
  `view-login`                                                     

  `index.html`,           `views/auth/signup.html`                 `<section id="view-signup" class="view view--auth">`
  `view-signup`                                                    

  `index.html`,           `views/dashboard/dashboard.html`         `<section id="view-dashboard" class="view">`
  `view-dashboard`                                                 

  `index.html`,           `views/groups/groups.html`               `<section id="view-groups" class="view">`
  `view-groups`                                                    

  `index.html`,           `views/expenses/expenses.html`           `<section id="view-expenses" class="view">`
  `view-expenses`                                                  

  `index.html`,           `views/activity/activity.html`           `<section id="view-activity" class="view">`
  `view-activity`                                                  

  `index.html`,           `views/settings/settings.html`           `<section id="view-settings" class="view">`
  `view-settings`                                                  

  `index.html`,           `views/subscription/subscription.html`   `<section id="view-subscription" class="view">`
  `view-subscription`                                              
  -----------------------------------------------------------------------------------------------------------------------

Each file should contain its complete root `<section>` and all its
descendants. Do not include `<html>`, `<head>` or `<body>` tags in a
screen file.

### 4.3 Screen ownership details

**Login:** Keep the login form, magic-link button, hint, error element
and signup navigation link in `login.html`.

**Signup:** Keep the signup form, validation attributes and login
navigation link in `signup.html`.

**Dashboard:** Keep all current statistics, group selector, expense
summary, trend controls, chart containers and recent-activity
placeholder in `dashboard.html`.

**Groups:** Keep group creation, join-by-code, email invitation, group
list and My Invitations sections together in `groups.html`. The
invitation list remains in this screen.

**Expenses:** Keep the group selector, add-expense block, form slot and
expense list card in `expenses.html`. The actual expense form is
currently generated dynamically; do not invent a second static form.

**Activity:** Keep the group/date filters, export control, event table
slot, count and pager in `activity.html`.

**Settings:** Keep the profile form, subscription summary and logout
control in `settings.html`.

**Subscription:** Keep the current plan banner and plan cards in
`subscription.html`.

### 4.4 HTML extraction constraints

Preserve exactly:

-   Every existing `id`.
-   Every existing `name`.
-   Every existing `data-*` attribute.
-   Every existing class used by JavaScript.
-   Form input types, validation attributes and autocomplete values.
-   Button types.
-   `hidden` state on initial elements.
-   Accessibility attributes and labels.
-   Existing text and option values.
-   Element nesting where selectors or CSS depend on it.

Do not rename IDs or classes during extraction.

Do not introduce inline event handlers such as `onclick`, `onsubmit` or
`onchange`.

Do not add script tags to the screen HTML files.

Do not duplicate IDs across screen files. Since all loaded screens
remain in the DOM, IDs must be globally unique across the entire mounted
application. Existing IDs are already intended to be unique; preserve
that property.

------------------------------------------------------------------------

## 5. View loader and lifecycle

The frontend is served through HTTP(S), so browser-native `fetch()` and
ES modules are suitable.

### 5.1 Required view behaviour

Use the following lifecycle:

1.  The router determines the requested route.
2.  The route is checked for authentication and validity.
3.  If its view has not been loaded, fetch the corresponding HTML file.
4.  Validate that the returned markup contains the expected root section
    and ID.
5.  Insert that root section into the correct persistent container.
6.  Mark the view as mounted.
7.  Initialize the feature's DOM bindings exactly once.
8.  Run the feature's route-entry behaviour.
9.  Hide the previously active view and show the requested view.
10. On later visits, reuse the mounted DOM instead of fetching/inserting
    it again.

Keep mounted views in the DOM until page unload. Do not remove a view on
every route transition.

This preserves form values and view-local DOM state more closely to the
current application, where all route sections exist from initial page
load.

### 5.2 Route-to-view registry

Use an explicit route registry. Do not construct arbitrary file paths
from unvalidated route strings.

The registry must cover:

  --------------------------------------------------------------------------------------------------------
  Route             File                                     Container               Root ID
  ----------------- ---------------------------------------- ----------------------- ---------------------
  `login`           `views/auth/login.html`                  `auth-view-container`   `view-login`

  `signup`          `views/auth/signup.html`                 `auth-view-container`   `view-signup`

  `dashboard`       `views/dashboard/dashboard.html`         `view-container`        `view-dashboard`

  `groups`          `views/groups/groups.html`               `view-container`        `view-groups`

  `expenses`        `views/expenses/expenses.html`           `view-container`        `view-expenses`

  `activity`        `views/activity/activity.html`           `view-container`        `view-activity`

  `settings`        `views/settings/settings.html`           `view-container`        `view-settings`

  `subscription`    `views/subscription/subscription.html`   `view-container`        `view-subscription`
  --------------------------------------------------------------------------------------------------------

The `magic` route is a special authentication action, not a screen file.
It must retain the existing magic-link verification flow and display the
login view while verification is in progress.

### 5.3 Loader implementation requirements

`core/view-loader.js` should expose a small API such as:

``` javascript
export async function ensureViewMounted(route) {
  // Resolve route through the fixed registry.
  // Return the already-mounted root when available.
  // Otherwise fetch, parse, validate and append the root.
}

export function getMountedView(route) {
  // Return the mounted root or null.
}
```

Implementation requirements:

-   Use a fixed route registry.
-   Use same-origin relative URLs.
-   Check `response.ok`.
-   Handle network errors and non-2xx responses.
-   Validate the expected root element and root ID.
-   Reject unexpected markup rather than silently inserting it.
-   Prevent concurrent duplicate fetches for the same route by caching
    the in-flight Promise.
-   Cache the mounted element after successful insertion.
-   Do not cache a failed fetch permanently; allow a later retry.
-   Do not use `eval`, `new Function` or script injection.
-   Do not execute scripts from fetched view markup.
-   Do not use `innerHTML` to replace the persistent application shell.
-   Parse the response into a `<template>` or detached document
    fragment, select the expected root, and append that root to its
    designated container.
-   Do not insert a second copy of an already-mounted view.

### 5.4 Router concurrency

Because route loading becomes asynchronous, prevent stale navigation
from winning a race.

Example race:

1.  User selects Groups.
2.  Groups HTML fetch begins.
3.  User quickly selects Activity.
4.  Activity HTML fetch completes first.
5.  Groups fetch completes afterward.

The router must not display Groups after Activity has become the latest
requested route.

Use a monotonically increasing navigation/request sequence number or
equivalent cancellation strategy. After every awaited load, verify that
the navigation is still current before changing visibility or running
route-entry work.

Do not rely only on `AbortController`; a request may already have
completed or a view may be shared by another navigation.

### 5.5 View visibility

The router should show only the current route's view:

-   For `login` and `signup`, show the corresponding auth view, hide the
    other auth view and hide `#app-shell`.
-   For protected routes, hide all auth views, show `#app-shell`, and
    show only the requested protected view.
-   Keep inactive views mounted but hidden.
-   Preserve the existing `hidden` attribute behaviour and the CSS rule
    `[hidden] { display: none !important; }`.

Do not change route names or hash URL formats.

### 5.6 Feature lifecycle

Each feature module should expose a consistent lifecycle where
appropriate:

``` javascript
export function mount() {
  // Bind listeners once after the feature HTML is mounted.
}

export async function onEnter() {
  // Refresh/load data required when the route becomes active.
}
```

Not every module needs both functions. Keep the API small.

-   `mount()` must be idempotent or called exactly once by the router.
-   `onEnter()` may run on every route entry, matching the current
    route-handler behaviour.
-   Do not bind the same listener from `onEnter()`.
-   Do not recreate the view's markup on every route entry unless the
    existing feature already renders a specific dynamic region.
-   Feature modules must not assume their HTML exists before the loader
    mounts it.

------------------------------------------------------------------------

## 6. JavaScript module responsibilities and exact source mapping

The following mapping uses function names and approximate source line
ranges from the supplied `app.js`. Line ranges are navigation aids; use
function names as the authoritative extraction boundaries because line
numbers will change during refactoring.

### 6.1 Root `js/app.js`

Final responsibility: application composition and startup only.

Move out of `app.js`:

-   Feature implementations.
-   UI utility implementations.
-   Route-specific rendering.
-   API/business logic.

Keep in `app.js`:

-   Import statements.
-   Feature/route registration.
-   Global shell initialization.
-   Global shell event bindings.
-   Authentication/session bootstrap coordination.
-   Startup sequence.
-   Global browser event subscriptions, unless a dedicated core module
    owns them.

The final `app.js` should be short and should not contain the feature
function bodies.

### 6.2 `core/dom.js`

Move:

-   `$()` --- currently around line 31.
-   `$all()` --- currently around line 32.

Provide the existing selector behaviour with optional root arguments.

All modules should import these helpers rather than defining their own
copies.

### 6.3 `core/state.js`

Centralize state currently declared in `app.js`:

  Existing state        Current approximate line Target property
  ------------------- -------------------------- ---------------------------
  `currentUser`                               26 `state.currentUser`
  `dashGroupId`                              483 `state.dashboard.groupId`
  `dashRange`                                484 `state.dashboard.range`
  `groupsCache`                              741 `state.groupsCache`
  `membersCache`                             742 `state.membersCache`
  `expensesCache`                            743 `state.expensesCache`
  `settleCache`                              744 `state.settleCache`
  `notesCache`                               746 `state.notesCache`
  `expensesGroupId`                         1261 `state.expenses.groupId`
  `activityGroups`                          2361 `state.activity.groups`
  `activityGroupId`                         2362 `state.activity.groupId`
  `activityPage`                            2363 `state.activity.page`
  `activityTotal`                           2364 `state.activity.total`

Recommended initial shape:

``` javascript
export const state = {
  currentUser: null,

  dashboard: {
    groupId: "",
    range: "daily"
  },

  groupsCache: [],
  membersCache: {},
  expensesCache: {},
  settleCache: {},
  notesCache: {},

  expenses: {
    groupId: ""
  },

  activity: {
    groups: [],
    groupId: "",
    page: 0,
    total: 0
  }
};
```

Keep feature-specific transient state private to its feature unless
another feature genuinely needs it.

Do not turn this into a general-purpose state-management framework.

### 6.4 `core/router.js`

Move/refactor:

-   `NAV_ITEMS` --- current lines 4--10.
-   `ROUTE_TITLES` --- current lines 12--22.
-   `PROTECTED_ROUTES` --- current line 24.
-   `currentRoute()` --- current lines 34--37.
-   `navigate()` --- current lines 39--43.
-   `navItemHtml()` --- current lines 117--123.
-   `renderNav()` --- current lines 125--132.
-   `setActiveNav()` --- current lines 134--138.
-   `router()` --- current lines 141--160.

Router responsibilities:

-   Parse the current hash route.
-   Preserve existing route names and title strings.
-   Apply the same authentication guards.
-   Load the route's HTML.
-   Mount the feature once.
-   Invoke route-entry behaviour.
-   Toggle route visibility.
-   Update active navigation.
-   Update `document.title`.
-   Handle asynchronous navigation races.
-   Register the `hashchange` listener once.

Do not place feature API calls or feature rendering inside the router.

Use a registration interface or a route configuration passed from
`app.js` so `core/router.js` does not import every feature module and
create circular dependencies.

### 6.5 `ui/toast.js`

Move:

-   `toastTimer` --- current line 52.
-   `showToast()` --- current lines 53--60.

Keep the existing `#toast` root and current message/kind behaviour.

### 6.6 `ui/forms.js`

Move:

-   `setError()` --- current lines 63--67.
-   `setBusy()` --- current lines 68--78.

Retain current function signatures initially so feature extraction does
not also change form behaviour.

### 6.7 `ui/modal.js`

Move:

-   `openModal()` --- current lines 81--103.
-   `closeModal()` --- current lines 105--109.
-   `modalEscHandler()` --- current lines 112--114.

Preserve the current `#modal-root`, overlay click-to-close, close button
and Escape handling.

The existing modal implementation receives HTML content generated by
feature functions. Do not introduce a modal template language.

Review the HTML-safety of `contentHtml` separately; do not silently
change modal content or behaviour during extraction.

### 6.8 `utils/html.js`

Move the shared HTML escaping helper:

-   `esc()` --- current lines 45--49.

Use one canonical escaping helper for dynamically generated HTML.

Do not confuse HTML text escaping with URL validation, JavaScript-string
escaping or CSS escaping. Continue to use DOM `textContent` where
practical.

### 6.9 `utils/formatters.js`

Move shared formatting helpers:

-   `formatAmount()` --- current lines 763--766.
-   `formatDate()` --- current lines 386--390, if it remains a shared
    helper.

Keep domain-specific calculations with their feature unless they are
demonstrably shared.

### 6.10 `features/auth.js`

Move:

-   `magicInFlight` --- current line 195.
-   `establishSession()` --- current lines 197--201.
-   `handleMagicLink()` --- current lines 203--229.
-   `requestMagicLink()` --- current lines 231--241.
-   `bindAuthForms()` --- current lines 244--372.

Also move authentication-specific session functions currently in the
shell section:

-   `logout()` --- current lines 176--180.
-   `onExpired()` --- current lines 183--187.

`applyUserChrome()` should remain in a shell/session module or be placed
in `app.js` only if it remains very small and is used by multiple
features. Prefer a dedicated `core/shell.js` if the shell
responsibilities grow; do not add it prematurely.

Preserve:

-   Existing auth API endpoints.
-   `Auth` calls from `api.js`.
-   Magic-link token parsing from the hash query.
-   Existing toast/error messages.
-   Existing navigation destinations.
-   Existing session persistence behaviour.

`api.js` remains the owner of token storage and HTTP refresh mechanics.
`auth.js` coordinates application login/session flows; it must not
duplicate token-refresh logic.

### 6.11 `features/dashboard.js`

Move the dashboard section:

-   `routeHandlers.dashboard` --- current lines 327--337.
-   `dashGroupId`, `dashRange` --- current lines 483--484, into
    `state.dashboard`.
-   `MONTH_SHORTS` --- current line 485.
-   `dayKey()` --- current lines 488--490.
-   `weekStart()` --- current lines 492--496.
-   `isoWeekNumber()` --- current lines 498--503.
-   `rangeBuckets()` --- current lines 505--536.
-   `buildRangeSeries()` --- current lines 538--573.
-   `renderExpenseSummary()` --- current lines 575--606.
-   `renderExpenseCharts()` --- current lines 608--658.
-   `loadExpenseSummary()` --- current lines 660--718.
-   `bindDashboard()` --- current lines 720--739.

Dependencies:

-   `api.js` for API calls.
-   `charts.js` for chart SVG generation and stable category colours.
-   `core/state.js` for group and expense caches and dashboard
    selection.
-   `utils/html.js` and `utils/formatters.js`.
-   `ui` utilities as required.

Do not move chart drawing algorithms out of `charts.js`.

### 6.12 `features/groups.js`

Owns the Groups screen and group-level operations.

Move:

-   `routeHandlers.groups` --- current lines 339--343.
-   `EXPENSE_CATEGORIES` --- current lines 748--753, if still shared
    with expense forms; otherwise export from a clearly designated
    shared domain module.
-   `refreshGroupCount()` --- current lines 755--761.
-   `memberName()` --- current lines 768--772, if shared; otherwise
    place it in a group-domain helper module.
-   `memberChipsHtml()` --- current lines 774--785.
-   `groupCardHtml()` --- current lines 1115--1182.
-   `loadGroups()` --- current lines 1605--1643.
-   The group-specific portion of `bindGroupActions()` --- current lines
    1645--approximately 1930 and any related group actions later in that
    function.

Also move note functionality currently located around lines 1204--1259:

-   `noteRowHtml()`
-   `notesListHtml()`
-   `resetNoteForm()`
-   `loadGroupNotes()`

Keep group list rendering and group CRUD in this module.

**Important:** `bindGroupActions()` currently contains multiple
concerns. Do not move the entire function wholesale into `groups.js`.
Split it by event responsibility:

-   Group create form toggle/cancel.
-   Group creation submit.
-   Join-group submit.
-   Email invitation submit.
-   Group card copy/regen/delete/leave actions.
-   Group notes toggle/edit/delete/create actions.

Keep the group list event delegation attached once to the mounted Groups
view or its stable group-list container.

### 6.13 `features/expenses.js`

Owns expense rendering and expense operations, including expenses shown
inside group cards and the dedicated Expenses screen.

Move:

-   `expenseRowHtml()` --- current lines 787--810.
-   `expensesListHtml()` --- current lines 812--818.
-   `expenseFormHtml()` --- current lines 820--863.
-   `toLocalInputValue()` --- current lines 865--872.
-   `expenseEditFormHtml()` --- current lines 874--925.
-   `openExpenseEditModal()` --- current lines 927--1003.
-   `equalSplitPercentages()` --- current lines 1005--1011.
-   `splitPopupHtml()` --- current lines 1013--1040.
-   `openSplitPopup()` --- current lines 1042--1113.
-   `loadGroupExpenses()` --- current lines 1184--1202.
-   `expensesGroupId` and `LAST_EXPENSE_GROUP_KEY` --- current lines
    1261--1264, with selected group in `state.expenses.groupId`.
-   `rememberLastExpenseGroupId()` --- current lines 1266--1272.
-   `lastUsedExpenseGroupId()` --- current lines 1274--1280.
-   `renderExpensesScreen()` --- current lines 1282--1313.
-   `applyExpensesGroup()` --- current lines 1315--1340.
-   `selectExpensesGroup()` --- current lines 1342--1351.
-   `routeHandlers.expenses` --- current lines 345--349.
-   `bindExpensesScreen()` --- current lines 2133--2205.
-   Expense-specific delegated click actions currently embedded in
    `bindGroupActions()` and attached to `#main` (approximately lines
    1930 onward).

The delegated expense action handler currently supports both the Groups
screen and Expenses screen. Preserve this cross-screen behaviour.

Recommended extraction:

-   Export expense markup helpers required by `groups.js`.
-   Export `loadGroupExpenses()` if other features need it.
-   Bind the cross-screen expense action handler once on the persistent
    `#main` or `#view-container`, not once per screen entry.
-   Bind the dedicated Expenses screen form/select handlers once after
    `view-expenses` is mounted.

Avoid circular imports. `expenses.js` should depend on `core/state.js`,
shared utilities and UI modules; it should not import `groups.js`.

### 6.14 `features/settlement.js`

Move settlement-related code currently embedded in the Groups section:

-   `PAYOUT_ESCALATION_DAYS` --- current line 1353.
-   `payoutStatusPillHtml()` --- current lines 1355--1360.
-   `isInvolvedInExpense()` --- current lines 1362--1367.
-   `payoutActionsHtml()` --- current lines 1369--1394.
-   `linkExpensesFormHtml()` --- current lines 1396--1416.
-   `payoutRowHtml()` --- current lines 1418--1437.
-   `payoutSectionHtml()` --- current lines 1439--1447.
-   `adhocFormHtml()` --- current lines 1449--1491.
-   `corpusFromPreview()` --- current lines 1493--1509.
-   `corpusSectionHtml()` --- current lines 1511--1544.
-   `settlePanelHtml()` --- current lines 1546--1590.
-   `loadSettlePanel()` --- current lines 1592--1603.
-   Settlement-related branches and submit/change handlers currently
    embedded in `bindGroupActions()`.

The current settlement panel is generated dynamically inside group
cards. It is not a separate route or static HTML screen.

`groups.js` may import `settlePanelHtml()` or another public settlement
renderer. `settlement.js` must not import `groups.js`; both should use
shared state and shared utilities.

Split settlement event handling from the current large
`bindGroupActions()` function:

-   Toggle settlement panel.
-   Toggle/cancel ad-hoc payment form.
-   Submit ad-hoc payment.
-   Payout status actions.
-   Link expenses to payout.
-   Filter ad-hoc expense options when payor/payee changes.

Keep settlement API endpoints and payloads unchanged.

### 6.15 `features/invitations.js`

Move:

-   `invitationCardHtml()` --- current lines 2207--2224.
-   `loadInvitations()` --- current lines 2226--2239.
-   `refreshInviteCount()` --- current lines 2241--2246.
-   `bindInvitationActions()` --- current lines 2248--2277.

Invitations remain displayed in the Groups screen. This module owns
invitation operations, not a new Invitation route.

Mount its event handlers only after the Groups view has been inserted,
because the relevant elements are in `groups.html`.

### 6.16 `features/notifications.js`

Move:

-   `NOTIFICATION_LABELS` --- current lines 2279--2291.
-   `notificationText()` --- current lines 2293--2297.
-   `renderNotifications()` --- current lines 2299--2316.
-   `setBellBadge()` --- current lines 2318--2328.
-   `loadNotifications()` --- current lines 2330--2340.
-   `markNotificationsRead()` --- current lines 2342--2349.
-   `toggleNotifPanel()` --- current lines 2351--2358.

Notifications belong to the persistent shell. Bind the bell, mark-read
and outside-click handlers once during shell initialization, not on each
route entry.

### 6.17 `features/activity.js`

Move:

-   `routeHandlers.activity` --- current lines 351--354.
-   `ACTIVITY_PAGE_SIZE` --- current line 2360.
-   Activity state variables --- current lines 2361--2364, into
    `state.activity`.
-   `activityRangeParams()` --- current lines 2366--2376.
-   `clearActivityList()` --- current lines 2378--2387.
-   `loadActivityScreen()` --- current lines 2389--2432.
-   `fetchActivityPage()` --- current lines 2434--2469.
-   `formatActivityTime()` --- current lines 2471--2477.
-   `activityMemberName()` --- current lines 2479--2486.
-   `activityActorName()` --- current lines 2488--2492.
-   `eventPillClass()` --- current lines 2494--2502.
-   `prettifyActivity()` --- current lines 2504--2525.
-   `activityRowHtml()` --- current lines 2527--2538.
-   `renderActivityTable()` --- current lines 2540--2560.
-   `renderActivityPager()` --- current lines 2562--2575.
-   `ACTIVITY_EXPORT_CHUNK` and `ACTIVITY_EXPORT_CAP` --- current lines
    2577--2578.
-   `csvCell()` --- current lines 2580--2584.
-   `activityGroupSlug()` --- current lines 2586--2591.
-   `exportActivityCsv()` --- current lines 2593--2658.
-   `bindActivityScreen()` --- current lines 2660--2694.

Bind Activity listeners once after the Activity HTML has been mounted.

### 6.18 `features/subscription.js`

Move:

-   `routeHandlers.subscription` --- current lines 368--371.
-   `PLAN_LABELS` --- current lines 374--380.
-   `planLabel()` --- current lines 382--384.
-   `planSummary()` --- current lines 393--403.
-   `shortPlan()` --- current lines 405--408.
-   `renderSubscriptionBanner()` --- current lines 410--426.
-   `loadSubscription()` --- current lines 428--439.
-   `updatePlanStat()` --- current lines 441--444.
-   `refreshPlanStat()` --- current lines 446--450.
-   `refreshSettingsPlan()` --- current lines 452--460.
-   `bindSubscriptionActions()` --- current lines 462--480.

`formatDate()` may move to `utils/formatters.js` if shared.

Plan purchase buttons exist only in the Subscription view, so bind them
after that view is mounted.

### 6.19 `features/settings.js`

Move:

-   `routeHandlers.settings` --- current lines 356--366.
-   Settings view initialization and user field population.
-   Settings logout button binding, if it is not kept in global shell
    initialization.

The supplied `app.js` populates `profile-name`, `profile-email` and
`profile-avatar` on entry to Settings. However, a submit handler for
`form-profile` is not present in the supplied `app.js` text. Do not
invent or implement a profile-update API as part of this structural
refactor. Preserve the current behaviour and record the missing submit
behaviour as a separate issue for product confirmation.

### 6.20 `api.js`, `charts.js` and `icons.js`

Keep these files at their current paths and preserve their public APIs:

-   `api.js`: `api`, `Auth`, `ApiError` and existing refresh behaviour.
-   `charts.js`: `Charts` and existing SVG/chart functions.
-   `icons.js`: `Icons` and existing SVG icon definitions.

Do not merge these files into feature modules.

The new `app.js` and feature modules should import these APIs
explicitly.

------------------------------------------------------------------------

## 7. Dependency rules

Use explicit ES module imports and exports.

### 7.1 Allowed dependency direction

``` text
app.js
  |
  +--> core/router.js
  +--> core/view-loader.js
  +--> core/state.js
  +--> ui/*
  +--> features/*
          |
          +--> core/*
          +--> ui/*
          +--> utils/*
          +--> api.js
          +--> charts.js (dashboard only)
          +--> icons.js (where needed)
```

Core modules must not import feature modules.

Utility modules must not import feature modules.

Feature modules must not import `app.js`.

Avoid circular dependencies between features.

### 7.2 Shared group data

Groups, Expenses, Dashboard and Settlement share group-related caches
and member information.

Use `core/state.js` for shared caches. Do not create separate copies of
`groupsCache`, `membersCache`, `expensesCache`, `settleCache` or
`notesCache` in different feature modules.

`groups.js` can export group-specific operations needed by other
modules, but avoid an import cycle. Prefer shared state and small helper
modules where necessary.

### 7.3 No implicit globals

After migration:

-   Do not depend on `window.api`, `window.Icons` or other globals.
-   Do not attach feature functions to `window`.
-   Do not rely on script-tag ordering.
-   Use explicit imports.
-   Do not expose private implementation functions merely to make
    imports convenient.

------------------------------------------------------------------------

## 8. App bootstrap and shell responsibilities

`app.js` should coordinate initialization in this order:

1.  Wait for `DOMContentLoaded` if the module is not already executing
    after DOM readiness.
2.  Initialize persistent shell icon content that exists in
    `index.html`.
3.  Render navigation.
4.  Bind persistent shell event handlers once.
5.  Register application-wide events (`hashchange`, `auth:expired`,
    `subscription:required`).
6.  Restore/validate the current session using the existing API flow.
7.  Start the router for the current hash.
8.  Let the router load the requested view.
9.  Let the relevant feature mount its listeners once.
10. Let the feature's route-entry function load/refresh its data.

Important: several icons currently initialized in `boot()` belong to
screen markup that will no longer be present at initial page load:

-   `btn-new-group-icon`, `join-icon`, `invite-icon` belong to Groups.
-   `magic-icon` belongs to Login.
-   `expenses-add-icon` belongs to Expenses.
-   `activity-export-icon` belongs to Activity.
-   `btn-bell` and `btn-logout` belong to the persistent shell.

Initialize screen-owned icons in that feature's `mount()` function after
its HTML has been inserted. Initialize shell-owned icons during shell
startup.

Do not call `querySelector()` for an element before its screen has been
mounted.

------------------------------------------------------------------------

## 9. Router and feature registration contract

The router should remain generic.

A suitable conceptual contract is:

``` javascript
registerRoute("dashboard", {
  mount: dashboard.mount,
  onEnter: dashboard.onEnter
});
```

The exact implementation may differ, but preserve these rules:

-   The route registry contains the file path, expected root ID,
    destination container and feature lifecycle.
-   The router does not contain business logic.
-   The loader does not know about API calls or feature-specific data.
-   A feature's `mount()` runs once after its HTML is mounted.
-   A feature's `onEnter()` runs whenever the route becomes active,
    unless the existing behaviour is intentionally one-time.
-   Shell handlers run once.
-   `hashchange` is registered once.
-   A failed view load must show a recoverable error state and must not
    leave the app silently blank.
-   A route change while another route is loading must not display the
    stale route.

Do not change the existing `navigate(route)` hash format (`#/route`).

Preserve the special `magic` route handling.

------------------------------------------------------------------------

## 10. CSS policy

Keep:

``` text
css/app.css
```

Do not split the stylesheet during this refactor.

The existing CSS already contains:

-   CSS custom properties.
-   Reset and typography rules.
-   Shared button/form/card styles.
-   Application shell and navigation styles.
-   Screen-specific sections.
-   Modal and toast styles.
-   Chart styles.
-   Responsive rules.

Required CSS work:

1.  Preserve all existing selectors and declarations initially.
2.  Preserve the `[hidden] { display: none !important; }` rule.
3.  Preserve the current mobile-first responsive behaviour.
4.  Preserve existing class names used by HTML and JavaScript.
5.  Do not introduce a CSS framework.
6.  Do not rename classes while extracting HTML.
7.  Add or adjust CSS only when the new shell/container structure
    requires it.
8.  If a layout change is needed, keep it minimal and verify desktop,
    tablet and mobile widths.

A BEM-like naming convention may be used for new classes, but do not
undertake a wholesale class rename in this task.

------------------------------------------------------------------------

## 11. Security and rendering requirements

The current frontend generates HTML strings and inserts some content
through `innerHTML`. The existing `esc()` helper is a useful baseline.

During extraction:

-   Move to one shared `escapeHtml()` helper or preserve the existing
    `esc()` export name consistently.
-   Escape API/user-provided values when interpolating them into HTML
    text or attributes.
-   Prefer `textContent` for plain text updates.
-   Do not assume escaping text makes an interpolated URL safe.
-   Do not interpolate untrusted strings into inline JavaScript.
-   Do not introduce inline event handlers.
-   Do not execute scripts contained in fetched view markup.
-   Do not use `eval` or `new Function`.
-   Do not weaken existing authentication guards.

This refactor should not attempt a wholesale rewrite of all render
functions. Preserve behaviour first; security hardening can be a
separately reviewed incremental change.

------------------------------------------------------------------------

## 12. Incremental implementation phases

Do not attempt to move all 2,748 lines in one pass. Complete and verify
each phase before continuing.

### Phase 0 --- Baseline and inventory

Tasks:

1.  Keep a clean backup/branch of the current frontend.
2.  Record the current routes and their expected behaviour.
3.  Record the existing IDs and `data-*` attributes per screen.
4.  Record the existing API endpoint usage.
5.  Identify global state and event listener locations.
6.  Verify the application starts and all routes work before
    modifications.
7.  Capture representative desktop and mobile screenshots if practical.
8.  Create `FRONTEND_MAP.md` at the frontend project root using the
    project-specific starting template in Section 17.2. Populate it with
    verified route-to-view mappings, existing shell IDs, shared state,
    API client, chart and icon modules, CSS section references and
    current source-file locations. Mark anything that cannot yet be
    verified as pending; do not guess.

Deliverables: baseline checklist, route/screen inventory and the initial
populated `FRONTEND_MAP.md`.

Exit criteria: the untouched application is known to work and the initial
frontend map exists before structural changes begin.

### Phase 1 --- Establish native ES modules without feature extraction

Tasks:

1.  Change the script entry to `type="module"`.
2.  Import `api.js`, `charts.js` and `icons.js` using ES module exports.
3.  Convert the current global declarations to exports/imports only as
    needed.
4.  Keep all functionality in the existing `app.js` temporarily.
5.  Verify startup, routing, auth and API calls.

If `api.js`, `charts.js` and `icons.js` currently use top-level `const`
declarations, add explicit exports for their existing public objects. Do
not change their behaviour.

Exit criteria: the app works using native ES modules while still using
the original single HTML file.

### Phase 2 --- Extract screen HTML and implement the view loader

Perform this phase only after Phase 1 is stable.

Tasks:

1.  Create the `views/` directory and route subdirectories.
2.  Extract each of the eight existing view sections into its mapped
    file.
3.  Replace the route sections in `index.html` with
    `#auth-view-container` and `#view-container`.
4.  Preserve the persistent shell and all its IDs.
5.  Implement the fixed route registry.
6.  Implement the view loader with in-flight request deduplication and
    mounted-view caching.
7.  Implement asynchronous router sequencing.
8.  Ensure each screen is inserted only once.
9.  Keep loaded screens mounted and hide inactive screens.
10. Add a visible recoverable error message if a view cannot load.
11. Verify every route and the special magic-link route.

Exit criteria:

-   All eight views load from their own files.
-   All routes retain their current hash names.
-   A view is fetched only on first use.
-   Revisiting a view does not fetch or insert a duplicate.
-   Form values and local DOM state survive route changes while the page
    remains open.
-   Fast route changes do not display stale content.

### Phase 3 --- Extract core and UI utilities

Move:

-   DOM helpers.
-   Shared state.
-   Toast.
-   Forms.
-   Modal.
-   HTML escaping.
-   Shared formatting helpers.
-   Router and view loader.

Update imports and remove duplicate definitions from `app.js`.

Exit criteria: utilities are imported explicitly and there are no
duplicated global implementations.

### Phase 4 --- Extract independent features

Recommended order:

1.  Authentication.
2.  Notifications.
3.  Subscription.
4.  Settings.
5.  Activity.
6.  Invitations.
7.  Dashboard.

For each feature:

1.  Move its functions.
2.  Add explicit imports/exports.
3.  Add `mount()` and `onEnter()` where appropriate.
4.  Ensure event listeners are bound once.
5.  Remove the old implementation from `app.js`.
6.  Test the feature before moving to the next.

Exit criteria: each extracted feature works independently through the
router and has no dependency on global script ordering.

### Phase 5 --- Extract interconnected group, expense and settlement functionality

Recommended order:

1.  Shared state and shared formatting dependencies.
2.  Expense rendering helpers and expense screen functionality.
3.  Settlement rendering and event handlers.
4.  Group screen rendering and group operations.
5.  Split the current `bindGroupActions()` into group, expense and
    settlement event responsibilities.
6.  Resolve imports without circular dependencies.
7.  Remove the old monolithic implementations.

This phase requires extra care because the Groups screen currently
renders expense rows/forms and settlement panels inside group cards.

Exit criteria:

-   Group cards render identically.
-   Expense operations work from both Groups and Expenses screens.
-   Settlement and payout actions work.
-   Notes work.
-   Invitation operations work.
-   Shared caches remain consistent.

### Phase 6 --- Final cleanup and regression testing

Tasks:

1.  Remove obsolete code and imports.
2.  Check every module for unused exports.
3.  Check for duplicate event listeners.
4.  Check for circular imports.
5.  Check all dynamic HTML generation and escaping.
6.  Check all route guards.
7.  Check direct hash navigation and browser refresh on each route.
8.  Check responsive layouts.
9.  Check network failures for HTML and API requests.
10. Update `FRONTEND_MAP.md`.

Exit criteria: all acceptance tests in Section 14 pass.

------------------------------------------------------------------------

## 13. Function extraction checklist

Use this as the implementation tracking list.

  -----------------------------------------------------------------------
  Source responsibility               Destination
  ----------------------------------- -----------------------------------
  `$`, `$all`                         `core/dom.js`

  route constants and hash router     `core/router.js`

  current user and shared caches      `core/state.js`

  HTML view fetch/mount/cache         `core/view-loader.js`

  `showToast`                         `ui/toast.js`

  `setError`, `setBusy`               `ui/forms.js`

  `openModal`, `closeModal`, Escape   `ui/modal.js`
  handler                             

  `esc`                               `utils/html.js`

  shared date/amount formatting       `utils/formatters.js`

  login/signup/magic link/session     `features/auth.js`
  flows                               

  dashboard calculations, rendering   `features/dashboard.js`
  and bindings                        

  group cards, group CRUD, notes      `features/groups.js`

  expense rendering, forms, splits    `features/expenses.js`
  and CRUD                            

  settlement/payout/corpus rendering  `features/settlement.js`
  and actions                         

  invitation list and actions         `features/invitations.js`

  notification rendering and actions  `features/notifications.js`

  activity loading, filters, paging   `features/activity.js`
  and CSV                             

  settings route initialization       `features/settings.js`

  subscription status and plan        `features/subscription.js`
  actions                             

  startup and shell composition       `app.js`

  HTTP client and token refresh       existing `api.js`

  SVG chart generation                existing `charts.js`

  SVG icon definitions                existing `icons.js`

  all current styling                 existing `css/app.css`
  -----------------------------------------------------------------------

------------------------------------------------------------------------

## 14. Acceptance and regression checklist

All existing functionality must be tested after the refactor.

### 14.1 HTML and routing

-   [ ] Login HTML loads from its separate file.
-   [ ] Signup HTML loads from its separate file.
-   [ ] Dashboard HTML loads from its separate file.
-   [ ] Groups HTML loads from its separate file.
-   [ ] Expenses HTML loads from its separate file.
-   [ ] Activity HTML loads from its separate file.
-   [ ] Settings HTML loads from its separate file.
-   [ ] Subscription HTML loads from its separate file.
-   [ ] Direct navigation to each hash route works.
-   [ ] Browser refresh on each route works.
-   [ ] Unknown route fallback matches current behaviour.
-   [ ] Protected routes redirect to Login when unauthenticated.
-   [ ] Authenticated users are redirected away from Login/Signup as
    before.
-   [ ] Magic-link verification works.
-   [ ] Navigation does not create duplicate view elements.
-   [ ] A previously mounted view is reused.
-   [ ] Rapid route changes do not display stale views.
-   [ ] Failed HTML fetches show a recoverable error.

### 14.2 Authentication and session

-   [ ] Login works.
-   [ ] Signup works.
-   [ ] Magic-link request works.
-   [ ] Magic-link verification works.
-   [ ] Logout from header works.
-   [ ] Logout from Settings works.
-   [ ] Expired session redirects to Login.
-   [ ] Token refresh behaviour in `api.js` is unchanged.
-   [ ] User name/avatar/header state is updated correctly.

### 14.3 Dashboard

-   [ ] Welcome text appears.
-   [ ] Group count loads.
-   [ ] Pending invitation count loads.
-   [ ] Plan status loads.
-   [ ] Group selector populates.
-   [ ] Expense summary loads.
-   [ ] Daily/weekly/monthly range controls work.
-   [ ] Bar and pie charts render.
-   [ ] Empty and error states work.

### 14.4 Groups, expenses and settlement

-   [ ] Groups load.
-   [ ] Create-group form opens, closes and submits.
-   [ ] Join-by-code works.
-   [ ] Invite-by-email works.
-   [ ] Invite code copy works.
-   [ ] Invite regeneration works.
-   [ ] Group deletion works.
-   [ ] Leaving a group works.
-   [ ] Group notes load.
-   [ ] Add/edit/delete notes work.
-   [ ] Group expense rows render.
-   [ ] Expense form opens and closes.
-   [ ] Equal split works.
-   [ ] Custom percentage split works.
-   [ ] Expense creation works from the existing supported locations.
-   [ ] Expense edit modal works.
-   [ ] Expense deletion works.
-   [ ] Dedicated Expenses group selector works.
-   [ ] Last-used group selection is preserved.
-   [ ] Settlement preview loads.
-   [ ] Settlement actions work.
-   [ ] Ad-hoc payment works.
-   [ ] Payout actions work.
-   [ ] Linking expenses to payouts works.
-   [ ] Corpus values and deposits render correctly.

### 14.5 Invitations, notifications, activity and subscription

-   [ ] Invitation list loads.
-   [ ] Invitation actions work.
-   [ ] Notification panel opens and closes.
-   [ ] Mark-all-read works.
-   [ ] Notification badge updates.
-   [ ] Activity group/date filters work.
-   [ ] Activity pagination works.
-   [ ] Activity CSV export works.
-   [ ] Subscription banner loads.
-   [ ] Plan selection works as currently implemented.
-   [ ] Subscription-required event redirects correctly.

### 14.6 UI and responsive behaviour

-   [ ] Desktop layout is unchanged.
-   [ ] Tablet layout is unchanged.
-   [ ] Mobile layout is unchanged.
-   [ ] Top navigation works.
-   [ ] Sidebar navigation works.
-   [ ] Bottom tab navigation works.
-   [ ] Modal overlay and Escape behaviour work.
-   [ ] Toast messages appear and disappear.
-   [ ] Hidden elements remain hidden.
-   [ ] No console errors.
-   [ ] No unexpected network 404s.

------------------------------------------------------------------------

## 15. Special implementation cautions

### 15.1 Existing event binding assumes all views exist

The current `boot()` binds handlers to elements across all views because
all markup is initially in `index.html`.

After HTML extraction, those elements do not exist until their view is
loaded.

Therefore:

-   Do not run all feature bind functions at initial application
    startup.
-   Mount a feature only after its view has been inserted.
-   Bind that feature's listeners once.
-   Keep persistent shell listeners in shell initialization.
-   Ensure cross-screen delegated handlers are attached to a persistent
    ancestor.

### 15.2 Cross-screen expense actions

The current `bindGroupActions()` attaches a delegated click handler to
`#main` for expense actions used from both Groups and Expenses.

Preserve this behaviour. Do not accidentally bind two handlers, one in
Groups and one in Expenses, that both process the same click.

A single delegated handler on a persistent ancestor is acceptable,
provided it is registered once and its selector remains limited to the
intended expense actions.

### 15.3 Dynamic group cards

`groupCardHtml()` generates markup containing group actions, expense
content, notes and settlement containers.

The group card should remain generated dynamically unless there is a
clear reason to introduce a native template. Do not move a static copy
of generated group cards into `groups.html`.

### 15.4 Dynamic modals

`openExpenseEditModal()` and other feature functions generate modal body
content.

Keep the existing modal-root mechanism. The modal utility should remain
generic; feature modules should own their form markup and submit
behaviour.

### 15.5 Screen-owned icons

Move icon initialization to the appropriate feature's `mount()`:

-   Groups: new group, join, invite icons.
-   Login: magic-link icon.
-   Expenses: add-expense icon.
-   Activity: export icon.

Keep persistent shell icons in shell initialization:

-   Notification bell.
-   Logout.

### 15.6 Profile form behaviour gap

The supplied source initializes the Settings profile fields but does not
contain a submit handler for `form-profile`.

Do not invent a profile update endpoint or silently add profile-saving
behaviour during refactoring. Record this separately and ask for
product/API confirmation if profile editing is expected to work.

### 15.7 Avoid broad cleanup during extraction

Do not combine these tasks with:

-   Visual redesign.
-   API endpoint changes.
-   API payload changes.
-   Renaming route names.
-   Renaming DOM IDs.
-   Rewriting chart algorithms.
-   Rewriting authentication/token refresh.
-   Changing localStorage keys.
-   Changing CSS class conventions wholesale.
-   Introducing new features.

Keep each migration diff focused and reviewable.

------------------------------------------------------------------------

## 16. Coding-agent operating instructions

These instructions are intended for an agent with limited context
capacity.

1.  Read this specification completely before editing.
2.  Read `FRONTEND_MAP.md` before editing. It is a required project file;
    if it is missing, create it using Section 17 before proceeding with
    structural changes.
3.  Work on one phase at a time.
4.  Before editing a feature, inspect only:
    -   Its source functions in the current `app.js`.
    -   Its destination HTML file.
    -   Its direct dependencies.
    -   The relevant CSS section if visual changes are involved.
5.  Do not read the entire frontend repeatedly when the function map is
    sufficient.
6.  Do not invent missing functions, endpoints, data structures or
    screens.
7.  Preserve existing identifiers and API contracts.
8.  After moving a function, remove its original copy to avoid duplicate
    definitions.
9.  Update imports/exports in the same change as the extraction.
10. Do not leave a feature half-migrated with some handlers bound in
    both old and new locations.
11. Run syntax checks and the relevant browser regression tests after
    each phase.
12. Report files changed, functions moved, tests performed and any
    unresolved issue.
13. Stop and report if the current source differs materially from the
    source described in this specification. Do not guess how to
    reconcile a changed implementation.
14. Do not proceed to the next phase if the current phase fails its exit
    criteria.

### Required completion report per phase

``` text
Phase:
Files created:
Files modified:
Functions moved:
Functions removed from old location:
Imports/exports changed:
Behaviour verified:
Tests run:
Known issues:
Phase exit criteria: PASS / FAIL
```

------------------------------------------------------------------------

## 17. `FRONTEND_MAP.md` requirement

`FRONTEND_MAP.md` is a required project artifact. It is not optional
end-of-project documentation.

Create and maintain it at the frontend project root:

```text
frontend_service/docs/FRONTEND_MAP.md
```

The file is part of the target directory structure shown in Section 3.1.
Create its initial version during Phase 0, before any structural
refactoring begins. Keep it synchronized after every phase that changes
files, module ownership, routes, shared state or dependencies. The map
must describe the **actual current implementation during migration**,
not merely the intended final architecture.

This is a navigation index for developers and coding agents. It is not a
replacement for this implementation specification.

### 17.1 Required contents of `FRONTEND_MAP.md`

The map must contain all of the following sections:

1. Project purpose and a short explanation of how to use the map.
2. Current migration status, including the last completed phase.
3. Route-to-HTML mapping, including route name, HTML file, container,
   root element ID and feature module ownership.
4. Persistent application shell elements and their exact existing IDs.
5. JavaScript module ownership and responsibilities.
6. Shared state ownership, including the current user and shared caches.
7. API client location and responsibility.
8. Chart and icon module locations and responsibilities.
9. CSS section references, using actual section headings or selector
   groups found in `css/app.css`.
10. Important cross-feature dependencies and shared event delegation.
11. A short targeted-change navigation instruction.
12. A concise update log recording each map update.

Do not invent values to complete the map. During Phase 0, verify exact
IDs, CSS headings, selectors and current file ownership against the
source files. Use `PENDING VERIFICATION` when a fact cannot yet be
confirmed. During migration, use `CURRENT`, `MIGRATED` and `PENDING`
status labels where they help distinguish the current implementation
from the target architecture.

### 17.2 Initial `FRONTEND_MAP.md` template

Use the following as the initial file content. It is a project-specific
starting point, not a substitute for checking the source. In particular,
replace every verification note only after inspecting the actual source.

````markdown
# Kharcha Tracker Frontend Map

## 1. Purpose and usage

This file is the navigation index for the Kharcha Tracker frontend.
Use it to identify the small set of files relevant to a targeted change.
The implementation and migration rules are defined in
`kharcha-frontend-refactoring-spec.md` (or the approved specification
filename used in this repository).

**Navigation rule:** For a targeted screen change, start with the mapped
HTML view, then inspect its mapped feature module. Inspect the relevant
CSS section only when the change affects presentation. Follow the
cross-feature dependencies below when a behaviour is shared.

## 2. Migration status

| Item | Status / value |
|---|---|
| Overall status | CURRENT SOURCE INVENTORY / MIGRATION NOT YET VERIFIED |
| Last completed phase | PENDING VERIFICATION |
| Current phase | PENDING VERIFICATION |
| Last updated | YYYY-MM-DD |
| Source baseline | `index.html`, `css/app.css`, `js/app.js`, `js/api.js`, `js/charts.js`, `js/icons.js` |

Status meanings:

- `CURRENT`: verified location in the implementation being inspected.
- `MIGRATED`: responsibility has been moved and verified in its target.
- `PENDING`: planned or not yet verified; do not assume it exists.

## 3. Route and view map

The target HTML and feature columns below describe the approved
architecture. Update the status and actual path columns as each phase is
implemented. Do not mark a target file as current before it exists and
has been verified.

| Route | Existing root DOM ID | Target HTML file | Container | Feature module(s) | Status |
|---|---|---|---|---|---|
| `login` | `view-login` | `views/auth/login.html` | `auth-view-container` | `features/auth.js` | PENDING |
| `signup` | `view-signup` | `views/auth/signup.html` | `auth-view-container` | `features/auth.js` | PENDING |
| `dashboard` | `view-dashboard` | `views/dashboard/dashboard.html` | `view-container` | `features/dashboard.js` | PENDING |
| `groups` | `view-groups` | `views/groups/groups.html` | `view-container` | `features/groups.js`, `features/invitations.js`, `features/settlement.js`, `features/expenses.js` | PENDING |
| `expenses` | `view-expenses` | `views/expenses/expenses.html` | `view-container` | `features/expenses.js` | PENDING |
| `activity` | `view-activity` | `views/activity/activity.html` | `view-container` | `features/activity.js` | PENDING |
| `settings` | `view-settings` | `views/settings/settings.html` | `view-container` | `features/settings.js`, `features/subscription.js` | PENDING |
| `subscription` | `view-subscription` | `views/subscription/subscription.html` | `view-container` | `features/subscription.js` | PENDING |

`magic` is a special authentication action, not a separate HTML view.
It must continue to use the existing magic-link verification flow and
login view.

Important screen boundaries:

- Invitations are a section within Groups, not a separate route.
- Group details are rendered within Groups group cards, not a separate
  route.
- Settlement UI is rendered within Groups.
- Notifications belong to the persistent application shell, not a
  standalone route.

## 4. Persistent application shell

The following containers are part of the approved target shell. Confirm
the exact IDs and preserve all existing shell IDs/classes when
extracting markup from the current `index.html`.

| Element / responsibility | ID or selector | Status / notes |
|---|---|---|
| Authentication view mount container | `auth-view-container` | Target container; verify implementation |
| Authenticated application shell | `app-shell` | Target shell wrapper; verify implementation |
| Main content element | `main` / `#main` | ID shown in target shell example; verify against source |
| Protected view mount container | `view-container` | Target container; verify implementation |
| Sidebar navigation | `#sidebar` | ID shown in target shell example; verify against source |
| Bottom tab navigation | `#tabbar` | ID shown in target shell example; verify against source |
| Toast root | `#toast` | ID shown in target shell example; verify against source |
| Modal root | `#modal-root` | ID shown in target shell example; verify against source |
| Top navigation | PENDING VERIFICATION | Record exact existing ID/class from source |
| Header / topbar | PENDING VERIFICATION | Record exact existing ID/class from source |
| User identity controls | PENDING VERIFICATION | Record exact existing IDs/classes from source |
| Notification panel and badge | PENDING VERIFICATION | Record exact existing IDs/classes from source |

Do not treat a target container shown in the specification's illustrative
shell as proof that the same ID already exists in the source. Mark target
containers as pending until implemented.

## 5. JavaScript module map

| File | Responsibility | Status |
|---|---|---|
| `js/app.js` | Application startup, shell composition and route registration | CURRENT SOURCE FILE; extraction pending |
| `js/api.js` | HTTP client, API calls and token refresh | CURRENT; retain independent responsibility |
| `js/charts.js` | SVG chart generation | CURRENT; retain independent responsibility |
| `js/icons.js` | SVG icon definitions | CURRENT; retain independent responsibility |
| `js/core/dom.js` | DOM helpers such as `$` and `$all` | PENDING |
| `js/core/state.js` | Current user and shared application caches | PENDING |
| `js/core/view-loader.js` | Fetch, validate, mount and cache route HTML | PENDING |
| `js/core/router.js` | Hash routing, route registry, guards and navigation sequencing | PENDING |
| `js/ui/modal.js` | Generic modal open/close and Escape handling | PENDING |
| `js/ui/toast.js` | Toast display | PENDING |
| `js/ui/forms.js` | Shared form error and busy-state helpers | PENDING |
| `js/features/auth.js` | Login, signup, magic-link and session flows | PENDING |
| `js/features/dashboard.js` | Dashboard data, rendering and bindings | PENDING |
| `js/features/groups.js` | Group cards, group operations and notes | PENDING |
| `js/features/expenses.js` | Expense rendering, forms, splits and CRUD | PENDING |
| `js/features/settlement.js` | Settlement and payout UI/actions | PENDING |
| `js/features/invitations.js` | Invitation list and actions | PENDING |
| `js/features/notifications.js` | Notification rendering and actions | PENDING |
| `js/features/activity.js` | Activity loading, filters, pagination and CSV export | PENDING |
| `js/features/settings.js` | Settings screen initialization | PENDING |
| `js/features/subscription.js` | Subscription status and plan actions | PENDING |
| `js/utils/formatters.js` | Shared date/amount formatting | PENDING |
| `js/utils/html.js` | HTML escaping helper(s) | PENDING |

Update this table when files are created, moved, renamed or when
responsibility changes. The status must describe implementation reality,
not planned completion.

## 6. Shared state ownership

| State / data | Owner | Notes / status |
|---|---|---|
| Current authenticated user | `core/state.js` (target) | Identify current source declaration in `js/app.js` during Phase 0 |
| Shared group/expense/invitation caches | `core/state.js` (target) | Preserve one shared cache; do not duplicate it across features |
| Authentication/session token handling | `js/api.js` | Preserve existing API and token-refresh behaviour |
| View mounted/loading state | `core/view-loader.js` (target) | Track mounted views and in-flight fetches |
| Active route/navigation sequence | `core/router.js` (target) | Prevent stale asynchronous navigation from winning |

Record the actual variable names and source locations during Phase 0
when they can be verified. Do not create duplicate state owners during
extraction.

## 7. API client

- **Current file:** `js/api.js`
- **Responsibility:** HTTP/API access and existing token-refresh logic.
- **Rule:** Feature modules import the API client; they must not duplicate
  HTTP or token-refresh implementations.
- **Status:** CURRENT SOURCE FILE; verify its exported interface during
  Phase 1.

Record the actual public exports and endpoint groupings here after
inspecting `js/api.js`; do not infer endpoint names.

## 8. Charts and icons

| Concern | File | Responsibility | Status |
|---|---|---|---|
| Charts | `js/charts.js` | SVG chart generation | CURRENT; verify exported interface |
| Icons | `js/icons.js` | SVG icon definitions | CURRENT; verify exported interface |

Screen-specific icon initialization should move to the owning feature's
`mount()` lifecycle. Persistent shell icons should be initialized by
shell setup. Preserve the existing icon and chart output.

## 9. CSS map

**Stylesheet:** `css/app.css` (retain as one consolidated stylesheet).

| Screen / concern | Actual CSS heading or selector group | Status |
|---|---|---|
| Auth | PENDING VERIFICATION | Inspect `css/app.css` |
| Dashboard | PENDING VERIFICATION | Inspect `css/app.css` |
| Groups | PENDING VERIFICATION | Inspect `css/app.css` |
| Expenses | PENDING VERIFICATION | Inspect `css/app.css` |
| Activity | PENDING VERIFICATION | Inspect `css/app.css` |
| Settings | PENDING VERIFICATION | Inspect `css/app.css` |
| Subscription | PENDING VERIFICATION | Inspect `css/app.css` |
| Shared shell / navigation | PENDING VERIFICATION | Inspect `css/app.css` |
| Modals, forms and toasts | PENDING VERIFICATION | Inspect `css/app.css` |

Use real section headings or stable selector groups from the stylesheet.
Do not add line numbers unless there is a deliberate process to keep them
updated; line numbers become stale as CSS changes.

## 10. Cross-feature dependencies

- The Groups screen contains group cards, invitation content, expense
  content and settlement UI. These are not separate routes.
- The Expenses screen and Groups screen both use expense behaviour.
  Preserve the shared expense action handling and avoid registering
  duplicate click handlers.
- The current implementation delegates cross-screen expense actions
  through the persistent `#main` ancestor. Keep one appropriately
  scoped delegated handler registered once, unless source inspection
  demonstrates that the implementation has changed.
- Settings displays subscription summary information; record the exact
  settings/subscription data dependency after verifying the source.
- Notifications are part of the persistent shell and must not be
  initialized as a route view.
- Authentication state affects access to protected routes. Keep route
  guards and session behaviour consistent with the existing app.

Add or revise entries when implementation reveals another real
cross-feature dependency. Do not list speculative dependencies.

## 11. Targeted change workflow

For a change limited to one screen:

1. Find the route in Section 3 and open its HTML file.
2. Open only the feature module(s) mapped to that screen.
3. Follow Section 10 if the behaviour crosses feature boundaries.
4. Inspect the relevant verified CSS section only if presentation
   changes are involved.
5. Inspect `api.js`, `charts.js`, `icons.js` or shared core/UI modules
   only when the mapped feature imports or uses them.
6. Update this map if the change affects file ownership, routes, IDs,
   state or dependencies.

## 12. Update log

| Date | Phase | Files / responsibilities changed | Verification |
|---|---|---|---|
| YYYY-MM-DD | Phase 0 | Initial source inventory and map created | PENDING |

````

The initial template intentionally marks target modules and unverified
shell/CSS details as pending. During Phase 0, the implementer must replace
those placeholders with verified source facts where possible. After each
migration phase, update the map so it remains an accurate guide to the
code that exists at that point.

### 17.3 Update rules

- Update the map whenever files or responsibilities change.
- Do not postpone its first creation until the end of the refactor.
- Distinguish current implementation from target architecture.
- Remove temporary `CURRENT`, `MIGRATED` and `PENDING` labels only when
the final structure has been verified.
- Do not write duplicate implementation instructions in the map; link
  back to this specification for detailed rules.

------------------------------------------------------------------------

## 18. Definition of done

The refactor is complete only when all the following are true:

1.  Each of the eight existing route views has its own HTML file.
2.  `index.html` contains the persistent application shell and mount
    containers, not all screen markup.
3.  Views load through native `fetch()` and are retained after first
    mount.
4.  Hash routes and route guards behave as before.
5.  JavaScript uses native ES modules and explicit imports/exports.
6.  `app.js` is a bootstrap/composition module rather than a feature
    monolith.
7.  Feature responsibilities are separated according to this
    specification.
8.  Shared caches are not duplicated.
9.  Event listeners are not registered repeatedly on route changes.
10. Existing API, authentication, chart and icon behaviour is preserved.
11. `css/app.css` remains the consolidated stylesheet.
12. No framework, bundler, template engine or new runtime dependency is
    introduced.
13. All relevant regression tests pass.
14. `FRONTEND_MAP.md` accurately describes the final implementation.
15. Any pre-existing issue discovered during migration is documented
    rather than silently changed.

------------------------------------------------------------------------

## 19. Final architectural decision

The approved target is:

**Screen-oriented HTML + feature-oriented JavaScript + consolidated
CSS + native browser loading.**

This architecture directly addresses the original motivation: a person
or smaller coding model should be able to locate the HTML for a
particular screen and make a targeted UI change without scanning the
entire `index.html`.

It also addresses the larger maintainability issue by breaking the
current monolithic `app.js` into understandable feature modules.

The migration must remain incremental and behaviour-preserving. Do not
rewrite the application from scratch.

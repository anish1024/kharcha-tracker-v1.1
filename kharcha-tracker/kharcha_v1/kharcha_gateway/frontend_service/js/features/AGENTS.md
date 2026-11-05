# AGENT.md — Kharcha Tracker frontend service: features

This folder consolidates all feature modules into one agent-facing
document. Each feature module lives in its own subfolder under
`js/features/` and exposes `mount()` / `onEnter()` patterns.

## Feature map

| Feature | Module file | Owns view(s) | Key responsibility |
|---|---|---|---|
| auth | `js/features/auth/AGENT.md` | `views/auth/*` | login, signup, magic-link, session |
| dashboard | `js/features/dashboard/AGENT.md` | `views/dashboard/*` | charts, expense summary, stats |
| groups | `js/features/groups/AGENT.md` | `views/groups/*` | group cards, CRUD, notes |
| expenses | `js/features/expenses/AGENT.md` | `views/expenses/*` | expense list, forms, splits, CRUD |
| settlement | `js/features/settlement/AGENT.md` | `views/groups/*` | settle, payouts, ad-hoc, corpus |
| invitations | `js/features/invitations/AGENT.md` | `views/groups/*` | invite list, accept/decline |
| notifications | `js/features/notifications/AGENT.md` | `views/*` shell | bell panel, mark-read |
| activity | `js/features/activity/AGENT.md` | `views/activity/*` | activity log, filters, CSV |
| settings | `js/features/settings/AGENT.md` | `views/settings/*` | settings init, #stat-plan |
| subscription | `js/features/subscription/AGENT.md` | `views/subscription/*` | plans, #stat-plan update |

> Note: the consolidated `js/features/AGENT.md` is the canonical index.
> Per-feature subdirectories retain their own `AGENT.md` for localized
> agent guidance, but the consolidated index is authoritative for a
> quick targeted change.

## Consolidated feature details

### auth

Module: `js/features/auth.js`
Exports: `mountLogin`, `mountSignup`, `handleMagicLink`, `logout`,
`onExpired`, `applyUserChrome`.

DOM IDs used: `#magic-icon`, `#magic-btn`, `#magic-link-email`,
`#magic-link-submit`, `#magic-error`, `#form-login`, `#login-email`,
`#login-password`, `#login-submit`, `#login-error`, `#login-magic`,
`#form-signup`, `#signup-name`, `#signup-email`, `#signup-password`,
`#signup-submit`, `#signup-error`, `#signup-login`, `#btn-logout`,
`#btn-bell`.

State: `state.currentUser`, `magicInFlight`.

Endpoints: `POST /auth/login`, `POST /auth/signup`, `POST /auth/magic-link`,
`POST /auth/magic-link/verify`.

Files: `views/auth/login.html`, `views/auth/signup.html`,
`js/features/auth.js`, `js/api.js`, `js/core/router.js`, `js/ui/*`.

---

### dashboard

Module: `js/features/dashboard.js`
Exports: `mount`, `onEnter`.

DOM IDs used: `#dashboard-sub`, `#stat-plan`, `#stat-groups`, `#stat-invites`,
`#dash-expense-group`, `#dash-expense-empty`, `#dash-expense-stats`,
`#dash-trend-empty`, `#dash-charts`, `#dash-bar-chart`, `#dash-bar-empty`,
`#dash-pie-chart`, `#dash-pie-legend`, `#dash-pie-total`, `#dash-range-toggle`.

State: `state.dashboard.{groupId, range}`, `state.groupsCache`,
`state.expensesCache`.

Endpoints: `GET /groups`, `GET /groups/{id}/expenses`,
`GET /users/me/subscription`.

Charts: `Charts.stackedBarChart`, `Charts.pieChart` from `js/charts.js`.

Files: `views/dashboard/dashboard.html`, `js/features/dashboard.js`,
`js/charts.js`, `js/features/subscription.js`, `css/app.css`.

---

### groups

Module: `js/features/groups.js`
Exports: `mount`, `loadGroups`, `refreshGroupCount`, `bindGroupActions`.

DOM IDs used: `#groups-list`, `#btn-new-group`, `#btn-new-group-icon`,
`#form-create-group`, `#new-group-name`, `#new-group-currency`,
`#create-group-error`, `#btn-cancel-create-group`, `#btn-join-group`,
`#join-icon`, `#form-join-group`, `#join-group-id`, `#join-code`,
`#join-error`, `#join-submit`, `#btn-invite-email`, `#invite-icon`,
`#form-invite-email`, `#invite-group`, `#invite-email`, `#invite-error`,
`#invite-submit`, `#invites-list`.

State: `state.groupsCache`, `state.membersCache`, `state.expensesCache`,
`state.settleCache`, `state.notesCache`.

Endpoints: many group/note/settlement/payout/invitation endpoints.

Files: `views/groups/groups.html`, `js/features/groups.js`,
`js/features/invitations.js`, `js/features/settlement.js`,
`js/features/expenses.js`, `css/app.css`.

---

### expenses

Module: `js/features/expenses.js`
Exports: `renderExpensesScreen`, `mount`, `expensesListHtml`,
`bindCrossScreenExpenseActions`, `loadGroupExpenses`.

DOM IDs used: `#expenses-group-select`, `#expenses-list`, add-expense form
IDs, `#expenses-export-btn`, `#expenses-import-btn`, `#expenses-import-file`.

State: `state.expenses.groupId`, `state.expensesCache`.

Endpoints: `GET /groups/{id}/expenses`, `POST /expenses`,
`PATCH /expenses/{id}`, `DELETE /expenses/{id}`, split endpoints,
`GET /groups/{id}/expenses/export` (portable JSON export, user names in
place of user ids), `POST /groups/{id}/expenses/import` (partial import;
response `{imported, skipped:[{index, reason}]}`).

Export/Import: `exportExpensesJson()` fetches the export endpoint and
downloads a Blob (mirrors `exportActivityCsv` in `activity.js`);
`importExpensesJson(file)` parses the JSON file and POSTs it, toasts the
imported/skipped summary and refreshes the list. Both require a selected
group (buttons hidden otherwise by `applyExpensesGroup`).

Files: `views/expenses/expenses.html`, `js/features/expenses.js`,
`js/features/groups.js`, `js/icons.js` (`Icons.download`, `Icons.upload`),
`css/app.css`.

---

### settlement

Module: `js/features/settlement.js`
Exports: `bindSettlementActions`, `mount`.

DOM IDs used: `.settle-panel`, settlement rows, payout rows, ad-hoc,
corpus.

State: `state.settleCache`, `state.expensesCache`, `state.membersCache`.

Endpoints: settle/payout/payments endpoints.

Files: `views/groups/groups.html`, `js/features/settlement.js`,
`js/features/groups.js`, `css/app.css`.

---

### invitations

Module: `js/features/invitations.js`
Exports: `mount`, `loadInvitations`, `refreshInviteCount`.

DOM IDs used: `#invites-list`.

State: `state.groupsCache`, `state.currentUser`.

Endpoints: `GET /users/me/invitations`,
`POST /invitations/{id}/accept`, `POST /invitations/{id}/decline`.

Files: `views/groups/groups.html`, `js/features/invitations.js`,
`js/features/groups.js`, `js/features/notifications.js`.

---

### notifications

Module: `js/features/notifications.js`
Exports: `mount`, `loadNotifications`, `renderNotifications`,
`toggleNotifPanel`, `markNotificationsRead`, `bindShellNotifications`.

DOM IDs used: `#btn-bell`, `#notif-panel`, `#notif-list`,
`#notif-mark-read`.

State: `state.currentUser`.

Endpoints: `GET /users/me/notifications`,
`POST /users/me/notifications/read`.

Files: `index.html`, `js/features/notifications.js`, `js/app.js`,
`css/app.css`.

---

### activity

Module: `js/features/activity.js`
Exports: `mount`, `onEnter`, `loadActivity`, `renderActivity`,
`bindActivityActions`, `exportActivityCsv`.

DOM IDs used: `#activity-group`, `#activity-start`, `#activity-end`,
`#activity-page-size`, `#activity-table`, `#activity-count`,
`#activity-pager`, `#activity-export`.

State: `state.activity.{groups, groupId, page, total}`.

Endpoints: `GET /groups`, `GET /activity`.

Files: `views/activity/activity.html`, `js/features/activity.js`,
`css/app.css`.

---

### settings

Module: `js/features/settings.js`
Exports: `mount`, `onEnter`.

DOM IDs used: `#user-name`, `#user-email`, `#user-avatar`, `#form-profile`,
`#stat-plan`.

State: `state.currentUser`.

Endpoints: `GET /users/me/subscription`.

Files: `views/settings/settings.html`, `js/features/settings.js`,
`js/features/subscription.js`.

---

### subscription

Module: `js/features/subscription.js`
Exports: `mount`, `onEnter`, `loadSubscription`, `refreshPlanStat`,
`refreshSettingsPlan`, `bindSubscriptionActions`, `updatePlanStat`.

DOM IDs used: `#stat-plan`, plan cards, cancel/switch buttons.

State: `state.currentUser`.

Endpoints: `GET /users/me/subscription`, `POST /subscription`.

Files: `views/subscription/subscription.html`, `js/features/subscription.js`,
`js/features/settings.js`.

## Cross-feature wiring notes

- `app.js` registers routes via `registerRoute(route, { mount, onEnter })`.
- `bindCrossScreenExpenseActions()` bound once at boot on `#main`.
- Settlement delegated handler via `bindSettlementActions()` from
  `groups.js`.
- Invitations mounted after groups view inserted.

## Rules (see top-level AGENT.md)

- No new `graph.yaml` fields.
- No frameworks/bundlers.
- No HTTP duplication.
- No circular imports.
- No profile-update API invention.
- Verify with `node --check`.

## Files to touch for a change spanning features

1. Top-level `AGENT.md` / `README.md`.
2. Relevant per-feature module and its owning view.
3. Relevant shared module(s).
4. `FRONTEND_MAP.md` / `docs/`.
5. `css/app.css` if presentation changes.
# AGENT.md — Kharcha Tracker feature: dashboard

## Module

- `js/features/dashboard.js`

## Exports

- `mount()` — binds `#dash-expense-group`, `#dash-range-toggle`
- `onEnter()` — welcome text, chrome, refreshes stats/notifications

## DOM IDs used

- `#dashboard-sub`, `#stat-plan`, `#stat-groups`, `#stat-invites`
- `#dash-expense-group`, `#dash-expense-empty`, `#dash-expense-stats`
- `#dash-trend-empty`, `#dash-charts`, `#dash-bar-chart`,
  `#dash-bar-empty`, `#dash-pie-chart`, `#dash-pie-legend`, `#dash-pie-total`,
  `#dash-range-toggle`

## State touched

- `state.dashboard.groupId`, `state.dashboard.range`
- `state.groupsCache`, `state.expensesCache`

## Endpoints

- `GET /groups` (cached)
- `GET /groups/{id}/expenses` (cached)
- `GET /users/me/subscription` (via refreshPlanStat)

## Chart

- Uses `Charts.stackedBarChart`, `Charts.pieChart` from `js/charts.js`

## Files to touch for dashboard bugs

1. `views/dashboard/dashboard.html`
2. `js/features/dashboard.js`
3. `js/charts.js`
4. `js/features/subscription.js` (#stat-plan)
5. `css/app.css` (Dashboard, Charts, Cards, Stats)

## Notes

- Stacked bar chart now renders per-bar total labels (Phase 7).
- No click-to-pie behaviour (removed in revert).

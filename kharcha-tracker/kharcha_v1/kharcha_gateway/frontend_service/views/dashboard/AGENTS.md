# AGENT.md — Kharcha Tracker view: dashboard

## View file

- `views/dashboard/dashboard.html`

## Root element

- `<section id="view-dashboard" class="view">`

## Dominant DOM IDs

- `#stat-plan` — dashboard stat, shared with Settings and Subscription
- `#stat-groups` — group count stat
- `#stat-invites` — pending invites stat
- `#dash-expense-group` — group selector
- `#dash-expense-empty` / `#dash-expense-stats` — summary panels
- `#dash-trend-empty` — empty state
- `#dash-charts` — charts container
- `#dash-bar-chart` — stacked bar chart SVG container
- `#dash-pie-chart` — pie chart SVG container
- `#dash-pie-legend` — legend list
- `#dash-pie-total` — bucket total note
- `#dash-range-toggle` — daily/weekly/monthly toggle ([data-range])
- `#trend-snapshot...` (activity)

## Owning feature modules

- `js/features/dashboard.js` — `mount()`, `onEnter()`
- `js/charts.js` — `stackedBarChart()`, `pieChart()` rendering
- `js/features/subscription.js` — refreshes `#stat-plan`

## State touched

- `state.dashboard.groupId`
- `state.dashboard.range`
- `state.groupsCache`, `state.expensesCache`

## Endpoints

- `GET /groups/{id}/expenses` (via loadExpenseSummary → loadGroupExpenses)
- `GET /users/me/subscription` (via refreshPlanStat)

## CSS sections

- Dashboard, Charts, Cards, Stat-grid

## Files to touch for dashboard bugs

1. `views/dashboard/dashboard.html`
2. `js/features/dashboard.js`
3. `js/charts.js` (chart SVG rendering)
4. `js/features/subscription.js` (#stat-plan)
5. `css/app.css` (Dashboard section)

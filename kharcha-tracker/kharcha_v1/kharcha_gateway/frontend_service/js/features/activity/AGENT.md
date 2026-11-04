# AGENT.md — Kharcha Tracker feature: activity

## Module

- `js/features/activity.js`

## Exports

- `mount()`, `onEnter()`
- `loadActivity()`, `renderActivity()`, `bindActivityActions()`
- `exportActivityCsv()`

## DOM IDs used

- `#activity-group`, `#activity-start`, `#activity-end`
- `#activity-page-size`, `#activity-table`, `#activity-count`
- `#activity-pager`, `#activity-export`

## State touched

- `state.activity.groups`, `state.activity.groupId`,
  `state.activity.page`, `state.activity.total`

## Endpoints

- `GET /groups` (selector)
- `GET /activity` with query params

## CSS sections

- Activity log, Cards, Tables, Forms

## Files to touch for activity bugs

1. `views/activity/activity.html`
2. `js/features/activity.js`
3. `css/app.css` (Activity log section)

## Notes

- Pagination handled client-side
- CSV export chunked to avoid blocking
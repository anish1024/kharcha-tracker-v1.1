# AGENT.md — Kharcha Tracker view: activity

## View file

- `views/activity/activity.html`

## Root element

- `<section id="view-activity" class="view">`

## Dominant DOM IDs

- `#activity-group` — group selector
- `#activity-start`, `#activity-end` — date range
- `#activity-page-size` — page size
- `#activity-table` — event table
- `#activity-count` — total count
- `#activity-pager` — pagination controls
- `#activity-export` — CSV export button

## Owning feature module

- `js/features/activity.js` — `mount()`, `onEnter()`, `loadActivity()`,
  `renderActivity()`, `bindActivityActions()`, `exportActivityCsv()`

## State touched

- `state.activity.groups`, `state.activity.groupId`, `state.activity.page`,
  `state.activity.total`

## Endpoints

- `GET /groups` (groups for selector)
- `GET /activity` with query params (group, start, end, page, size)

## CSS sections

- Activity log, Cards, Tables

## Files to touch for activity bugs

1. `views/activity/activity.html`
2. `js/features/activity.js`
3. `css/app.css` (Activity log section)
# AGENT.md — Kharcha Tracker view: subscription

## View file

- `views/subscription/subscription.html`

## Root element

- `<section id="view-subscription" class="view">`

## Dominant DOM IDs

- `#stat-plan` — shared stat on dashboard; also updated here
- `#btn-cancel-current-plan`, `#btn-switch-plan-*` — plan switch buttons

## Owning feature module

- `js/features/subscription.js` — `mount()`, `onEnter()`

## State touched

- `state.dashboard.groupId` (data flow between dashboard & subscription)

## Endpoints

- `GET /users/me/subscription`, `POST /subscription`

## CSS sections

- Plans, Cards

## Files to touch for subscription bugs

1. `views/subscription/subscription.html`
2. `js/features/subscription.js`
3. `js/features/settings.js` (#stat-plan update)
4. `css/app.css` (Plans section)

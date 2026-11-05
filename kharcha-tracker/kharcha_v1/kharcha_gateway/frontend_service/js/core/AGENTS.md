# AGENT.md — Kharcha Tracker core modules

## `js/core/dom.js`

- Exports `$()` and `$all()` helpers
- Used by every feature module for DOM selection
- No DOM IDs of its own — applies IDs from owning features
- Import pattern: `import { $, $all } from "../core/dom.js"`

## `js/core/state.js`

- Central shared state (`state`) for all feature caches
- Namespaced caches:
  - `dashboard: { groupId, range }`
  - `groupsCache: []`
  - `membersCache: {}`
  - `expensesCache: {}`
  - `settleCache: {}`
  - `notesCache: {}`
  - `expenses: { groupId }`
  - `activity: { groups, groupId, page, total }`
- Also exports `memberName(member)` helper
- Primary owner: feature modules read+write via `state.*`
- Import pattern: `import { state } from "../core/state.js"`
- Do not import feature modules from core
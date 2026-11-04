# Kharcha Tracker — Frontend Service (core)

Shared low-level modules used by features and the shell.

| File | Responsibility | Has an `AGENT.md`? |
|---|---|---|
| `js/core/dom.js` | `$`, `$all` DOM helpers | yes (`js/core/AGENT.md`) |
| `js/core/state.js` | `state` shared state object + `memberName` helper | yes (`js/core/AGENT.md`) |
| `js/core/view-loader.js` | fetch, validate, mount, cache route HTML | n/a (router doc) |
| `js/core/router.js` | hash router, route registry, guards, nav | n/a (router doc) |

See `js/core/AGENT.md` for the per-module detail and the shared-state
fields owned here.

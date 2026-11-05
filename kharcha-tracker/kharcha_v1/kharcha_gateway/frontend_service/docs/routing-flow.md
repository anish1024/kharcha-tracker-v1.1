Here's the routing flow exactly as it runs now.

## 1. Startup

`index.html` loads `js/app.js` as a module. On `DOMContentLoaded`, `boot()` runs (`js/app.js:2689`):

1. Initializes shell icons (`#btn-bell`, `#btn-logout`).
2. Calls `renderNav()` from `js/core/router.js`, which fills `#sidebar`, `#topnav`, and `#tabbar` with plain `<a href="#/route">` links.
3. Binds the persistent cross-screen expense handler on `#main` (once).
4. Binds shell-only controls: logout, notification panel, mark-all-read, outside-click close.
5. Registers `auth:expired` / `subscription:required` listeners.
6. Calls `setMagicHandler(handleMagicLink)` and `registerRoute()` for all eight routes.
7. Restores the session if a token exists: `GET /users/me`; on a network failure (`status === 0`) it falls back to cached `Auth.user`, otherwise clears auth.
8. Calls `startRouter()`.

`startRouter()` is idempotent: it adds one `hashchange` listener and immediately calls `router()` for the current URL.

## 2. Route parsing

`currentRoute()` (`js/core/router.js:45`) reads the hash:

```js
location.hash.replace(/^#\/?/, "").split("?")[0] || "login"
```

So:

| URL | Route |
|---|---|
| `#/dashboard` | `dashboard` |
| `#dashboard` | `dashboard` |
| `#/groups?sort=x` | `groups` |
| `#/magic?token=...` | `magic` |
| empty hash | `login` |

Query strings are ignored by route matching but still parsed by the magic-link handler.

## 3. One navigation, step by step

Every navigation enters `router()` (`js/core/router.js:150`), including the initial load, every `hashchange`, and every `navigate()` call:

1. **Bump the sequence**: `sequence = ++navigationSequence`. Every later `await` is followed by `if (sequence !== navigationSequence) return;`. This is how a slow view fetch or slow `onEnter` gets discarded when the user navigates again.
2. **Resolve the route** with `currentRoute()`.
3. **Handle `magic` separately** (below).
4. **Compute guards**:
   - `isAuth` = `login` or `signup`
   - `authed` = `Boolean(Auth.access)` — i.e. does `kharcha_access_token` exist in localStorage.
5. **Apply guards** (before any fetch):
   - Protected route + no token → `navigate("login")`
   - Auth route + token + cached `Auth.user` → `navigate("dashboard")`
   - Unknown route → `navigate(authed ? "dashboard" : "login")`
6. **Mount the view** with `ensureViewMounted(route)` — fetch it if this is the first visit, otherwise reuse the existing root.
7. **Check the sequence** again; a stale navigation stops here.
8. **Run the route's `mount()` once** (if it has not run before).
9. **Check the sequence** again.
10. **Reveal the view**: clear any error box, toggle visibility (`showView`), set the active nav item, set `document.title`.
11. **Run the route's `onEnter()` on every entry**, awaiting it so overlapping navigations still sequence correctly.

`navigate(route)` (`js/core/router.js:50`) is just a hash setter:

```js
location.hash = "#/" + route;
```

If the hash is already that value, it calls `router()` directly instead of waiting for a `hashchange` event. Redirects therefore happen as a second router pass, not as recursion.

## 4. Guards and redirects

| Situation | Result |
|---|---|
| `#/dashboard` with no token | Redirect to `#/login` |
| `#/login` with token **and** cached `Auth.user` | Redirect to `#/dashboard` |
| `#/login` with token but no cached user | Login view is allowed to show |
| Unknown route with token | Redirect to `#/dashboard` |
| Unknown route without token | Redirect to `#/login` |
| `#/magic?token=...` | Login view is shown, then `handleMagicLink()` runs |

Notable asymmetry: protecting a route only requires a token, while bouncing an authenticated user away from Login/Signup requires both a token and cached user data.

## 5. View loading and caching

`js/core/view-loader.js` holds a fixed registry:

```js
route → { file, container, rootId }
```

`ensureViewMounted(route)`:

1. Returns the cached root from `mountedViews` if present.
2. Joins an existing `inFlightViews` promise if the same view is already loading.
3. Otherwise calls `loadView()`, then stores the root in `mountedViews` and removes the in-flight entry in `finally`.

`loadView()`:

1. Confirms the target container exists (`#auth-view-container` or `#view-container`).
2. Returns an existing DOM element with the expected root id if one already exists (defensive fallback).
3. `fetch()`es the HTML file relative to `document.baseURI`.
4. Parses it with `DOMParser`.
5. Rejects markup that contains a `<script>` or does not consist of exactly one root element.
6. Requires the root to be `<section id="view-...">` matching the registry.
7. Appends the root to its container and returns it.

The fetched view keeps its original `hidden` attribute; the router decides when it becomes visible. A failed fetch is **not** cached, so Retry re-fetches.

## 6. Mount vs. onEnter

The router tracks:

- `routeFeatures: Map<route, { mount, onEnter }>`
- `mountedFeatures: Set<route>` — guarantees `mount()` runs at most once per page session.

`mount()` owns one-time wiring: form listeners, delegated handlers, screen-specific icon injection.

`onEnter()` owns per-visit work: loading data and refreshing the screen.

| Route | `mount()` (once) | `onEnter()` (every entry) |
|---|---|---|
| `login` | inject magic icon, bind login form | — |
| `signup` | bind signup form | — |
| `dashboard` | `bindDashboard()` | welcome text, chrome, counts, plan, invites, notifications, expense summary |
| `groups` | icons, `bindGroupActions()`, `bindInvitationActions()` | chrome, `loadGroups()`, `loadInvitations()` |
| `expenses` | icon, `bindExpensesScreen()` | chrome, `await loadGroups()`, `renderExpensesScreen()` |
| `activity` | icon, `bindActivityScreen()` | chrome, `await loadActivityScreen()` |
| `settings` | bind settings logout button | populate profile, clear errors, chrome, refresh plan |
| `subscription` | `bindSubscriptionActions()` | chrome, `loadSubscription()` |

Because mount is once-only, navigating away and back does not re-add listeners; `onEnter` still refreshes data each time.

## 7. Visibility

`showView(route, isAuth)` (`js/core/router.js:90`):

- Hides every `.view` except `#view-<route>`.
- Hides `#app-shell` when the route is auth, shows it otherwise.
- Hides `#auth-view-container` for protected routes and `#view-container` for auth routes.

All views remain in the DOM after their first mount, just hidden.

## 8. Error path

If fetching/validating a view fails:

- `showViewError()` reveals the target container (or auth container).
- Appends a `.empty` box with `data-view-error="<route>"`, a readable message, and a **Retry** button.
- Retry removes the error box and calls `router()` again.
- The failed view is not in `mountedViews`, so the next attempt fetches again.

Errors thrown by `onEnter()` are caught and only logged with `console.error`; the view stays visible with whatever state it reached. There is no user-facing error for that case.

## 9. Magic-link flow

`#/magic?token=...` is not a view:

1. `router()` ensures the **login** view is mounted and shown.
2. Calls `handleMagicLink()`.
3. `handleMagicLink()` extracts the token from the hash, rejects empty tokens, calls `POST /auth/magic-link/verify`, establishes the session, then `navigate("dashboard")`.
4. On failure it shows a toast and navigates back to `login`.
5. A `magicInFlight` flag prevents duplicate verification runs.

## 10. What triggers routing

- Initial `startRouter()` call.
- Any `hashchange` (nav links, browser back/forward, manual hash edits).
- `navigate()` calls from feature code.
- Retry button in a view error box.
- `onExpired()` / `onSubscriptionRequired()` indirectly, via `navigate()`.

There is no History API usage, no route table for unknown pages beyond the redirect rule, and no per-route teardown.

## 11. Rough edges worth knowing

- Guard checks use the token and `Auth.user`, not the in-memory `currentUser`.
- If a user opens Login with a token but an empty cached user, Login is shown rather than redirected.
- `mountFeature()` records the route as mounted *before* calling `mount()`, so a mount that throws will never be retried automatically.
- `showView()` only looks at `.view` elements currently in the document. This is fine while mounted views stay attached, but a manually removed view would still be returned by `mountedViews` as a detached node.
- `onEnter()` failures are silent to the user.
- First paint on a cold auth route is blank until the login HTML fetch finishes; `#app-shell` is hidden from the start, and the auth container is empty until the view mounts.

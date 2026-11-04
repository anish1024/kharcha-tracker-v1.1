# AGENT.md — Kharcha Tracker feature: auth

## Module

- `js/features/auth.js`

## Exports

- `mountLogin()`, `mountSignup()`
- `handleMagicLink()` (registered via router `setMagicHandler()`)
- `logout()`, `onExpired()`, `applyUserChrome()`

## DOM IDs used

- `#magic-icon`, `#magic-btn`, `#magic-link-email`, `#magic-link-submit`,
  `#magic-error`
- `#form-login`, `#login-email`, `#login-password`, `#login-submit`,
  `#login-error`, `#login-magic`
- `#form-signup`, `#signup-name`, `#signup-email`, `#signup-password`,
  `#signup-submit`, `#signup-error`, `#signup-login`
- `#btn-logout` (shell, injected at boot)
- `#btn-bell` (shell)

## State touched

- `state.currentUser`
- `magicInFlight` (module-local)

## Endpoints

- `POST /auth/login`, `POST /auth/signup`, `POST /auth/magic-link`,
  `POST /auth/magic-link/verify`

## Events

- Dispatches `auth:expired` (handled by auth.onExpired)
- Router redirects auth views on token presence

## Files to touch for auth bugs

1. `views/auth/login.html`, `signup.html`
2. `js/features/auth.js`
3. `js/api.js` (Auth)
4. `js/core/router.js` (route registration)
5. `js/ui/toast.js`, `js/ui/forms.js`, `js/ui/modal.js`
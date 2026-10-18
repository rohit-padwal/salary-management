# Frontend Conventions and Decisions

Implemented Angular/Material 22.2.3 with strict standalone components, zoneless change detection, OnPush, signals, typed Reactive Forms, and HttpClient providers. This stable baseline was checked on 2026-10-18; future upgrades must recheck [Angular compatibility](https://angular.dev/reference/versions) and published stable tags. Framework/CLI/Material versions are pinned; the lockfile fixes transitive versions and engine checks enforce compatible tooling. Node/npm versions, commands, and output paths are in the [frontend development guide](../frontend/README.md); complete setup is in the [root README](../README.md).

## Organization and Contracts

Feature folders under `frontend/src/app/` own employee directory/forms, analytics, authentication, reference data, and layout. Keep templates, styles, and specs beside their component. Routes lazy-load protected pages beneath the shell; public login sits outside. Small typed services own API requests; shared helpers are introduced only for actual reuse.

Money, IDs, and versions remain strings end to end. Never use Number conversion, numeric money pipes, or browser aggregation; the server owns monetary rules/rounding. Country and currency stay independent; catalog values come from the reference-data API, never invented fallbacks. Salary is a text input with exact string validation.

The shared normalization helper mirrors Java 21 `Character.isWhitespace`, unlike JavaScript `trim()`. Literal non-breaking characters remain part of names/searches; Unicode limits count code points. Recheck parity on Java upgrades. Salary syntax remains untrimmed. See the [coverage review](COVERAGE_REVIEW.md) for the discovered mismatch and verification.

## Interaction Invariants

| Area | Behavior |
| --- | --- |
| Directory | Server pages only; explicit Apply separates draft/applied search, filters, sort. Apply/size changes reset page zero; Reset retains size and restores default sort. Sizes 25/50/100. Salary sort requires selected currency. |
| Analytics | Explicit Apply/Reset, visible applied criteria, independent report/catalog retries; render exact server currency cards and grouped table. No client aggregation or polling. |
| Reads | Cancel superseded/destroyed requests; clear obsolete rows during loading/failure. Distinguish empty directory, no matches, out-of-range page, and safe retryable errors. |
| Creation | Refresh CSRF, send six string fields once, block duplicate submission, confirm server-normalized result. Uncertain saves direct HR to check directory; no automatic replay. |
| Editing | Shared form retains original ID/version outside controls. PUT sends all six fields plus version. No-change confirmation uses actual server metadata. |
| Conflict recovery | Stale/unknown conflict, missing update target, or uncertain response preserves draft and blocks Save. Explicit discard/reload replaces fields/version only after successful GET; failure retains draft. No automatic merge/retry. |

Field failures map known field/code pairs to local messages; arbitrary response text is never rendered. Catalog retries do not replay employee/report queries. Query state and drafts are memory-only, lost on leaving/reloading/session redirection; there is no away guard or persistent draft store. Request cancellation cannot promise server-side rollback.

## Session and Accessibility

Relative `/api` URLs use browser-managed cookies and Angular XSRF headers. Login obtains CSRF, posts URL-encoded credentials, refreshes rotated token, and reads session identity; password controls are cleared after submission. No credentials/tokens are stored in localStorage/sessionStorage. Protected activation fails closed; backend authorization is authoritative.

Return destinations are allowlisted employee directory/new/digits-edit and analytics routes, optionally with query/fragment. Non-auth API 401 clears local identity and redirects without replay; 403 is not treated as bad credentials. Logout posts once and clears identity on success; failed logout remains visible/retryable, and failed anonymous-token refresh cannot undo completed logout.

The shell provides responsive navigation, active links/titles, skip link, routed-content focus, and recoverable not-found page. Tables use captions/scoped headers and a focusable horizontal scroll region. Controls remain usable during loading; confirmations/errors receive appropriate focus/announcements. Material's local Azure theme and system fonts avoid external font requests.

## Verification and Extension

Vitest/HttpTestingController specs cover observable requests, exact values, cancellation, draft/applied criteria, validation, recovery, session behavior, navigation, and focus. [Verification](VERIFICATION.md) records actual connected Chrome checks and limits; these local scripts are not an installed application dependency or committed browser runner.

URL-persisted criteria, durable drafts, CSV, charts, historical reporting, conversion, and new roles require separate product/contracts decisions. Production Docker serves these assets through Nginx under the same API origin; development uses the configured proxy. See [architecture](ARCHITECTURE.md) for packaging trade-offs.

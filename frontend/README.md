# Salary Management Frontend

Angular 22.2.3 application foundation using standalone components, routing, strict TypeScript/templates, zoneless change detection, and typed HttpClient providers. The responsive shell includes the employee directory, a compensation analytics dashboard, active navigation, page titles, a skip link, and a recoverable not-found page. Login, session restoration, protected navigation, and logout are implemented. Department/country/currency filters and server sorting controls are implemented. Employee creation and version-checked editing are implemented; compensation analytics reporting is implemented.

## Setup

Use Node 24.22.0 and npm 11.19.1. `.nvmrc` records the Node version; with an existing nvm installation, run `nvm install` and `nvm use` in this directory. Alternatively use an official Node distribution. Engine checks reject unsupported runtimes.

On the current macOS ARM64 working copy, an official checksum-verified Node distribution is available under the repository's ignored `.tools/`. To use it for the current shell, run from the repository root:

```sh
export PATH="$PWD/.tools/node-v24.22.0-darwin-arm64/bin:$PATH"
cd frontend
```

This local binary is not committed. Fresh clones need their own compatible Node installation. Global Node defaults do not need to change.

```sh
node --version
npm --version
npm ci
npm test
npm run format:check
npm run build
npm start
```

`npm ci` installs the committed lockfile. The local CLI is used by npm scripts; no global Angular CLI is required. npm caches live in ignored `../.tools/npm-cache`.

## Development

Open `http://127.0.0.1:4200`. The configured development proxy forwards `/api` and nested API paths to `http://127.0.0.1:8080`, preserving the browser origin for session/CSRF integration. Start the backend separately with the `local` profile and the configured HR account described in the main setup guide. Opening a protected route without a session shows `/login`. Sign in with that configured account; credentials are not supplied by the frontend. The employee directory, creation/editing form, and authentication use real APIs; the analytics dashboard also reads real compensation reports. Restart the development server after changing `proxy.conf.json`.

`npm test` runs the CLI-managed Vitest suite once; `npm run test:watch` watches changes. `npm run format` formats frontend files. The production build writes to ignored `dist/salary-management-ui/browser`; packaging those assets with Spring Boot is a later step.

## Employee Directory

`employees/employee-api` performs typed GET requests with `page`, `size`, optional `q`/catalog filters, and a typed allowlisted sort. `reference-data/reference-data-api` loads current departments, countries, and currencies for native selects inside Material form fields; empty catalogs stay empty and department IDs stay exact strings. The component uses a bounded server page directly, without client-side filtering or a `MatTableDataSource` holding the whole directory. Salary amounts, IDs, and versions remain exact strings; salary display appends the recorded currency code without numeric conversion.

Search, filters, and sorting use one typed Reactive Form with explicit Apply/Enter submission. Draft values do not alter pagination until applied. Apply or a page-size change resets to page zero; options are 25/50/100. Reset clears search and filters and restores employee-number ascending order while retaining page size. Country never infers currency. Salary sorting requires an explicit currency; clearing currency while salary sort is selected shows a validation error rather than silently changing the sort. Superseded requests and reads on destroyed pages are cancelled. Errors do not terminate later searches/retries, and stale rows are cleared during loading/failure. The UI distinguishes an empty directory, no search matches, an empty out-of-range page, and safe connectivity/access/server errors. API 401 uses the existing login redirect. Query/page state is local and resets on leaving/reloading; URL persistence remains a separate decision. Employee names link to their protected edit forms.

Catalog loading/error/retry is independent of employee reads: lookup failure disables catalog selectors while browsing, search, and ordinary sorting remain usable. Retry filters reloads catalogs without resubmitting the employee query; no app-wide catalog cache is introduced. The server remains authoritative for catalog validation and sort rules.

The native Material table has scoped column headers, a caption, and a focusable horizontal scrolling region on narrow screens. Paginator controls stay mounted during reads. Tests use HttpTestingController for request parameters, exact values, cancellation, state transitions, validation, and recovery. Chrome verification against Spring Boot and an isolated PostgreSQL database seeded with 10,000 employees covers real server pages, keyboard pagination/focus, page-size reset, combined filters, twelve sort selections, numeric salary order within one currency, lookup-error/retry simulation, search/no matches, API session expiry, and 320–1440px layouts. The temporary database is removed afterward; development data is untouched. Browser smoke tooling remains local rather than an application dependency.

## Employee Creation

Add employee opens protected `/employees/new`. The typed Reactive Form reuses current reference data; loading failures offer Retry options and empty/incomplete catalogs block creation. Employee number/name rules mirror the API, including international names and code-point length checks. Salary is a text input: pure string checks cover positive plain decimals, meaningful currency precision, and the exact storage boundary without Number conversion or rounding. Changing currency revalidates the unchanged amount; country remains independent.

Save refreshes CSRF, then sends exactly six string fields in one POST. Duplicate submissions are blocked while pending. Server field/code pairs map to local messages on known controls; raw response details/messages are never rendered. Entries survive validation, conflict, CSRF, and connection failures. No create is automatically retried: an unconfirmed response directs HR to check the directory, because a write may already have committed. Success replaces the form with server-normalized details and moves focus to its confirmation heading. Cancel returns to the directory; drafts remain in memory and are discarded on leaving/reloading or session redirection. Creation and editing share the same form and validators; draft persistence remains a separate decision.

Unit/HTTP tests cover decimal boundaries, currency changes, exact requests, duplicate/conflict behavior, and safe errors. Chrome against isolated seeded PostgreSQL verifies actual creation, lookup retry, duplicate number/CSRF rejection, confirmation and directory retrieval, plus a committed-but-lost response simulation without replay. Three synthetic records are created only in the temporary database and removed during cleanup. No new dependency or backend contract is introduced.

## Employee Editing

Select an employee name in the directory or open protected `/employees/:id/edit`. The shared form waits for the current employee and required catalogs; failed loads cannot submit an empty replacement. Save refreshes CSRF and sends the six editable fields plus the original string version. Server-confirmed results preserve exact salary/currency values. Unchanged submissions are accepted without inventing a new version or timestamp.

A stale/unknown conflict, missing update target, or unconfirmed response preserves the draft and blocks further saves. **Discard draft and load latest** explicitly warns that entries will be replaced; fields and version change only after a successful read. A failed reload preserves the draft for another attempt. Duplicate numbers and ordinary validation/CSRF errors remain correctable without changing the original version. No automatic retry, version replacement, or merge occurs. Leaving/reloading/session redirection discards the draft; no navigation-away guard or persistent draft storage is included.

Unit/HTTP tests cover original versions beyond JavaScript's safe integer range, exact transport, validation, duplicate submissions, stale recovery, failed reads/reloads, no-change saves, route reuse, and safe errors. Real Chrome checks against isolated PostgreSQL exercise two-tab stale edits, explicit recovery, exact salary updates, a committed-but-lost response, CSRF rejection/manual retry, Cancel, session expiry, and narrow layouts. The creation browser workflow is also checked after sharing the form. Only temporary seeded data is changed and removed afterward.

## Compensation Analytics

Protected `/analytics` uses `analytics/compensation-api` and readonly DTOs for the existing authenticated GET contract. The report shows overall matching headcount, one salary summary card per currency, and a department/country table with per-currency headcount, total, average, minimum, and maximum. All monetary values and department keys stay strings, including aggregate totals beyond employee storage limits. Rendering never converts money through Number, pipes that coerce to numbers, or client-side arithmetic; server rounding/ordering remain authoritative. The overall headcount can span currencies; no monetary total does.

A typed Reactive Form submits grouping and name/number search plus independent department/country/currency filters together on Apply/Enter. Shared employee-search validation enforces the same Unicode length/null-character rules as the directory. Reset restores department grouping/all employees. Applied criteria remain visible while draft controls change; Retry report retains the applied query. Requests use switchMap and lifecycle teardown to cancel obsolete reads. Loading/failure clears previous results, empty/no-match states are distinct, and raw server error messages are never rendered. Catalog errors have an independent retry; reports, search, and grouping remain usable. Empty catalogs remain empty. No pagination/sort parameters, employee-record reads for aggregation, CSRF initialization, or write requests are sent by the report service.

Cards reflow on narrow screens, and the table uses a caption, scoped headers, and a focusable horizontal scrolling region. Automated UI/HTTP tests cover exact values, grouping/filter submission, draft/applied state, retries/cancellation, validation, empty data, and safe errors. Real Chrome against isolated PostgreSQL seeded with 10,000 employees compares all department/country report values against an independent SQL/Decimal calculation, checks filter totals against the directory, and exercises lookup/report failures, expiry, and 320–1440px layouts. The seeded employee fingerprint remains unchanged. Browser tooling stays local; application dependencies and backend contracts are unchanged.

Query state is memory-only and resets on leaving/reloading; URL parameters are not restored into controls. Reports refresh through Apply or Reset rather than polling. Charts, export, historical reporting, and currency conversion require separate approval.

## Authentication

`SessionService` uses relative URLs and browser-managed cookies. Angular supplies the CSRF header from `XSRF-TOKEN`; the application never writes credentials, session IDs, or tokens to localStorage/sessionStorage. Login obtains CSRF, sends URL-encoded credentials, refreshes the rotated token, then reads the authenticated username. Passwords are cleared after submission. Invalid credentials, CSRF rejection, and network failures receive separate safe messages.

Protected activation fails closed when a session check fails. Return URLs accept only `/employees`, `/employees/new`, `/employees/{digits}/edit`, or `/analytics` with optional query/fragment values. A non-auth API 401 clears local identity and returns to login without retrying the failed request; 403 does not imply bad credentials. Logout obtains a current token, posts once, clears identity only on success, and refreshes the anonymous token. Failure after successful logout cannot undo it; failed logout itself remains visible and retryable. Server authorization remains authoritative.

## Source Organization

`src/main.ts` bootstraps the root router outlet with providers from `app.config.ts`. `app.routes.ts` places lazy-loaded employee directory/creation/editing, analytics, and not-found pages beneath `layout/app-shell`. The shell owns header/navigation, its content outlet, keyboard focus, and footer. Root `/` redirects to `/employees`; `/analytics` opens compensation analytics. Route titles and `aria-current` identify the active page. The lazy-loaded public `/login` route sits outside the shell. Its typed Reactive Form submits credentials through `auth/session-service`; a child-route guard checks the server session before protected route activation.

Material/CDK 22.2.3 provide navigation controls, login form fields, and buttons with the prebuilt Azure theme. System fonts and CSS wrapping keep both links visible on small screens without external font requests or a menu-toggle state. Global styles hold shared page typography/panels; layout-specific styles stay beside the shell. Keep future component templates, styles, and `.spec.ts` together.

The navigation suite covers default/direct routes, link activation, current-page indicators, stable shell reuse, route titles, skip-link and route focus, preserved initial browser focus, and unknown-route recovery. Authentication tests cover request order, token handling, safe redirects, login validation/errors, session checks, expiry interception, and logout failure/retry. Real Chrome smoke checks against Spring Boot/PostgreSQL cover actual cookies, CSRF rejection/rotation, reload restoration, expiry and logout, plus 320/375/768/1440-pixel layouts. Expected anonymous/invalid-login requests return 401; no employee writes, uncaught page errors, or external requests are generated. Browser smoke tooling is local/ignored, not an application dependency.

The root `.gitignore` owns repository exclusions. Commit source, configuration, and `package-lock.json`; keep `node_modules`, build output, caches, coverage, runtime logs, and local environment files untracked. Browser-delivered configuration must never contain secrets.

See [frontend conventions](../docs/FRONTEND.md) and the [main setup guide](../README.md) for the project decisions and backend setup.

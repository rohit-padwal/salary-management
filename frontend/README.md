# Salary Management Frontend

Angular 22.2.3 application foundation using standalone components, routing, strict TypeScript/templates, zoneless change detection, and typed HttpClient providers. The responsive shell includes the employee directory, an analytics placeholder, active navigation, page titles, a skip link, and a recoverable not-found page. Login, session restoration, protected navigation, and logout are implemented. Employee creation/editing, filter/sort controls, and analytics reporting remain later steps.

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

Open `http://127.0.0.1:4200`. The configured development proxy forwards `/api` and nested API paths to `http://127.0.0.1:8080`, preserving the browser origin for session/CSRF integration. Start the backend separately with the `local` profile and the configured HR account described in the main setup guide. Opening a protected route without a session shows `/login`. Sign in with that configured account; credentials are not supplied by the frontend. The employee directory and authentication use real APIs; the analytics page remains a placeholder. Restart the development server after changing `proxy.conf.json`.

`npm test` runs the CLI-managed Vitest suite once; `npm run test:watch` watches changes. `npm run format` formats frontend files. The production build writes to ignored `dist/salary-management-ui/browser`; packaging those assets with Spring Boot is a later step.

## Employee Directory

`employees/employee-api` performs typed GET requests with `page`, `size`, and optional `q`. The component uses a bounded server page directly, without client-side filtering or a `MatTableDataSource` holding the whole directory. Salary amounts, IDs, and versions remain exact strings; salary display appends the recorded currency code without numeric conversion.

Search uses a typed Reactive Form and explicit button/Enter submission. Search or page-size changes reset to page zero; options are 25/50/100. Superseded requests and reads on destroyed pages are cancelled. Errors do not terminate later searches/retries, and stale rows are cleared during loading/failure. The UI distinguishes an empty directory, no search matches, an empty out-of-range page, and safe connectivity/access/server errors. API 401 uses the existing login redirect. Query/page state is local and resets on leaving/reloading; URL persistence, filter/sort controls, and editing are separate increments.

The native Material table has scoped column headers, a caption, and a focusable horizontal scrolling region on narrow screens. Paginator controls stay mounted during reads. Tests use HttpTestingController for request parameters, exact values, cancellation, state transitions, validation, and recovery. Chrome verification against Spring Boot and an isolated PostgreSQL database seeded with 10,000 employees covers real server pages, keyboard pagination/focus, page-size reset, search, no matches, retry, API session expiry, and 320–1440px layouts. The temporary database is removed afterward; development data is untouched. Browser smoke tooling remains local rather than an application dependency.

## Authentication

`SessionService` uses relative URLs and browser-managed cookies. Angular supplies the CSRF header from `XSRF-TOKEN`; the application never writes credentials, session IDs, or tokens to localStorage/sessionStorage. Login obtains CSRF, sends URL-encoded credentials, refreshes the rotated token, then reads the authenticated username. Passwords are cleared after submission. Invalid credentials, CSRF rejection, and network failures receive separate safe messages.

Protected activation fails closed when a session check fails. Return URLs accept only `/employees` or `/analytics` with optional query/fragment values. A non-auth API 401 clears local identity and returns to login without retrying the failed request; 403 does not imply bad credentials. Logout obtains a current token, posts once, clears identity only on success, and refreshes the anonymous token. Failure after successful logout cannot undo it; failed logout itself remains visible and retryable. Server authorization remains authoritative.

## Source Organization

`src/main.ts` bootstraps the root router outlet with providers from `app.config.ts`. `app.routes.ts` places lazy-loaded employee, analytics, and not-found pages beneath `layout/app-shell`. The shell owns header/navigation, its content outlet, keyboard focus, and footer. Root `/` redirects to `/employees`; `/analytics` opens compensation analytics. Route titles and `aria-current` identify the active page. The lazy-loaded public `/login` route sits outside the shell. Its typed Reactive Form submits credentials through `auth/session-service`; a child-route guard checks the server session before protected route activation.

Material/CDK 22.2.3 provide navigation controls, login form fields, and buttons with the prebuilt Azure theme. System fonts and CSS wrapping keep both links visible on small screens without external font requests or a menu-toggle state. Global styles hold shared page typography/panels; layout-specific styles stay beside the shell. Keep future component templates, styles, and `.spec.ts` together.

The navigation suite covers default/direct routes, link activation, current-page indicators, stable shell reuse, route titles, skip-link and route focus, preserved initial browser focus, and unknown-route recovery. Authentication tests cover request order, token handling, safe redirects, login validation/errors, session checks, expiry interception, and logout failure/retry. Real Chrome smoke checks against Spring Boot/PostgreSQL cover actual cookies, CSRF rejection/rotation, reload restoration, expiry and logout, plus 320/375/768/1440-pixel layouts. Expected anonymous/invalid-login requests return 401; no employee writes, uncaught page errors, or external requests are generated. Browser smoke tooling is local/ignored, not an application dependency.

The root `.gitignore` owns repository exclusions. Commit source, configuration, and `package-lock.json`; keep `node_modules`, build output, caches, coverage, runtime logs, and local environment files untracked. Browser-delivered configuration must never contain secrets.

See [frontend conventions](../docs/FRONTEND.md) and the [main setup guide](../README.md) for the project decisions and backend setup.

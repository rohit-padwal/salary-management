# Frontend Tooling and Conventions

**Status:** The user explicitly selected the latest stable modern Angular, not AngularJS. The Angular workspace under `frontend/` has a responsive application shell, a server-paginated/searchable employee directory, and an analytics placeholder. Login/session handling and local browser integration with the authentication API are implemented. Employee editing, filter/sort controls, analytics reporting, and deployment remain later steps. Incubyte's confirmation remains a separate pending clarification.

## Verified Stable Baseline

Checked on **2026-10-18** against published package metadata and official documentation:

| Tool | Baseline |
| --- | --- |
| Angular framework, CLI, build tooling | 22.2.3, stable releases |
| Angular Material and CDK | 22.2.3, installed for navigation, login controls, and the prebuilt theme |
| Node.js | 24.22.0 LTS, recorded in `frontend/.nvmrc`; project engine range `^24.15.0` |
| npm | 11.19.1, recorded in package metadata |
| TypeScript | 6.0.x (`>=6.0 <6.1`) |
| RxJS | Compatible stable 7.x within Angular's `^7.4.0` range |
| Unit testing | CLI-managed Vitest and DOM test environment |

The machine's global Node remains 25.9.0, outside Angular 22.2.3's supported engine ranges. An official SHA-256-verified Node 24.22.0 distribution is available in ignored `.tools/` for this working copy, selected per command or shell; global tooling is unchanged. `frontend/.npmrc` enforces engines, saves new direct dependencies exactly, and puts npm cache files in ignored `.tools/npm-cache`. Angular framework/CLI/build packages are pinned to 22.2.3, while `package-lock.json` fixes the complete resolved dependency graph. Use `npm ci` for reproducible setup and local npm scripts rather than a global CLI. Future upgrades must recheck stable tags and compatibility; preview releases remain excluded.

Sources: published [Angular framework](https://registry.npmjs.org/@angular/core/latest), [CLI](https://registry.npmjs.org/@angular/cli/latest), [build](https://registry.npmjs.org/@angular/build/latest), and [Material](https://registry.npmjs.org/@angular/material/latest) metadata; Angular's [compatibility guidance](https://angular.dev/reference/versions); Node's [LTS release list](https://nodejs.org/en/about/previous-releases). These links can change after the verification date.

## Implementation Conventions

- Use standalone components and application providers, strict TypeScript/templates, and the standard CLI application builder. Preserve the current zoneless default; no ZoneJS dependency is planned. See [components](https://angular.dev/essentials/components) and [zoneless guidance](https://angular.dev/guide/zoneless).
- Organize frontend source by feature, such as employees, analytics, and authentication. Keep a component's template, styles, and `.spec.ts` alongside its TypeScript. Use descriptive hyphenated filenames and `inject()` for dependency injection. The frontend follows Angular's [style guide](https://angular.dev/style-guide), rather than copying backend layer folders into a global components/services hierarchy.
- Keep components focused on presentation. Use signals/computed values for local view state, built-in `@if`/`@for` control flow, and stable employee IDs for list tracking. Avoid manual DOM manipulation and needless state-management dependencies. See [template control flow](https://angular.dev/guide/templates/control-flow).
- Retain the approved typed Reactive Forms approach for employee editing and typed HttpClient services for API access. Keep monetary values and IDs as strings across the API boundary. Do not introduce Signal Forms or another form abstraction without a concrete reason and an approved change.
- Preserve same-origin session/CSRF integration and explicit loading, empty, validation, error, and stale-edit states. Server validation and authorization remain authoritative. Use Material for needed controls, not as a reason to add speculative UI features.
- Use the CLI's supported [Vitest setup](https://angular.dev/guide/testing), testing user-visible behavior and HTTP/form integration. Run noninteractive tests and a production build before completing each frontend step. The navigation suite mounts the real root outlet before routing and verifies shell behavior, page titles, active links, skip-link/route focus, initial browser focus, and recovery. Real browser checks cover desktop/narrow layouts and keyboard use; future screens will add data-workflow tests. See [workspace commands](../frontend/README.md).

Initialization, layout, and navigation are complete. Employee editing, filter/sort controls, analytics reporting, and production asset packaging remain separate approved increments. The local `/api/**` proxy connects the login/session UI to the existing backend; deployed integration remains later work.

## Layout and Extension Points

The root delegates to the router. `layout/app-shell` is the parent route for lazy-loaded employee, analytics, and not-found pages, so navigation and focus behavior remain stable as feature content changes. Both navigation links stay visible and wrap below the brand on narrow screens. Native anchors retain expected keyboard/link behavior; `aria-current`, an active underline, and route titles identify the current view. Initial navigation preserves native focus; later SPA navigation moves focus to main content after rendering. The subscription is disposed with the shell. Native focus calls are limited to this accessibility behavior.

Material uses the local prebuilt Azure theme, CSS/system fonts, and no extra animation/icon-font dependency. The employee directory reads server pages; analytics remains a placeholder. The public login route sits outside the protected shell, which displays the username returned by the server. Authentication is confined to `auth/`: a typed Reactive Form, session service, child-route guard, safe return-URL rule, and functional expiry interceptor. Further employee workflows and analytics can be added within their feature directories without duplicating the layout. No generic page framework or global state library is introduced.

## Session Integration

Use the existing cookie session and Angular’s default same-origin XSRF support. Obtain CSRF before login/logout and refresh after successful authentication changes; read identity from `/auth/session`, never infer it from submitted credentials. Only a session-check 401 means anonymous; availability failures remain visible and protected activation fails closed. Return URLs allow known feature roots only. Non-auth API 401 redirects to login and propagates the original failure without retry; 403 remains a distinct error. The application never writes credentials or tokens to localStorage/sessionStorage.

The guard rechecks the server during protected route activation, accepting a small extra request rather than trusting cached identity. Server authorization remains required for every API call. Logout failures do not claim success; token-refresh failure after completed logout does not restore authentication. Existing form fields/buttons use Material’s public system typography tokens to retain local fonts. Future employee services reuse HttpClient and its existing CSRF/expiry handling; RBAC, SSO, and production same-origin packaging require separate decisions.

## Directory Read Flow

Use `EmployeeApi` with explicit page/size/search parameters and the backend’s stable default ordering. A page-sized array feeds the native Material table directly; pagination and matching remain on the database. IDs, versions, and salary values stay strings, including values beyond JavaScript’s safe integer range. Salary display preserves the server decimal scale and records its currency separately.

Explicit search submission avoids per-keystroke requests. The typed form checks the 200-code-point limit; the server remains authoritative. A request stream uses switchMap and toSignal teardown to cancel obsolete/destroyed reads. Catch errors inside each request so retries and later searches continue. Clear stale rows during reads/failures, offer a first-page recovery for an empty out-of-range page, and retain paginator controls during reads. Search and page-size changes reset to page zero. Local query state keeps this increment small; URL persistence, filters, sorting controls, and editing require separate approval.

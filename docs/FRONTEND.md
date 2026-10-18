# Frontend Tooling and Conventions

**Status:** The user explicitly selected the latest stable modern Angular, not AngularJS. The minimal Angular workspace is initialized under `frontend/`, with installed/locked dependencies and a root application heading. Feature screens and backend browser workflows remain later steps. Incubyte's confirmation remains a separate pending clarification.

## Verified Stable Baseline

Checked on **2026-10-18** against published package metadata and official documentation:

| Tool | Baseline |
| --- | --- |
| Angular framework, CLI, build tooling | 22.2.3, stable releases |
| Angular Material and CDK | 22.2.3, when UI components need them |
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
- Use the CLI's supported [Vitest setup](https://angular.dev/guide/testing), testing user-visible behavior and HTTP/form integration. Run noninteractive tests and a production build before completing each frontend step. The bootstrap suite verifies the real root component with application providers; future screens will add behavior-focused tests. See [workspace commands](../frontend/README.md).

Initialization is complete. Layout, employee/login screens, analytics screens, and production asset packaging remain separate approved increments. The local `/api/**` proxy is configured for the existing backend; this does not implement a login/session UI or establish deployed integration.

# Frontend Tooling and Conventions

**Status:** The user explicitly selected the latest stable modern Angular, not AngularJS. This is the implementation baseline before frontend initialization; no frontend workspace or dependencies are installed yet. Incubyte's confirmation remains a separate pending clarification.

## Verified Stable Baseline

Checked on **2026-10-18** against published package metadata and official documentation:

| Tool | Baseline |
| --- | --- |
| Angular framework, CLI, build tooling | 22.2.3, stable releases |
| Angular Material and CDK | 22.2.3, when UI components need them |
| Node.js | Recommend 24.22.0 LTS; Angular's Node 24 range is `^24.15.0` |
| TypeScript | 6.0.x (`>=6.0 <6.1`) |
| RxJS | Compatible stable 7.x within Angular's `^7.4.0` range |
| Unit testing | CLI-managed Vitest and DOM test environment |

The installed Node on this development machine was 25.9.0 when checked, outside Angular 22.2.3's supported engine ranges. Select compatible Node during frontend setup; this refactor does not alter global tooling. Recheck the stable package tags when initializing, keep framework/CLI/build packages aligned, and commit exact resolved dependencies in `package-lock.json`. Avoid `next`/RC releases and unpinned global CLI requirements.

Sources: published [Angular framework](https://registry.npmjs.org/@angular/core/latest), [CLI](https://registry.npmjs.org/@angular/cli/latest), [build](https://registry.npmjs.org/@angular/build/latest), and [Material](https://registry.npmjs.org/@angular/material/latest) metadata; Angular's [compatibility guidance](https://angular.dev/reference/versions); Node's [LTS release list](https://nodejs.org/en/about/previous-releases). These links can change after the verification date.

## Implementation Conventions

- Use standalone components and application providers, strict TypeScript/templates, and the standard CLI application builder. Preserve the current zoneless default; no ZoneJS dependency is planned. See [components](https://angular.dev/essentials/components) and [zoneless guidance](https://angular.dev/guide/zoneless).
- Organize frontend source by feature, such as employees, analytics, and authentication. Keep a component's template, styles, and `.spec.ts` alongside its TypeScript. Use descriptive hyphenated filenames and `inject()` for dependency injection. The frontend follows Angular's [style guide](https://angular.dev/style-guide), rather than copying backend layer folders into a global components/services hierarchy.
- Keep components focused on presentation. Use signals/computed values for local view state, built-in `@if`/`@for` control flow, and stable employee IDs for list tracking. Avoid manual DOM manipulation and needless state-management dependencies. See [template control flow](https://angular.dev/guide/templates/control-flow).
- Retain the approved typed Reactive Forms approach for employee editing and typed HttpClient services for API access. Keep monetary values and IDs as strings across the API boundary. Do not introduce Signal Forms or another form abstraction without a concrete reason and an approved change.
- Preserve same-origin session/CSRF integration and explicit loading, empty, validation, error, and stale-edit states. Server validation and authorization remain authoritative. Use Material for needed controls, not as a reason to add speculative UI features.
- Use the CLI's supported [Vitest setup](https://angular.dev/guide/testing), testing user-visible behavior and HTTP/form integration. Run noninteractive tests and a production build before completing each frontend step. No frontend tests or builds have run yet because initialization is pending.

Initialize only after approval of that step. Layout, employee screens, analytics screens, and production asset packaging remain separate approved increments.

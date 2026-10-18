# Salary Management Frontend

Angular 22.2.3 application foundation using standalone components, routing, strict TypeScript/templates, zoneless change detection, and typed HttpClient providers. The responsive shell includes employee/analytics placeholder routes, active navigation, page titles, a skip link, and a recoverable not-found page. Employee data workflows, authentication UI, and analytics reporting remain later steps.

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

Open `http://127.0.0.1:4200`. The configured development proxy forwards `/api` and nested API paths to `http://127.0.0.1:8080`, preserving the browser origin for the planned session/CSRF integration. Start the backend separately for real API requests. The current placeholder pages make no API requests; login/session handling must precede connecting protected data workflows. Restart the development server after changing `proxy.conf.json`.

`npm test` runs the CLI-managed Vitest suite once; `npm run test:watch` watches changes. `npm run format` formats frontend files. The production build writes to ignored `dist/salary-management-ui/browser`; packaging those assets with Spring Boot is a later step.

## Source Organization

`src/main.ts` bootstraps the root router outlet with providers from `app.config.ts`. `app.routes.ts` places lazy-loaded employee, analytics, and not-found pages beneath `layout/app-shell`. The shell owns header/navigation, its content outlet, keyboard focus, and footer. Root `/` redirects to `/employees`; `/analytics` opens compensation analytics. Route titles and `aria-current` identify the active page. Future login routes can sit outside the shell; no guards/session UI are implemented yet.

Material/CDK 22.2.3 provide native-anchor navigation controls with the prebuilt Azure theme. System fonts and CSS wrapping keep both links visible on small screens without external font requests or a menu-toggle state. Global styles hold shared page typography/panels; layout-specific styles stay beside the shell. Keep future component templates, styles, and `.spec.ts` together.

The navigation suite covers default/direct routes, link activation, current-page indicators, stable shell reuse, route titles, skip-link and route focus, preserved initial browser focus, and unknown-route recovery. Real Chrome smoke checks additionally cover keyboard navigation, history, direct loads, 320/375/768/1440-pixel layouts, 44-pixel navigation targets, and absence of console errors/API/external requests. Browser smoke tooling is local/ignored, not an application dependency.

The root `.gitignore` owns repository exclusions. Commit source, configuration, and `package-lock.json`; keep `node_modules`, build output, caches, coverage, runtime logs, and local environment files untracked. Browser-delivered configuration must never contain secrets.

See [frontend conventions](../docs/FRONTEND.md) and the [main setup guide](../README.md) for the project decisions and backend setup.

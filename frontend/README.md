# Salary Management Frontend

Angular 22.2.3 application foundation using standalone components, routing, strict TypeScript/templates, zoneless change detection, and typed HttpClient providers. Employee screens, authentication UI, and analytics screens are later steps.

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

Open `http://127.0.0.1:4200`. The configured development proxy forwards `/api` and nested API paths to `http://127.0.0.1:8080`, preserving the browser origin for the planned session/CSRF integration. Start the backend separately for real API requests. The initial page makes no API requests. Restart the development server after changing `proxy.conf.json`.

`npm test` runs the CLI-managed Vitest suite once; `npm run test:watch` watches changes. `npm run format` formats frontend files. The production build writes to ignored `dist/salary-management-ui/browser`; packaging those assets with Spring Boot is a later step.

## Source Organization

`src/main.ts` bootstraps `src/app/app.ts` with application providers from `app.config.ts`. `app.routes.ts` holds routes; feature routes/components will be added incrementally. Keep a component's template, styles, and `.spec.ts` together. The root currently renders only the application heading and router outlet.

The root `.gitignore` owns repository exclusions. Commit source, configuration, and `package-lock.json`; keep `node_modules`, build output, caches, coverage, runtime logs, and local environment files untracked. Browser-delivered configuration must never contain secrets.

See [frontend conventions](../docs/FRONTEND.md) and the [main setup guide](../README.md) for the project decisions and backend setup.

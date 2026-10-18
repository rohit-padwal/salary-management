# Frontend Development

Angular/Material **22.2.3**, TypeScript **6.0.x**, standalone components, strict templates, zoneless change detection, and Vitest. Complete Docker/manual setup and demo credentials are in the [root README](../README.md).

## Tooling and Commands

Use **Node 24.22.0** (`.nvmrc`) and **npm 11.19.1**. With an existing nvm installation, run `nvm install` and `nvm use` here; otherwise select a compatible installed Node. No global Angular CLI is needed. Engine checks reject unsupported runtimes.

From `frontend/`:

```sh
node --version
npm --version
npm ci
npm test
npm run build
npm run format:check
npm start
```

`npm ci` uses the committed lockfile. `npm run test:watch` watches tests; `npm run format` applies formatting. npm caches are local/ignored under `../.tools/npm-cache`.

## Runtime

Development opens **http://127.0.0.1:4200** and proxies `/api` to **http://127.0.0.1:8080**. Start the configured backend separately with its `local` profile; restart the Angular server after changing `proxy.conf.json`.

Routes: `/login`, `/employees`, `/employees/new`, `/employees/:id/edit`, `/analytics`; unknown UI paths show a recovery page. Protected routes check the server session. Accounts are configured in the backend, never bundled into Angular.

`npm run build` writes `dist/salary-management-ui/browser`. The Docker frontend serves this production output through Nginx with same-origin API proxying and client-route fallback. No secrets belong in browser-delivered configuration.

See [frontend conventions](../docs/FRONTEND.md) for exact money/text handling, forms, stale-draft recovery, request cancellation, accessibility, and extension boundaries; [verification](../docs/VERIFICATION.md) records actual tests/browser checks. Root `.gitignore` excludes dependencies, generated output, caches, reports, and private environment files while retaining the lockfile/source.

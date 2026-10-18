# Verification Evidence

This file preserves dated runs; counts below belong to their stated baselines. The reviewer Docker/setup verification is recorded at the end.

## Historical Step 22 Integration Run

Verified on **2026-10-19 (Asia/Kolkata)** for Step 22, using the application baseline at `ea041f7` plus the cross-endpoint regression test added in this step. No production-code, dependency, schema, or API changes were required.

## Automated Results

| Check | Actual result |
| --- | --- |
| `./mvnw verify` from `backend/`, with Java 21 selected | Build success; 156 unit tests and 441 integration tests; zero failures, errors, or skips |
| `npm test` from `frontend/`, with Node 24 selected | 184 tests across 16 files passed |
| `npm run build` from `frontend/` | Production build passed; initial bundle 393.27 kB, below the existing 500 kB warning budget |
| `npm run format:check` from `frontend/` | All matched files passed |
| `git diff --check` | Passed |

Toolchain: Temurin Java 21.0.10, Maven Wrapper 3.9.11, Node 24.22.0, npm 11.19.1, Angular 22.2.3, and PostgreSQL 18.6. Docker supplied isolated test databases. Select compatible Java/Node as described in the [root setup](../README.md) and [frontend setup](../frontend/README.md).

`CompensationAnalyticsApiIT.employeeWritesUpdateDirectoryAndReportsWhileRejectedEditsPreserveBoth` now connects authenticated employee POST/PUT, directory GET, and analytics GET in one repeatable HTTP test. It creates employees through the API, verifies exact totals and HALF_EVEN averages, moves an employee between department/country/currency groups, and compares the updated employee with the directory response. Both grouping dimensions reflect the move. Rejected stale and invalid updates preserve the employee, listing, and reports. The existing suite supplies real sessions/CSRF, Spring Boot, Flyway, and disposable PostgreSQL; no new test framework or fixtures in the development database are needed.

Run `./mvnw verify` to repeat this regression with the rest of the backend suite. The test covers existing behavior; no production-code fix was needed.

## Connected Browser Workflow

Real headless Chrome exercised Angular through its same-origin development proxy against the executable Spring Boot JAR and a temporary PostgreSQL database seeded with exactly 10,000 employees. Temporary account/database credentials were generated at runtime. The browser automation was an ad hoc local check, not a committed browser suite or part of `npm test`.

| Workflow | Observed result |
| --- | --- |
| Anonymous creation deep link → login → form | Returned to `/employees/new` with supported options loaded |
| Create two Engineering/India employees in USD | Exact salaries `1000.00` and `1000.01`; directory returned both normalized records |
| Search the two records in analytics | Headcount 2, USD total `2000.01`, average `1000.00`, minimum `1000.00`, maximum `1000.01` |
| Edit one employee to Finance/Japan, salary `100000` JPY | One accepted update; original ID/creation timestamp retained and version advanced from `0` to `1` |
| Save an older form in a second tab | `409`; draft retained and Save blocked; explicit discard/reload loaded the current record |
| Submit excess JPY precision, then Cancel | Validation blocked the request; persisted record and version stayed unchanged |
| Reopen directory and both analytics groupings | Directory matched the saved employee; summaries separated JPY `100000` from the remaining USD `1000.01`; department/country groups reflected the move |
| Apply the moved employee's old and new filters | Old organization/currency combination matched zero; new combination matched one |
| Expire session during an analytics read → sign in | `401` redirected to login with `/analytics` return URL; report controls reset on return as documented |
| Sign out → read protected analytics | Login shown; subsequent API read returned `401` |

The browser sent exactly two employee POSTs and two PUTs, of which one PUT was stale. Database verification found **10,002 employees, two workflow records, and a total employee version sum of 1**. There were no uncaught browser errors or external requests. Temporary browser/application/database processes were removed; the persistent development database was not targeted.

## Manual Reproduction

Use a disposable database, the existing seed command, the configured HR account, and the local backend/frontend setup. Choose a unique employee-number prefix and filter analytics by it to exclude seeded employees.

1. Open Add employee while signed out; sign in and create two employees with the USD values above, using Engineering and India.
2. Search their shared prefix in the directory and analytics. Check the two-record totals and rounded average.
3. Open the first record for editing in two tabs. Save Finance/Japan/JPY `100000` in one; try saving the older form in the other, then use the explicit discard/reload action.
4. Check directory values, department and country reports, separate currencies, and the moved employee's old/new filter combinations.
5. Enter `100000.001` for JPY and verify local validation blocks Save; Cancel and confirm the persisted values remain unchanged.
6. Remove the authentication session cookie in browser developer tools and request a report. Confirm login return behavior; sign out after signing back in and check protected access again.

## Scope and Remaining Delivery Work

At Step 22, this verified local integration with the development proxy; the production build was compiled separately. The later Docker/setup run below verifies Nginx production asset serving and client-route fallback. Public hosting, deployed HTTPS/cookie behavior, and production workflow verification remain pending. These checks are not a load benchmark or a complete security audit.

The committed HTTP workflow test provides a regression check for future employee/report changes. A permanent browser runner or CI workflow would be a separate tooling decision. Existing memory-only drafts and local report/directory query state remain unchanged.

## Step 23 Follow-Up

The [coverage and edge-case review](COVERAGE_REVIEW.md) identified and fixed a client/API Unicode-whitespace mismatch. Its separately scoped results are 194 frontend tests, 161 backend unit tests, and 64 selected analytics API integration tests, plus build/format checks and real-browser literal-search verification. Other integration suites were not rerun in that step. The Step 22 full-suite results above remain the record of that earlier run.


## Earlier Feature Browser Evidence

These observations were previously recorded in `frontend/README.md` and are retained here as historical evidence, not additional reruns in the Docker task. Each used isolated seeded PostgreSQL and local Chrome tooling; temporary services/data were removed.

| Feature check | Previously observed behavior |
| --- | --- |
| Shell/authentication | 320/375/768/1440px layouts, keyboard/navigation focus, login/cookie/CSRF rotation, reload restoration, expiry/logout; no employee writes, external requests, or uncaught page errors. |
| Directory | Real server pagination, keyboard focus, page-size reset, combined filters, all twelve sort selections, numeric salary order within a currency, lookup retry, no-match search, expiry, and 320–1440px layouts. |
| Creation | Exact saved values/confirmation, lookup retry, duplicate and CSRF rejection, directory retrieval, and a committed-but-lost response without automatic replay; three records created only in the temporary database. |
| Editing | Two-tab stale conflict/recovery, exact updates, committed-but-lost response, CSRF/manual retry, Cancel, expiry, narrow layouts, and creation regression after form sharing. |
| Analytics | Both grouping dimensions and every reported monetary value compared with independent SQL/Decimal expectations; directory/filter parity, report/catalog failures, expiry, 320–1440px layouts; employee fingerprint unchanged. |

## Reviewer Documentation and Docker Setup — 2026-10-19

Baseline `4644f13` plus setup implementation `053cb6c`; macOS ARM64, Java 21, Node 24.22.0/npm 11.19.1, Docker 29.1.2/Compose v2.40.3. The approved requirements remain unchanged. This run adds opt-in demo initialization/readiness and container packaging; unrelated business behavior is preserved.

| Executed check | Actual result |
| --- | --- |
| Initial new backend test run | Expected compilation failure before the new readiness/seed-if-empty implementation existed |
| `./mvnw verify` | Build success: **164 unit + 449 integration tests**, zero failures/errors/skips |
| `./mvnw -DskipTests package` | Executable JAR build passed; tests deliberately skipped for this command |
| `npm ci` | Clean lockfile install passed; npm reported install-script approval warnings; no approvals/dependency changes made |
| `npm test` | **194 tests / 17 files passed** |
| `npm run build` | Production build passed within configured bundle budget |
| `npm run format:check` | Passed after documentation edits |
| `docker compose config --quiet` | Passed |
| `docker compose build` | Backend/frontend production images built; initial wrapper checksum failure fixed by installing unzip so the pinned ZIP is used |
| `docker compose up --build -d --wait` | All three services healthy; Flyway applied two migrations; exactly **10,000 employees** before browser writes |
| Direct/proxied health and asset requests | Backend and frontend `/api/v1/health`: 200 UP; employee/new/edit and analytics deep links: 200 HTML; missing JS: 404; protected API path ending `.js`: 401 problem JSON rather than SPA HTML |
| Chrome → Nginx → Spring Boot → PostgreSQL | Full connected workflow below passed against production Angular assets |
| `docker compose down` then `docker compose up -d --wait` | All employee fields preserved byte-for-byte by ordered-row fingerprint; demo runner inserted zero |
| `docker compose down --volumes` then rebuilt startup | Only the demo volume reset; exactly **10,000 employees**, no workflow additions, version sum zero |
| Local Markdown links/anchors and `git diff --check` | Passed; approved requirements and historical evidence preserved |

The Docker browser run reused the Step 22 workflow against port 4200 with public demo credentials: login/deep-link return, two creations, exact directory/analytics values and HALF_EVEN rounding, department/country/currency editing, two-tab stale rejection and explicit recovery, precision rejection without a write, session expiry/login return, and logout. It produced **10,002 employees, two workflow records, version sum 1** before restart/reset. There were no uncaught browser errors or external requests. The final fresh demo also passed read-only Chrome login, 25-of-10,000 directory rendering, reload/session restoration, and analytics. The original development database/volume was not targeted.

Manual-path verification used `.env.example`, its actual role/database creation SQL, `scripts/seed-employees.sh`, the executable Java JAR with `local`, and `npm start` with a temporary API proxy. Seed loaded 10,000 and a second strict invocation exited nonzero without changing the count. Real Chrome verified demo login, directory, reload, and analytics. Temporary processes/database were removed. Alternate ephemeral ports avoided existing services.

**Limits:** native `psql`/`createdb`/PostgreSQL daemon binaries are absent on this host, so a disposable PostgreSQL container supplied the database and SQL clients; OS-specific native service startup was not verified. Docker was tested on ARM64, not Windows/x86. Browser scripts remain ad hoc local checks. Public hosting, TLS/proxy security, workload benchmarks, and Step 24 security/performance/code-quality review remain pending. The Docker configuration is a loopback demo with public credentials and synthetic data, not a production deployment.

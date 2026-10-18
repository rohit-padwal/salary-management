# Employee Salary Management

Employee salary management for an HR Manager overseeing 10,000 employees across multiple countries, built for the Incubyte engineering assessment.

## Current Status

The backend connects to PostgreSQL through JPA and applies its schema/reference data through Flyway. Authenticated employee creation, retrieval, and full editing are available through JSON APIs, with stale-update protection, session authentication, and Angular-compatible CSRF protection. Authenticated reference-data lookup and server-side paginated employee search with department/country/currency filters and allowlisted sorting are also available. Compensation analytics are available through an authenticated JSON endpoint. An explicit seed command loads 10,000 deterministic synthetic employees. The Angular 22.2.3 frontend has a responsive shell, accessible navigation, and routed employee/analytics placeholders. Login, employee data management, and analytics reporting UI remain planned.

## Project Organization

Backend packages remain grouped by feature under `backend/src/main/java/com/acme/salarymanagement/`:

| Feature | Responsibility folders |
| --- | --- |
| `employee` | `controller`, `dto`, `entity`, `exception`, `model`, `repository`, `seed`, `service`, `validation` |
| `referencedata` | `controller`, `dto`, `entity`, `model`, `repository`, `service` |
| `analytics` | `controller`, `dto`, `model`, `repository`, `service` |
| `api` | `config`, `exception`, `validation` |
| `security` | `config`, `controller`, `exception`, `filter` |
| `infrastructure` | `persistence` |

`compensation/SalaryRules.java` and shared `validation/` remain small focused units. Tests mirror feature/responsibility packages under `backend/src/test/java`. Migrations remain in `backend/src/main/resources/db/migration`; operational scripts remain in `scripts/`; design and setup notes remain in `docs/`.

## Frontend Baseline

The frontend in `frontend/` uses modern Angular/CLI/build tooling **22.2.3**, verified as the latest stable release on 2026-10-18, with Node **24.22.0**, npm **11.19.1**, TypeScript 6.0.x, and a committed dependency lockfile. Material/CDK 22.2.3 supply navigation controls and the local prebuilt theme. See [frontend setup](frontend/README.md) and [frontend conventions](docs/FRONTEND.md).

With compatible Node selected, run from `frontend/`:

```sh
npm ci
npm test
npm run format:check
npm run build
npm start
```

The development server binds to `127.0.0.1:4200` and proxies `/api/**` to the backend at `127.0.0.1:8080`. Production output is `frontend/dist/salary-management-ui/browser`; packaging it with Spring Boot remains a delivery step. On this Mac, an ignored, checksum-verified Node distribution lives in `.tools/node-v24.22.0-darwin-arm64`; the frontend README explains selecting it for the current shell without changing the global Node default.

## Application Shell

The frontend opens `/employees` by default and provides a second `/analytics` route. Both currently display explicit placeholders without requesting employee/salary data. Unknown UI URLs show a not-found page with a link back. A shared header, visible navigation, main content region, and footer adapt to narrow screens; route changes update the page title and active-link indicator. A skip link and routed-content focus support keyboard use. Login/session handling is the next prerequisite before connecting protected data workflows.

## Git Exclusions

The root `.gitignore` excludes Node dependencies, Angular/Maven build output, caches, coverage and browser-test reports, local tooling, IDE settings, environment files, logs, and Java runtime diagnostics. Source/configuration, `frontend/package-lock.json`, Maven Wrapper files, and Flyway migrations remain trackable. Sanitized `.env.example` templates may be committed; actual `.env` and `.env.*` files stay ignored. Installed dependency directories, local credentials, and generated output stay untracked.

## Backend Toolchain

- Java 21
- Spring Boot 4.1.1 with Spring MVC
- Maven 3.9.11, pinned by the included Maven Wrapper
- PostgreSQL 18.6, supplied locally through Docker Compose
- Flyway for versioned migrations
- Spring Data JPA for persistence, with Hibernate schema validation
- Spring Security for single-account session authentication and CSRF protection
- Jakarta Bean Validation for required HTTP request fields
- Spring Boot's managed test starter, including JUnit Jupiter 6 and Mockito
- Testcontainers for isolated PostgreSQL integration tests

Business validation uses independently testable Java rules. Jakarta Bean Validation checks required HTTP fields; dedicated DTOs keep the JSON contract separate from application and persistence types.

## Build

Install JDK 21 and set `JAVA_HOME` to it. Start Docker Desktop before integration tests or local database setup. Maven Wrapper downloads Maven/dependencies on first use; Testcontainers downloads its PostgreSQL and cleanup images. A global Maven or PostgreSQL installation is not required. The Windows wrapper uses PowerShell.

On macOS, select an installed Java 21 JDK for the current shell:

```sh
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
```

On other platforms, configure `JAVA_HOME` for your JDK 21 installation. Check the Java version reported by Maven, since it can differ from `java -version`.

From the repository root:

```sh
cd backend
./mvnw -version
./mvnw verify
```

Confirm that the wrapper reports Maven 3.9.11 and Java 21. On Windows, use `mvnw.cmd` instead of `./mvnw`.

`./mvnw test` runs validation, service error-classification, and security-configuration tests without Docker. `./mvnw verify` also runs database and HTTP integration tests through Maven Failsafe. Those tests require Docker but do not require the development database or its credentials. Test credentials are synthetic and do not configure a usable application account.

## Configure the Local Database

Local credentials are kept in an ignored root `.env`; initial setup created this file privately on the development machine. For a new checkout, create a private `.env` using shell-compatible `KEY=value` assignments:

| Variable | Value |
| --- | --- |
| `DB_PORT` | Local PostgreSQL port, normally `5432` |
| `DB_URL` | `jdbc:postgresql://127.0.0.1:5432/salary_management`; match `DB_PORT` if changed |
| `DB_USERNAME` | Your chosen local database username |
| `DB_PASSWORD` | Your local database password |

Keep credentials out of Git and restrict access to the file (`chmod 600 .env` on macOS/Linux). Docker Compose reads `.env` automatically; Java receives these values through the environment.

From the repository root:

```sh
docker compose up -d --wait db
docker compose ps
```

The database binds to loopback and stores data in a named volume at the PostgreSQL 18 image's supported volume location. Existing data is preserved across container restarts. Changing `.env` does not automatically change credentials in an existing database volume.

## Seed 10,000 Synthetic Employees

Build the backend with `./mvnw verify` from `backend/`, start PostgreSQL, then run from the repository root on macOS/Linux:

```sh
set -a
. ./.env
set +a
./scripts/seed-employees.sh
```

The script requires `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` in the environment and uses `JAVA_HOME` when set. It targets that database, applies/validates Flyway migrations, and loads exactly 10,000 employees in one transaction. It prints `Seeded 10000 synthetic employees.` on success and exits; it starts no HTTP server and needs no HR login account. Normal application startup never seeds data.

The equivalent command is `java -jar backend/target/salary-management-0.0.1-SNAPSHOT.jar --seed-employees`, with Java 21 and the same environment variables. This also works from PowerShell after setting those variables. Use the seed flag alone; other command-line arguments are rejected in seed mode. Database settings stay out of command-line arguments and source control.

The employee table must be empty. A rerun or any existing employee causes a nonzero exit without inserting, updating, or deleting employee records. The command does not reset the database or overwrite HR edits. To reproduce the dataset, point it at a separate empty database with the supported reference catalogs. A late insert failure rolls back the whole load; concurrent seed attempts are serialized. The load briefly blocks employee writes while ordinary reads can continue.

Dataset version 1 uses fixed random seed `20261018`, `SYN-000001` through `SYN-010000`, curated international name combinations, all eight departments, and all six countries/currencies. Salary bands are illustrative gross annual amounts in each country's usual currency, not market benchmarks; the application still supports country and currency independently. Country counts are 1,667 each for IN/US/GB/DE and 1,666 each for JP/KW. Department IDs and currency precision come from current catalogs; names/codes required by the generator must exist. Shared employee and salary rules validate all records before insertion.

Reproducibility covers employee numbers and business fields for the same generator/catalog metadata on Java 21. Database-generated IDs/timestamps are intentionally excluded; sequences can advance even after rolled-back inserts. Loading and listing/analytics checks at 10,000 records are functional verification, not a performance benchmark.

## Configure HR Access

Web startup requires an explicitly configured HR username and raw BCrypt password hash. There are no default credentials or generated application passwords. Add these shell-compatible assignments to your private root `.env`:

```sh
HR_USERNAME=hr-manager
HR_PASSWORD_HASH='PASTE_YOUR_RAW_BCRYPT_HASH_HERE'
```

Replace the hash placeholder before starting the application. Keep the single quotes: BCrypt hashes contain dollar signs. The application accepts BCrypt hashes with cost at least 10; use cost 12 for local setup. The plaintext password is used only when signing in and must not be stored in `.env`.

If `htpasswd` is available (as on this development Mac), generate a hash with an interactive password prompt:

```sh
htpasswd -nBC 12 hr-manager
```

Copy only the raw `$2y$12$...` hash after the `hr-manager:` prefix into `HR_PASSWORD_HASH`. The command prints a hash without creating a password file. See [Apache's htpasswd documentation](https://httpd.apache.org/docs/2.4/programs/htpasswd.html). On other platforms, a local BCrypt tool or Spring Security's `BCryptPasswordEncoder(12)` can produce the same raw format; `$2a$`, `$2b$`, and `$2y$` are accepted. A `{bcrypt}` prefix is not used by this configuration.

## Run the Application

After building and starting the database, run from the repository root on macOS/Linux:

```sh
set -a
. ./.env
set +a
"$JAVA_HOME/bin/java" -jar backend/target/salary-management-0.0.1-SNAPSHOT.jar --spring.profiles.active=local --server.address=127.0.0.1
```

On Windows, set the same environment variables in PowerShell before running the JAR with your Java 21 executable.

The server starts at `http://127.0.0.1:8080`. Flyway applies pending migrations and validates completed migration checksums before startup succeeds. Missing/invalid HR configuration, missing database configuration, connection failures, or migration errors prevent a successful start.

The `local` profile explicitly allows cookies over loopback HTTP. Without it, session and CSRF cookies are Secure and require HTTPS in the browser. Both use `SameSite=Lax`; the session cookie is HTTP-only. Sessions expire after 30 minutes of inactivity and are lost on application restart. Production HTTPS and proxy configuration remain deployment work; do not activate `local` there.

Authentication endpoints are available under `/api/v1/auth`. The browser/client sequence is:

1. `GET /csrf` to obtain the readable `XSRF-TOKEN` cookie.
2. `POST /login` using URL-encoded `username` and `password`, the cookie, and an `X-XSRF-TOKEN` header containing its value. Success is `204`; invalid credentials produce a generic `401`.
3. Refresh the token with `GET /csrf` after login. `GET /session` returns only the authenticated username.
4. `POST /logout` with the current CSRF cookie/header to invalidate the session, then refresh the CSRF token before another login.

Missing/invalid CSRF tokens return `403`, including for login/logout. Unsupported login content types return `415` after CSRF validation. Security failures use `application/problem+json`, without HTML redirects or HTTP Basic challenges. API reads require authentication. Employee creation, lookup/editing by ID, and paginated listing are implemented. Read requests require a session but no CSRF token.

Stop the application with Ctrl+C. Stop the development database with `docker compose stop db`; its named volume is retained.

## Retrieve Supported Reference Data

Send authenticated `GET /api/v1/reference-data` before populating employee forms or filters. No CSRF cookie/header is required for this read. The `200` JSON response contains:

- `departments`: string `id` and `name`, ordered by name then ID.
- `countries`: `code` and `name`, ordered by code.
- `currencies`: `code`, `name`, and integer `minorUnits`, ordered by code.

The values come from the supported database catalogs used by employee validation. An empty catalog is an empty array. Department IDs remain exact strings even beyond JavaScript's safe integer range. Countries and currencies are independent choices; selecting a country does not imply a currency. Missing authentication returns `401` without a login redirect. Catalogs are managed through migrations; no write endpoints or caching are added.

## Create an Employee

After login and CSRF refresh, send `POST /api/v1/employees` with `Content-Type: application/json`, the session/CSRF cookies, and the `X-XSRF-TOKEN` header:

```json
{
  "employeeNumber": "EMP-000001",
  "fullName": "Asha Rao",
  "departmentId": "1",
  "countryCode": "IN",
  "salaryAmount": "125000.12",
  "currencyCode": "USD"
}
```

Choose a supported department ID from `/api/v1/reference-data`; the example department must exist in your database. Success returns `201`, a normalized employee response, and `Location: /api/v1/employees/{id}`. IDs/versions and salaries are JSON strings; timestamps are UTC strings. Salary is formatted to its currency's minor units. The response contains the six editable fields plus ID, version, timestamps, department name, and country name. Follow the returned `Location` to retrieve the record.

All six fields are required strings. Unknown/server-managed fields and non-string values are rejected. Invalid input/reference values return `400`; duplicate normalized numbers return `409 EMPLOYEE_NUMBER_CONFLICT`; unsupported request content types return `415`. Errors use Problem Details without rejected values or database details. Failed validation does not write an employee. Concurrent duplicate requests result in one creation and one conflict.

## List Employees

Send authenticated `GET /api/v1/employees?page=0&size=25`. Both parameters are optional: `page` defaults to `0` and `size` to `25`. The response contains `items` using the existing employee representation, plus integer `page`, `size`, `totalElements`, and `totalPages`.

By default, results are ordered by employee number ascending, then ID ascending, and paginated in PostgreSQL. Page numbers range from `0` through `2147483647`; sizes range from `1` through `100`. Values must contain ASCII digits only; leading zeros are accepted. Empty values, signs, whitespace, decimals, exponents, overflow, and repeated parameters return `400 VALIDATION_ERROR`.

Add optional `q`, for example `/api/v1/employees?q=asha&page=0&size=25`, to search employee number or full name by case-insensitive literal substring. Surrounding Unicode whitespace is stripped; absent/blank input means no search. The normalized limit is 200 Unicode code points. Repeated `q`, excessive length, and null characters return `400`. `%`, `_`, `!`, backslashes, and apostrophes are treated as literal text, with query values bound as parameters. Search does not split words; ordering is controlled separately through `sort`.

Add optional `departmentId`, `countryCode`, and `currencyCode` to narrow results, for example `/api/v1/employees?q=asha&countryCode=IN&currencyCode=USD`. Filters are combined with AND; the employee-number/name search stays grouped with OR. Department IDs follow the existing positive integer-string rules, including accepted leading zeros and rejected surrounding whitespace. Country/currency codes are trimmed and uppercased using the same rules as employee writes. Each supplied value must exist in the supported catalogs; supplied blank, malformed, unknown, or repeated filters return `400` with a field error. Country and currency remain independent choices. Omitted filters impose no restriction.

Add one optional `sort=field,direction`, for example `/api/v1/employees?countryCode=IN&sort=fullName,desc`. Supported fields are `employeeNumber`, `fullName`, `departmentName`, `countryCode`, `currencyCode`, and `salaryAmount`. Directions are exactly `asc` or `desc`; names/directions are case-sensitive, without surrounding whitespace. Missing or extra parts, blank or unsupported values, and repeated sort parameters return `400`. The default is `employeeNumber,asc`. Every ordering appends `id ASC`, including descending primary order, to keep ties stable across pages for unchanged data. Text ordering follows the database collation.

Salary sorting requires an explicit supported `currencyCode` filter, for example `?currencyCode=USD&sort=salaryAmount,asc`. It sorts database numeric values within that currency, with no conversion or cross-currency comparison. Missing currency returns `400` with `sort` / `CURRENCY_REQUIRED`, even for empty/no-match results; invalid supplied currencies retain their normal field errors.

An empty database or search/filter combination with no matches returns empty `items` with zero totals. Pages beyond the available matches return empty `items` with matching totals. GET needs no CSRF cookie/header; missing authentication returns `401` before parameter validation. Unsupported parameters return `400`, rather than being silently ignored.

The service validates each supplied catalog filter, then counts matches and fetches only an in-range page with its lookup labels. Employee queries use one count plus one content select for populated pages, or count only for out-of-range pages. Each supplied filter adds one catalog lookup, for up to five total selects with all three filters, or four for an out-of-range page. Tests check bounded employee loads and no per-employee lookup queries or writes. Offset pagination does not provide a stable snapshot while records change; totals and page rows can also observe separate commits under the default transaction isolation. Larger-scale performance still requires measurement.

## Retrieve an Employee

Send authenticated `GET /api/v1/employees/{id}`, or follow the creation response's `Location`. The response is `200` with the same employee representation. GET requires the session cookie; no CSRF header is required. Salary, version, and timestamps are preserved during retrieval.

IDs must contain ASCII digits and represent a positive value within Java's `Long` range. Leading zeros are accepted; signs, whitespace, decimal points, exponents, and out-of-range values return `400 VALIDATION_ERROR` for field `id`. A valid ID with no employee returns `404 EMPLOYEE_NOT_FOUND`. Unauthenticated requests return `401` before lookup or ID validation. Error responses exclude query strings.

## Edit an Employee

Send authenticated, CSRF-protected `PUT /api/v1/employees/{id}` with all six editable fields and the original `version` from GET. All seven values must be JSON strings. For example:

```json
{
  "employeeNumber": "EMP-000001",
  "fullName": "Asha Rao",
  "departmentId": "1",
  "countryCode": "IN",
  "salaryAmount": "145000.00",
  "currencyCode": "USD",
  "version": "0"
}
```

Success returns `200` with the updated employee. An effective change advances its server-managed version and update timestamp; ID and creation timestamp stay unchanged. If the normalized values already match, the existing version/timestamps are returned. The original-version check still applies, including concurrent changes detected at transaction commit. Currency and amount are replaced together without conversion.

Invalid input returns `400`, a missing target `404`, a duplicate number `409 EMPLOYEE_NUMBER_CONFLICT`, and an outdated version/concurrent edit `409 STALE_EMPLOYEE`. Invalid or conflicting updates leave the employee unchanged. Reload after a stale conflict rather than retrying with an overwritten version. PUT does not create missing records or provide salary history.

## Compensation Analytics

Send authenticated `GET /api/v1/analytics/compensation` for department grouping, or add `?groupBy=country`. Optional `q`, `departmentId`, `countryCode`, and `currencyCode` use the same rules as employee listing. For example: `/api/v1/analytics/compensation?groupBy=country&currencyCode=USD&countryCode=IN`. Each parameter may appear once; grouping names are exactly lowercase `department` or `country`. Pagination, sorting, and other unsupported parameters are rejected.

The `200` JSON response contains `groupBy`, integer `totalHeadcount`, `currencySummaries`, and `groups`. Every summary/group contains `currencyCode`, integer `headcount`, and decimal-string `totalSalary`, `averageSalary`, `minSalary`, and `maxSalary`; groups also include string `groupKey` and `groupLabel`. Department keys remain exact ID strings. A session is required, with no CSRF cookie/header for GET. Invalid parameters return `400` field errors, missing authentication returns `401`, and unsupported write methods return `405` after security checks. Responses inherit the existing security cache prevention headers.

PostgreSQL calculates group counts, sums, minimums, and maximums in one statement. Java combines only those aggregate rows into currency summaries and computes averages from exact totals/counts, rounding once with `HALF_EVEN` at currency precision. Totals/minimums/maximums remain exact, and totals may exceed the per-employee storage limit. No monetary total spans currencies. Empty results contain zero headcount and empty collections.

Both result levels come from the same captured group rows, including when another transaction changes salaries afterward. Groups are ordered by numeric department ID or country code, then currency code; currency summaries are ordered by currency code. The implementation uses existing JDBC support and shared employee read criteria/catalog validation, without schema changes or new dependencies.

## Verification and Testing

The migration suite checks fresh setup, repeat migration/validation, reference data, exact decimal storage, required values, unique identifiers, invalid salaries, reference integrity, and protected lookup deletion. Each test rolls back its changes; its disposable database is separate from the persistent development database.

Employee-creation unit tests cover normalization, international names, positive plain-decimal salaries, storage boundaries, currency precision, and precise conflict classification. Creation integration tests start the real Spring application against a separate disposable PostgreSQL database, check committed values through JDBC, and verify invalid requests leave no records. They also verify normalized duplicates and two concurrent same-number requests. Creation tests clear only their own disposable database between cases and close the application before stopping its container.

Security unit tests reject missing/plaintext/malformed account configuration and verify web startup fails without an explicit account. HTTP tests use isolated PostgreSQL and a real embedded server to exercise cookies, login/session/logout, protected reads/mutations, invalid credentials, CSRF rotation and failures, session invalidation/replay, content types, and JSON errors. Secure cookie attributes are checked separately from local HTTP behavior; the tests do not establish production TLS/proxy readiness.

`EmployeeApiIT` exercises authenticated creation, retrieval, and editing, exact response types/formatting, required fields, strict JSON types/fields, malformed bodies, invalid references and salaries, Unicode limits, duplicates/concurrency, unsupported media types, and access enforcement. It verifies full replacement, metadata/version behavior, unchanged submissions, rollback, and stale-form conflicts. `EmployeePersistenceIT` checks single-query retrieval and coordinates concurrent transactions with barriers/latches to verify competing edits and commit-time validation of an unchanged submission. Listing/search tests verify defaults, exact page envelopes, page traversal and matching totals, empty/oversized pages, strict query validation, session/CSRF behavior, both searchable fields, case matching, blank input, Unicode length boundaries, literal wildcard/escape/quote handling, all filter combinations with/without search, grouped OR/AND precedence, catalog normalization/validation, filtered page totals, all supported sort fields/directions in plain/searched listings, numeric salary ordering with currency enforcement, and ID tie-breaks across pages. Persistence tests verify that only the requested employees are loaded, labels need no additional queries, and reads preserve stored data. Focused unit/MVC tests verify ID formatting/parsing, pagination bounds for non-HTTP callers, safe errors, and explicit/JPA/Spring optimistic failures mapping to `409`.

Creation and effective updates obtain latest-change metadata from PostgreSQL's transaction clock. JPA manages the version; the client-supplied version is only a precondition. Unchanged submissions perform no employee write. Tests use disposable databases and do not reset development data.

`ReferenceDataApiIT` verifies the real HTTP response against PostgreSQL: complete ordered catalogs, exact JSON types, large string IDs, Unicode names, current database metadata, empty catalogs, authentication, GET without CSRF, unsupported media types, and rejection of catalog writes. It resets only its own disposable database between cases.

`CompensationRollupTest` checks weighted averages, avoiding rounded subgroup-average reuse, currency precision and `HALF_EVEN` boundaries, large totals, currency separation, and empty data. `CompensationAnalyticsIT` verifies both grouping dimensions against independent expected values, listing/filter/search parity, exact large IDs, PostgreSQL aggregation returning groups rather than employee entities, zero writes, and a coordinated writer committing between aggregation and rollup. Its 1,000-row fixture is an isolated query-shape check, not the production seed script or a workload benchmark.

`CompensationAnalyticsApiIT` exercises the real authenticated HTTP endpoint against isolated PostgreSQL: independently expected group/summary JSON, currency-scale strings, large IDs/totals, search/filter binding, literal and Unicode search, empty data, strict query errors, authentication before parsing, GET without CSRF, unsupported response media types, and absent write handlers.

`EmployeeSeedGeneratorTest` checks reproducibility, unique employee numbers, diverse valid names, catalog coverage, and currency precision. `EmployeeSeedIT` checks the complete 10,000-row load, listing/analytics compatibility, catalog metadata, rerun refusal with preserved HR edits, concurrent seed attempts, rollback after a late batch failure, repeatable business data, and the executable JAR's seed mode/exit status. These tests use only their own disposable PostgreSQL database.

Flyway alone creates eight departments, six countries, six currencies, and no employees. The separate seed command populates employees explicitly. Frontend feature screens and workload performance measurement remain future work.

Flyway owns schema changes. Add new versioned migration files for changes after a migration has been committed/applied; do not rewrite completed migrations. Hibernate validates the schema at startup and does not generate it. Separate `schema.sql`/`data.sql` initialization and Open Session in View are disabled.

## Design Documents

- [Requirements](docs/REQUIREMENTS.md)
- [Architecture and trade-offs](docs/ARCHITECTURE.md)
- [Data model](docs/DATA_MODEL.md)
- [API contracts](docs/API.md)
- [Assumptions and pending clarification](docs/ASSUMPTIONS.md)
- [AI usage: major code changes](docs/AI_USAGE.md)

## Deployment

Deployment is not configured. The planned target is one Spring Boot application serving Angular assets and REST under one public origin, backed by PostgreSQL. Hosting and production access configuration remain pending.

# Employee Salary Management

Employee salary management for an HR Manager overseeing 10,000 employees across multiple countries, built for the Incubyte engineering assessment.

## Current Status

The backend connects to PostgreSQL through JPA and applies its schema/reference data through Flyway. Authenticated employee creation, retrieval, and full editing are available through JSON APIs, with stale-update protection, session authentication, and Angular-compatible CSRF protection. Authenticated reference-data lookup and server-side paginated employee search are also available. Filtering, selectable sorting, analytics endpoints, employee seeding, and the Angular UI remain planned.

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

Results are ordered by employee number ascending, then ID ascending, and paginated in PostgreSQL. Page numbers range from `0` through `2147483647`; sizes range from `1` through `100`. Values must contain ASCII digits only; leading zeros are accepted. Empty values, signs, whitespace, decimals, exponents, overflow, and repeated parameters return `400 VALIDATION_ERROR`.

Add optional `q`, for example `/api/v1/employees?q=asha&page=0&size=25`, to search employee number or full name by case-insensitive literal substring. Surrounding Unicode whitespace is stripped; absent/blank input means no search. The normalized limit is 200 Unicode code points. Repeated `q`, excessive length, and null characters return `400`. `%`, `_`, `!`, backslashes, and apostrophes are treated as literal text, with query values bound as parameters. Search does not split words or change the fixed ordering.

An empty database or search with no matches returns empty `items` with zero totals. Pages beyond the available matches return empty `items` with matching totals. GET needs no CSRF cookie/header; missing authentication returns `401` before parameter validation. Filters, selectable sorting, and other unsupported parameters currently return `400`, rather than being silently ignored. They remain planned increments.

The service counts first, fetching only an in-range page and its lookup labels. Tests verify two selects for populated pages and one for out-of-range pages, with no per-employee lookup queries or writes. Offset pagination does not provide a stable snapshot while records change; totals and page rows can also observe separate commits under the default transaction isolation. Larger-scale performance still requires measurement.

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

## Verification and Testing

The migration suite checks fresh setup, repeat migration/validation, reference data, exact decimal storage, required values, unique identifiers, invalid salaries, reference integrity, and protected lookup deletion. Each test rolls back its changes; its disposable database is separate from the persistent development database.

Employee-creation unit tests cover normalization, international names, positive plain-decimal salaries, storage boundaries, currency precision, and precise conflict classification. Creation integration tests start the real Spring application against a separate disposable PostgreSQL database, check committed values through JDBC, and verify invalid requests leave no records. They also verify normalized duplicates and two concurrent same-number requests. Creation tests clear only their own disposable database between cases and close the application before stopping its container.

Security unit tests reject missing/plaintext/malformed account configuration and verify web startup fails without an explicit account. HTTP tests use isolated PostgreSQL and a real embedded server to exercise cookies, login/session/logout, protected reads/mutations, invalid credentials, CSRF rotation and failures, session invalidation/replay, content types, and JSON errors. Secure cookie attributes are checked separately from local HTTP behavior; the tests do not establish production TLS/proxy readiness.

`EmployeeApiIT` exercises authenticated creation, retrieval, and editing, exact response types/formatting, required fields, strict JSON types/fields, malformed bodies, invalid references and salaries, Unicode limits, duplicates/concurrency, unsupported media types, and access enforcement. It verifies full replacement, metadata/version behavior, unchanged submissions, rollback, and stale-form conflicts. `EmployeePersistenceIT` checks single-query retrieval and coordinates concurrent transactions with barriers/latches to verify competing edits and commit-time validation of an unchanged submission. Listing/search tests verify defaults, exact page envelopes, page traversal and matching totals, empty/oversized pages, strict query validation, session/CSRF behavior, both searchable fields, case matching, blank input, Unicode length boundaries, and literal wildcard/escape/quote handling. Persistence tests verify that only the requested employees are loaded, labels need no additional queries, and reads preserve stored data. Focused unit/MVC tests verify ID formatting/parsing, pagination bounds for non-HTTP callers, safe errors, and explicit/JPA/Spring optimistic failures mapping to `409`.

Creation and effective updates obtain latest-change metadata from PostgreSQL's transaction clock. JPA manages the version; the client-supplied version is only a precondition. Unchanged submissions perform no employee write. Tests use disposable databases and do not reset development data.

`ReferenceDataApiIT` verifies the real HTTP response against PostgreSQL: complete ordered catalogs, exact JSON types, large string IDs, Unicode names, current database metadata, empty catalogs, authentication, GET without CSRF, unsupported media types, and rejection of catalog writes. It resets only its own disposable database between cases.

The schema starts with eight departments, six countries, six currencies, and no employees. The 10,000-employee seed script is a later step. Filtering, selectable sorting, analytics endpoints, and the frontend remain future work.

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

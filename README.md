# Employee Salary Management

Employee salary management for an HR Manager overseeing 10,000 employees across multiple countries, built for the Incubyte engineering assessment.

## Current Status

The backend connects to PostgreSQL through JPA and applies its schema/reference data through Flyway. Authenticated employee creation is available through a validated JSON API, with session authentication and Angular-compatible CSRF protection. Retrieval, editing/listing, reference-data/analytics endpoints, employee seeding, and the Angular UI remain planned.

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

Missing/invalid CSRF tokens return `403`, including for login/logout. Unsupported login content types return `415` after CSRF validation. Security failures use `application/problem+json`, without HTML redirects or HTTP Basic challenges. API reads require authentication. Only employee creation is implemented: anonymous employee requests require login; authenticated `GET /api/v1/employees` currently returns `405` because listing is not implemented.

Stop the application with Ctrl+C. Stop the development database with `docker compose stop db`; its named volume is retained.

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

The example department must exist in your database. Success returns `201`, a normalized employee response, and `Location: /api/v1/employees/{id}`. IDs/versions and salaries are JSON strings; timestamps are UTC strings. Salary is formatted to its currency's minor units. The response contains the six editable fields plus ID, version, timestamps, department name, and country name. Retrieval at `Location` will be implemented in a later step.

All six fields are required strings. Unknown/server-managed fields and non-string values are rejected. Invalid input/reference values return `400`; duplicate normalized numbers return `409 EMPLOYEE_NUMBER_CONFLICT`; unsupported request content types return `415`. Errors use Problem Details without rejected values or database details. Failed validation does not write an employee. Concurrent duplicate requests result in one creation and one conflict.

## Verification and Testing

The migration suite checks fresh setup, repeat migration/validation, reference data, exact decimal storage, required values, unique identifiers, invalid salaries, reference integrity, and protected lookup deletion. Each test rolls back its changes; its disposable database is separate from the persistent development database.

Employee-creation unit tests cover normalization, international names, positive plain-decimal salaries, storage boundaries, currency precision, and precise conflict classification. Creation integration tests start the real Spring application against a separate disposable PostgreSQL database, check committed values through JDBC, and verify invalid requests leave no records. They also verify normalized duplicates and two concurrent same-number requests. Creation tests clear only their own disposable database between cases and close the application before stopping its container.

Security unit tests reject missing/plaintext/malformed account configuration and verify web startup fails without an explicit account. HTTP tests use isolated PostgreSQL and a real embedded server to exercise cookies, login/session/logout, protected reads/mutations, invalid credentials, CSRF rotation and failures, session invalidation/replay, content types, and JSON errors. Secure cookie attributes are checked separately from local HTTP behavior; the tests do not establish production TLS/proxy readiness.

Employee API integration tests exercise authenticated creation, exact response types/formatting, required fields, strict JSON types/fields, malformed bodies, invalid references and salaries, Unicode limits, duplicates/concurrency, unsupported media types, and access enforcement. Focused unit tests verify IDs above JavaScript's safe integer range and generic error responses that exclude exception details and query values.

The schema starts with eight departments, six countries, six currencies, and no employees. The 10,000-employee seed script is a later step. Retrieval, editing/stale-form behavior, listing, reference-data/analytics endpoints, and the frontend remain future work.

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

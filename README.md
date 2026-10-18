# Employee Salary Management

Employee salary management for an HR Manager overseeing 10,000 employees across multiple countries, built for the Incubyte engineering assessment.

## Current Status

The backend connects to PostgreSQL and applies its schema/reference data through Flyway. Migration integration tests run against an isolated PostgreSQL container. Employee management, analytics, authentication, employee seeding, and the Angular UI remain planned.

## Backend Toolchain

- Java 21
- Spring Boot 4.1.1 with Spring MVC
- Maven 3.9.11, pinned by the included Maven Wrapper
- PostgreSQL 18.6, supplied locally through Docker Compose
- Flyway for versioned migrations
- Spring Boot's managed test starter, including JUnit Jupiter 6 and Mockito
- Testcontainers for isolated PostgreSQL integration tests

JPA entities/repositories, application validation, and security dependencies will be added with their implementation steps.

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

`./mvnw test` runs the unit-test phase; no unit tests exist yet. `./mvnw verify` also runs the database integration tests through Maven Failsafe. Those tests require Docker but do not require the development database or its credentials.

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

## Run the Application

After building and starting the database, run from the repository root on macOS/Linux:

```sh
set -a
. ./.env
set +a
"$JAVA_HOME/bin/java" -jar backend/target/salary-management-0.0.1-SNAPSHOT.jar --server.address=127.0.0.1
```

On Windows, set the same environment variables in PowerShell before running the JAR with your Java 21 executable.

The server starts at `http://127.0.0.1:8080`. Flyway applies pending migrations and validates completed migration checksums before startup succeeds. Missing database configuration, connection failures, or migration errors prevent a successful start. Business routes are not implemented, so `/api/v1/employees` still returns `404`.

Stop the application with Ctrl+C. Stop the development database with `docker compose stop db`; its named volume is retained.

## Verification and Testing

The migration suite checks fresh setup, repeat migration/validation, reference data, exact decimal storage, required values, unique identifiers, invalid salaries, reference integrity, and protected lookup deletion. Each test rolls back its changes; its disposable database is separate from the persistent development database.

The schema starts with eight departments, six countries, six currencies, and no employees. The 10,000-employee seed script is a later step. Currency-specific input validation, employee workflows, concurrency behavior, and analytics tests will accompany their features.

Flyway owns schema changes. Add new versioned migration files for changes after a migration has been committed/applied; do not rewrite completed migrations. Hibernate schema generation and separate `schema.sql`/`data.sql` initialization are not used.

## Design Documents

- [Requirements](docs/REQUIREMENTS.md)
- [Architecture and trade-offs](docs/ARCHITECTURE.md)
- [Data model](docs/DATA_MODEL.md)
- [API contracts](docs/API.md)
- [Assumptions and pending clarification](docs/ASSUMPTIONS.md)
- [AI usage: major code changes](docs/AI_USAGE.md)

## Deployment

Deployment is not configured. The planned target is one Spring Boot application serving Angular assets and REST under one public origin, backed by PostgreSQL. Hosting and production access configuration remain pending.

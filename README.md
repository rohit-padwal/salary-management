# Employee Salary Management

Employee salary management for an HR Manager overseeing 10,000 employees across multiple countries, built for the Incubyte engineering assessment.

## Current Status

The backend foundation builds and runs. Requirements, architecture, data model, and API contracts are documented. Employee management, analytics, authentication, PostgreSQL integration, migrations, seeding, and the Angular UI are planned.

## Backend Toolchain

- Java 21
- Spring Boot 4.1.1 with Spring MVC
- Maven 3.9.11, pinned by the included Maven Wrapper
- Spring Boot's managed test starter, including JUnit Jupiter 6 and Mockito

Persistence, validation, and security dependencies will be added with their implementation steps.

## Build

Install JDK 21 and set `JAVA_HOME` to it. Maven Wrapper downloads Maven and project dependencies on first use, requiring HTTPS access to Maven Central. A global Maven installation is not required. The Windows wrapper uses PowerShell.

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

## Run Locally

After building, run from `backend/`:

```sh
"$JAVA_HOME/bin/java" -jar target/salary-management-0.0.1-SNAPSHOT.jar --server.address=127.0.0.1
```

The server starts at `http://127.0.0.1:8080`. Stop it with Ctrl+C. No database or credentials are required at this stage. Business routes are not implemented, so `/api/v1/employees` currently returns `404`.

## Verification and Testing

`./mvnw verify` compiles and packages the application. No automated application tests exist yet. The packaged JAR has also passed a local startup and HTTP 404 smoke check. Business-rule tests will be introduced with each feature using Red -> Green -> Refactor.

## Design Documents

- [Requirements](docs/REQUIREMENTS.md)
- [Architecture and trade-offs](docs/ARCHITECTURE.md)
- [Data model](docs/DATA_MODEL.md)
- [API contracts](docs/API.md)
- [Assumptions and pending clarification](docs/ASSUMPTIONS.md)
- [AI usage: major code changes](docs/AI_USAGE.md)

## Deployment

Deployment is not configured. The planned target is one Spring Boot application serving Angular assets and REST under one public origin, backed by PostgreSQL. Hosting and production access configuration remain pending.

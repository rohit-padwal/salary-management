# Employee Salary Management

Incubyte assessment for an HR Manager maintaining employee compensation across multiple countries.

## Current Status

The requirements, architecture, data model, and API contracts are documented. The backend currently contains a runnable Spring Boot foundation only. Employee workflows, analytics, authentication, PostgreSQL integration, migrations, seeding, and the Angular UI remain planned work.

## Backend Toolchain

- Java 21
- Spring Boot 4.1.1 with Spring MVC
- Maven 3.9.11, pinned by the included Maven Wrapper
- Spring Boot's managed test starter, including JUnit Jupiter 6 and Mockito

JUnit 6 was approved in place of the initial JUnit 5 preference to follow the current Spring Boot testing stack. Persistence, validation, and security dependencies will be added with their approved implementation steps.

## Build

Install a JDK 21 and set `JAVA_HOME` to it. The wrapper downloads its pinned Maven distribution and dependencies on first use; a global Maven installation is not required. Initial downloads require HTTPS access to Maven Central. The Unix wrapper uses available download tools or its Java fallback; the Windows wrapper uses PowerShell.

On macOS, select an installed Java 21 JDK for the current shell:

```sh
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
```

On other platforms, set `JAVA_HOME` to your JDK 21 installation using your shell's normal environment configuration. This matters even if `java -version` already shows 21: Maven can otherwise run on a different installed JDK.

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

The server starts on `http://127.0.0.1:8080`. Stop it with Ctrl+C. No business routes are registered yet, so a request to `/api/v1/employees` currently returns `404`. No database or credentials are required for this bootstrap stage.

## Verification and Testing

`./mvnw verify` compiles and packages the application using the standard Maven lifecycle. No automated application tests exist yet; a successful build does not prove employee or security behavior. Bootstrap verification also starts the packaged JAR on loopback and checks its HTTP response. Business-rule tests will be introduced with the relevant features using Red -> Green -> Refactor.

## Design Documents

- [Requirements](docs/REQUIREMENTS.md)
- [Architecture and trade-offs](docs/ARCHITECTURE.md)
- [Data model](docs/DATA_MODEL.md)
- [API contracts](docs/API.md)
- [Assumptions and pending clarification](docs/ASSUMPTIONS.md)
- [AI collaboration and verification record](docs/AI_USAGE.md)

## Deployment

Deployment is not configured yet. The approved target is one Spring Boot application serving Angular assets and REST under one public origin, backed by PostgreSQL. Hosting, production access configuration, and deployed verification remain separate approved steps.

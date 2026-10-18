# Backend Technical Documentation

Run the complete application using the [root setup guide](../README.md). This document covers backend organization and operations; [API.md](../docs/API.md) is the canonical contract/rules reference, [DATA_MODEL.md](../docs/DATA_MODEL.md) the physical schema, and [ARCHITECTURE.md](../docs/ARCHITECTURE.md) the decision/trade-off record.

## Toolchain and Layout

Java 21, Spring Boot 4.1.1 / MVC, Spring Data JPA, Jakarta Bean Validation, Spring Security, PostgreSQL 18.6, Flyway, and Maven Wrapper 3.9.11. Boot manages JUnit Jupiter 6 and Mockito; Failsafe/Testcontainers run PostgreSQL and real-HTTP integration tests. Maven's distribution checksum is pinned. Select Java 21 explicitly through `JAVA_HOME`; a global Maven is unnecessary.

Source root: `src/main/java/com/acme/salarymanagement/`; tests mirror feature/responsibility packages under `src/test/java/`.

| Feature | Responsibility folders |
| --- | --- |
| `employee` | `controller`, `dto`, `entity`, `exception`, `model`, `repository`, `seed`, `service`, `validation` |
| `referencedata` | `controller`, `dto`, `entity`, `model`, `repository`, `service` |
| `analytics` | `controller`, `dto`, `model`, `repository`, `service` |
| `api` | `config`, `exception`, `validation` |
| `security` | `config`, `controller`, `exception`, `filter` |
| `infrastructure` | `persistence`, `bootstrap`, `controller` |

`compensation/SalaryRules.java` and shared `validation/` remain focused small units. Migrations live in `src/main/resources/db/migration`; scripts in root `scripts/`. Create layers only when needed; analytics uses projections rather than entities. Manual DTO mapping separates HTTP from persistence without a mapping library or generic CRUD abstraction.

## Configuration

| Environment variable | Meaning |
| --- | --- |
| `DB_URL` | PostgreSQL JDBC URL, e.g. `jdbc:postgresql://127.0.0.1:5432/salary_management_local`. |
| `DB_USERNAME`, `DB_PASSWORD` | Required database connection credentials. |
| `HR_USERNAME` | Required configured web-login account. |
| `HR_PASSWORD_HASH` | Required raw BCrypt `$2a$`, `$2b$`, or `$2y$` hash, cost at least 10; recommend 12. No `{bcrypt}` prefix. |
| `SPRING_PROFILES_ACTIVE=local` | Explicit HTTP cookie support for loopback development/demo only. |
| `APP_DEMO_SEED_ENABLED=true` | Opt-in web startup seeding when the employee table is empty; absent/false by default. |

The application has no default HR account. The root `.env.example` and Compose configuration supply intentionally public local-demo settings explicitly. Real credentials belong in private environment configuration; `.env`/`.env.*` are ignored, sanitized `.env.example` remains trackable. Java does not automatically load root `.env`; export variables in its launch shell. Do not overwrite private configuration with demo settings. Restrict private environment files to the owner (`chmod 600 .env` on POSIX).

For a private account, generate a raw cost-12 BCrypt hash using a local tool. If Apache `htpasswd` is installed:

```sh
htpasswd -nBC 12 hr-manager
```

It prompts without creating a password file; copy only the hash after `hr-manager:`. Use single-quoted shell assignments because hashes contain `$`. Keep private plaintext passwords out of committed configuration and command-line arguments; the documented public demo values are deliberately disposable. See [htpasswd documentation](https://httpd.apache.org/docs/2.4/programs/htpasswd.html).

Web startup fails on missing/invalid HR settings, database connection failure, or migration failure. Standalone seed mode needs only database configuration. Default session/XSRF cookies are Secure and require HTTPS; `local` relaxes Secure for HTTP. Sessions are HTTP-only, cookie-only, SameSite=Lax, 30-minute idle timeout, and lost on backend restart.

## Build and Database Lifecycle

From `backend/`, with JDK 21 selected:

```sh
./mvnw -version
./mvnw test
./mvnw verify
```

`test` runs unit tests without Docker; `verify` packages the executable JAR and runs integration suites with isolated PostgreSQL containers. First use downloads Maven/dependencies/images. Use `mvnw.cmd` on Windows. `./mvnw -DskipTests package` builds without executing tests, as used by the container build; it is not verification evidence.

Flyway V1 creates schema; V2 initializes catalogs. Pending migrations run and completed checksums validate before successful startup. Hibernate uses `ddl-auto=validate`; SQL init and Open Session in View are disabled; Flyway clean is disabled. Add new versioned migrations instead of editing applied ones. Migrations use the configured role; separate least-privilege migration/runtime roles remain production delivery work.

Native PostgreSQL setup and the exact JAR command are in the [manual guide](../README.md#manual-setup-without-docker). Changing environment credentials does not change roles already stored in a persistent volume.

## Synthetic Data Seeding

After building and exporting database settings, run from the repository root:

```sh
./scripts/seed-employees.sh
# Equivalent, using Java 21:
java -jar backend/target/salary-management-0.0.1-SNAPSHOT.jar --seed-employees
```

Use the seed flag alone. This mode applies/validates migrations, loads exactly 10,000 employees, prints `Seeded 10000 synthetic employees.`, and exits without a web server/HR account. A nonempty employee table causes nonzero exit with no employee modification. There is no reset/overwrite option.

The Docker demo instead sets `APP_DEMO_SEED_ENABLED=true`: a web startup runner inserts only into an empty employee table and returns zero inserted for any existing dataset. A failed load rolls back and fails startup. This is deliberately opt-in; ordinary startup does not seed. Both paths use a table write lock and one transaction, serializing concurrent initializations while allowing ordinary reads. JDBC batches contain 500 rows; all generated records pass shared rules before insertion. No HTTP seed/reset endpoint exists.

Dataset v1 fixes random seed `20261018`, employee numbers `SYN-000001`..`SYN-010000`, curated international name combinations, eight departments, and six countries/currencies. IN/US/GB/DE have 1,667 employees each; JP/KW have 1,666. Illustrative annual salary bands are synthetic, not market benchmarks. Local country/currency pairing is a generator choice, not an application constraint.

Department IDs resolve by name and precision by currency code; required catalogs must exist. Business fields reproduce with the same generator/catalog metadata on Java 21. Generated IDs/timestamps are excluded, and sequences can advance after rollback. To reproduce fresh data, use a separate empty database or explicitly reset the disposable demo volume.

## API and Business Behavior

The [API contract](../docs/API.md) contains endpoint tables, request/response shapes, normalization, monetary bounds/precision, page/filter/sort rules, analytics output, session/CSRF sequence, and errors. The [schema](../docs/DATA_MODEL.md) documents relationships/constraints/indexes.

Employee services validate and resolve references, apply framework-free salary rules, and commit identity/current compensation atomically. Optimistic locks protect original-version edits through commit. Exact duplicate-constraint translation avoids misclassifying unrelated integrity failures. Database-sourced latest-change timestamps and no-change behavior preserve metadata correctly.

Directory queries bind literal input and map approved sort aliases to fixed properties. Count plus bounded content fetch avoids loading 10,000 entities; joined lookup labels prevent N+1 reads. Each supplied catalog filter adds one validation read. An out-of-range page skips content fetch. Offset pages do not guarantee snapshot consistency while edits occur.

Analytics performs one grouped SQL read; Java rolls up only aggregate rows into exact currency summaries and weighted averages. Currency precision comes from catalogs. Native SQL couples reporting to the schema, guarded by cross-endpoint parity tests; no cache, materialized summary, or exchange-rate service is present.

Controller and security failures share RFC 9457 problems. Malformed JSON/unknown fields/wrong types use safe generic errors. Public errors omit rejected values, SQL/parser details, credentials, and stack traces; unexpected application failures log exception type only. Hibernate/infrastructure diagnostics can still contain database values, so do not enable verbose SQL or expose logs for real salary data without the planned security review.

## Container Runtime and Readiness

[Compose](../compose.yaml) builds the backend JAR with a JDK and runs it as a non-root user on a JRE. The build installs `unzip` so Maven Wrapper uses its checksum-pinned ZIP rather than switching to tar.gz. The frontend builds with pinned Node/npm and serves static assets through Nginx, proxying `/api` without stripping the path. Client routes fall back to `index.html`; missing assets and API failures do not.

Database health gates backend startup. Public `GET /api/v1/health` remains `503 STARTING` until Spring startup runners complete; afterward it probes `SELECT 1`, returning `200 UP` or `503 DOWN` without database details. Frontend startup waits for backend health. This startup gate is not ongoing orchestration: a later backend outage does not automatically stop/restart dependents.

The demo database is internal to the Compose network, with PostgreSQL 18 storage mounted at `/var/lib/postgresql`. Backend/frontend bind to loopback ports 8080/4200. `DEMO_*` Compose overrides avoid reusing unrelated private `DB_*` settings. The explicit project name isolates the demo volume from the older `salary-management` database-only setup; no automatic migration/deletion of that volume occurs.

## Testing and Operational Limits

- Unit tests cover text/IDs/money, exact rollups, service conflict classification, HTTP mapping/errors, account validation, and readiness.
- `FlywayMigrationIT` covers schema/catalogs/constraints and exact persistence.
- `EmployeePersistenceIT` covers transactions, duplicates, optimistic races/no-change commit checks, bounded queries, and zero-write reads.
- `EmployeeApiIT`, `ReferenceDataApiIT`, `SessionSecurityIT`, and analytics API suites exercise real embedded HTTP sessions/CSRF and committed PostgreSQL values.
- Analytics suites compare independent sums/rounding, listing parity, large totals/IDs, and a writer committing after aggregation.
- Seed suites cover deterministic business fields, 10,000 rows, catalog variations, rollback after late failure, strict rerun refusal, concurrent attempts, and packaged CLI behavior. `DemoStartupIT` verifies full web startup and restart preservation on one database.

All integration databases are disposable; tests never target the persistent demo/development database. Frontend specs and connected browser observations are indexed in [coverage](../docs/COVERAGE_REVIEW.md) and [verification](../docs/VERIFICATION.md). Ad hoc browser tooling is not a committed end-to-end runner.

Current query/load-count checks establish bounded behavior, not latency or million-row readiness. Production TLS/proxy trust, credentials/roles, backup/restore, image lifecycle, workload measurement, security review, and deployment remain pending. Extension boundaries and accepted trade-offs are in [architecture](../docs/ARCHITECTURE.md#future-expansion).

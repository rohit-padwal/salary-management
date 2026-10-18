# Architecture and Engineering Decisions

The implemented MVP follows the approved [requirements](REQUIREMENTS.md), with provisional business rules in [ASSUMPTIONS.md](ASSUMPTIONS.md). Setup is in the [root README](../README.md); operational/backend details are in [technical documentation](../backend/TECHNICAL_DOCUMENTATION.md).

## System and Boundaries

Use a modular monolith: one Spring Boot build, one Angular application, and one PostgreSQL database. Feature-local responsibility folders keep employee, compensation, analytics, reference-data, security, and infrastructure code together.

Controllers translate HTTP/DTOs; services coordinate use cases and transactions; repositories perform persistence. Small mappings are manual. Business validation and monetary calculations are independently testable without requiring duplicate domain models or repository interfaces for every class. Controllers do not coordinate another feature's repositories directly.

| Decision | Why it fits now | Accepted trade-off / expansion path |
| --- | --- | --- |
| Modular monolith rather than microservices | Local calls, atomic transactions, simple deployment | Boundaries rely on code review; split only for demonstrated independent scaling/ownership needs. |
| Feature-local `controller`, `dto`, `service`, `model`, `entity`, `repository`, etc. | Easier navigation and clear ownership | Cross-subpackage types need public visibility; helpers stay package-private. No generic CRUD framework. |
| Current salary on employee | One current value and lifecycle, one versioned atomic edit | History or independently effective compensation would need new tables and update/report rules. |
| Controlled lookup tables | Consistent reports and supported currency precision | Catalog changes use migrations; live administration would need explicit consistency/cache decisions. |
| DTOs and exact string money/IDs/versions | Stable contracts, no JavaScript precision loss | Explicit parsing/formatting at the boundary. |
| Body version with optimistic locking | Fits a form-driven full PUT | No automatic merge/retry; ETag/If-Match would require a contract change. |
| SQL aggregation with Java rollup | One database read, small aggregate payload, exact arithmetic | Analytics couples to schema; schema changes must review native SQL and parity tests. |
| Single configured HR session | Straightforward same-origin authentication/CSRF | No individual attribution, shared-account limitations, sessions lost on restart. |

Employee management owns the employee entity/repository and coordinates compensation changes. `compensation/SalaryRules` owns monetary validation. Analytics is read-only; reference data owns the migration-controlled catalogs. Infrastructure supplies database timestamps, demo startup, and readiness. Supporting folders exist only where needed.

Java 21 / Spring Boot 4.1.1 use Maven Wrapper 3.9.11 with a distribution checksum. Boot-managed JUnit Jupiter 6 was approved in place of the original JUnit 5 preference. Angular/Material 22.2.3 was the verified stable baseline when selected on 2026-10-18; this task does not upgrade application dependencies.

## Data and Transaction Decisions

The [schema](DATA_MODEL.md) has four tables. Flyway owns versioned changes; Hibernate validates rather than creates schema. SQL initialization and Open Session in View are disabled. Do not rewrite applied migrations.

Creation and full editing validate/resolve all fields within one transaction. The exact employee-number unique constraint is translated after flush; an existence pre-check would not prevent races. Other integrity failures retain their classification.

Editing checks the submitted original version and holds an optimistic employee lock through commit, including normalized no-change submissions. The edit query deliberately excludes the read entity graph: applying the lock to fetched unversioned lookup entities caused a provider error. Ordinary retrieval retains its graph for one-query labels. Effective changes obtain update time from PostgreSQL's transaction clock; generated update-returning behavior previously obscured stale-row detection, so the service sets this value explicitly. No-change saves perform no write or clock query.

Money uses `BigDecimal` and currency metadata, rejecting meaningful excess precision before PostgreSQL can coerce it. Aggregates derive averages once from exact totals/counts using HALF_EVEN. See the [API rules](API.md); no conversion is implied.

## Read Design and Performance Boundaries

Directory count/content share a parameterized predicate and validated sort mapping. Literal LIKE escaping handles `!` before `%`/`_`. Explicit String casts on nullable JPQL search patterns avoid the PostgreSQL/Hibernate `lower(bytea)` inference failure found in integration testing. Catalog validation happens independently of matches.

Count first, skip out-of-range content, and load only the bounded page plus to-one labels. An in-range page uses two employee queries plus up to three supplied-catalog lookups; it has no per-employee queries. ID ascending always breaks primary-sort ties. Offset pagination is simple at the target scale; count and rows may observe different commits and separate pages are not a stable snapshot. Very deep pages/cursor contracts need reassessment as scale increases.

Analytics uses one native grouped statement including currency metadata. Java derives currency summaries from the same captured rows, avoiding inconsistent double reads and rounded-average reuse. Listing/native-query predicate duplication is accepted and guarded by parity tests. Aggregate totals are not narrowed to the per-employee storage type.

Existing indexes are a starting point. Substring search, alternate sorts, larger datasets, and realistic concurrency need measurement before extra indexes, caching, or infrastructure. Bounded-read tests are not load benchmarks.

## Browser and Security Boundaries

Standalone OnPush Angular components use signals, typed Reactive Forms, and small HttpClient services. Money remains strings throughout the UI. Shared normalization mirrors Java 21 whitespace; explicit Apply separates draft from applied criteria. See [frontend conventions](FRONTEND.md).

Authentication is enforced by Spring Security, with cookie-only sessions and Angular's XSRF integration. The application never persists credentials/tokens in browser storage. Cookies default to Secure; `local` explicitly permits HTTP. Public demo credentials belong only to local setup configuration, not application defaults. Unexpected public errors omit values/SQL details; application error logging records exception types only. Deployment security review remains pending.

## Reviewer Packaging and Delivery

The earlier production proposal was Spring Boot serving Angular assets. The reviewer setup now uses an Angular production build served by Nginx, proxying `/api` to the backend under one browser origin. This satisfies the requested full-stack Compose setup without CORS or a new application framework. It adds one small serving container; backend/frontend releases remain coordinated.

Compose project `salary-management-demo` isolates its persistent database volume from the older database-only development project. Database health gates backend startup; application readiness gates frontend startup. Opt-in `APP_DEMO_SEED_ENABLED=true` runs a transactional seed-if-empty after migrations. Existing records are preserved. The strict standalone seed command retains its refusal on nonempty tables. Tests cover concurrent initialization and restart preservation.

The public health response exposes status only and stays unready until startup runners finish and a database probe succeeds. Published demo ports bind to loopback; PostgreSQL is internal. Public hosting, TLS, production identities/roles, backups, image update policy, and deployed browser verification remain delivery decisions. The local profile and public defaults must not be used for real salary data.

## Future Expansion

- Add history/audit through migrations with explicit effective-date and attribution rules.
- Add users/RBAC behind security boundaries; choose shared sessions only if multiple instances are required.
- Extend supported catalogs through reviewed migrations; new precision above four digits requires schema/policy changes.
- Add conversion only with approved rate source, effective date, and rounding semantics.
- Measure query/index plans before cursor pagination, specialized search indexes, or precomputed reports.
- Add CSV validation/duplicate/failure semantics as a separate use case, reusing current rules.

Comments explain non-obvious invariants and provider workarounds; they do not restate getters or straightforward mappings. No factories, event bus, cache, or distributed infrastructure is needed by the current requirements.

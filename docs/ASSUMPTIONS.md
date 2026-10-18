# Assumptions and Pending Clarifications

## Status and Evidence

The user approved proceeding with the documented assumptions and saving the requirements baseline. Approval makes these project decisions usable for planning; it does not make them confirmed Incubyte requirements. The user has emailed Incubyte and reported that a response is pending.

The assessment was read in full before drafting [REQUIREMENTS.md](REQUIREMENTS.md). It confirms the HR persona, multi-country context, 10,000-employee scale, end-to-end software, relational database, seed script, tests, AI use, incremental commits, deployment, video, and supporting artifacts. It does not prescribe the detailed workflows or monetary rules below.

## Provisional Product Decisions

| Topic | Working decision | Rationale and boundary |
| --- | --- | --- |
| Salary basis | Gross annual base salary, excluding bonuses, benefits, taxes, and deductions. | Gives reporting a consistent meaning; revise if another salary definition is required. |
| Salary values | Positive, finite amounts stored as `NUMERIC(19,4)` and calculated with `BigDecimal`; validate currency precision before persistence. | Reject meaningful excess precision rather than silently round; insignificant trailing zeros may be accepted. The storage limit is not an HR policy maximum. Return decimal strings; round analytics averages once using `HALF_EVEN` at currency minor units. |
| Currency | Preserve each salary's original ISO currency code, referencing supported currency metadata with 0-4 minor units. | Employment country and salary currency are independent. Exact supported records remain to be selected and verified. |
| Reporting | Separate monetary results by currency; do not perform exchange-rate conversion. | Avoid comparing or adding unlike amounts. Overall headcount may span currencies. |
| Employee record | Stable internal identity; editable unique uppercase employee number up to 32 ASCII letters/digits/hyphens/underscores, starting with a letter/digit; nonblank full name up to 200 characters; department, country, current salary/currency, version, and timestamps. | Trim employee numbers and names; preserve internal name spacing/case. Current salary shares the employee lifecycle. Timestamps/version do not provide history or auditing. |
| Management | List, literal case-insensitive substring search, department/country/currency filters, one allowlisted sort, add, and full edit. | Zero-based pagination defaults to 25 records with a maximum of 100. Salary sorting requires a currency filter. |
| Analytics | Headcount and total/average/minimum/maximum salary by department or country, separated by currency, with the same search/filters as listing. | Each response uses a consistent snapshot. Empty results have zero headcount and empty group/summary arrays. No cross-currency monetary totals. |
| Seeding | Exactly 10,000 realistic synthetic employees from a fixed seed. | Makes initial setup reproducible and avoids real personal data. This is the initial seed size, not an employee-count limit. |
| Access | One configured HR account with Spring Security session login; protect employee, reference-data, and analytics APIs. | Credentials come from deployment configuration. HTTP-only authentication cookie, secure deployment, `SameSite=Lax`, 30-minute idle timeout, and separate CSRF cookie/header integration are approved; advanced RBAC is deferred. |
| Exclusions | Defer CSV import/export, payroll calculations, approval workflows, history, deletion, advanced RBAC, and conversion. | Keeps the MVP focused; these remain possible extensions rather than implementation commitments. |

## Approved Architecture Decisions

The user approved the [architecture proposal](ARCHITECTURE.md): a modular monolith with feature boundaries, one Spring Boot backend project, Angular assets served by Spring Boot under one public origin, and PostgreSQL. Frontend and backend production releases are coupled; development uses the Angular server with an API proxy.

Keep clear employee, compensation, analytics, infrastructure, and security responsibilities. Use controllers, services, repositories, DTOs, and straightforward mapping where needed within features. Analytics may use read-only database projections against the application's tables; this internal schema coupling is accepted.

Spring Security is approved for single-account session login/logout, protected reads and writes, and CSRF integration with Angular. HTTP-only authentication cookies, secure deployment cookies, and an explicit same-site policy are part of the design. No identity provider, account administration, or advanced RBAC is introduced.

The approved backend bootstrap uses Java 21, Spring Boot 4.1.1, Spring MVC, Maven Wrapper 3.9.11, and Boot's managed JUnit Jupiter 6/Mockito test starter. JUnit 6 replaces the original JUnit 5 preference with user approval. Explicitly select Java 21 through `JAVA_HOME` because the local global Maven otherwise uses Java 25. No global tooling or shell-profile changes are needed.

The standalone backend lives under `backend/` with package `com.acme.salarymanagement`; a future frontend can live separately in the repository. Database, validation, and security dependencies are deferred to their approved steps. Modern Angular acceptance is still unconfirmed by Incubyte. Clear boundaries support change but do not guarantee future requirements will require no refactoring.

## Approved Data Model Decisions

The user approved [DATA_MODEL.md](DATA_MODEL.md): four tables (`employees`, `departments`, `countries`, `currencies`), current salary on the employee record, controlled lookup data, required foreign keys, and no cascading lookup deletion. Department names are required, at most 100 characters, and unique without regard to case. Country and currency codes are uppercase ISO-shaped keys whose actual values require verified reference records.

Employee management owns the employee entity/repository; compensation owns monetary rules; employee application services coordinate combined updates atomically; analytics owns read-only projections. One employee version supports optimistic locking; the API requires the original browser-edit version in each full update and reports stale edits as `409`.

Start with primary-key/uniqueness indexes and employee indexes on `(department_id, id)` and `(country_code, id)`. Finalize other indexes against query contracts and measurements. Do not assume B-tree indexes accelerate arbitrary name substring searches. Use a unique ID as the final sort tie-breaker.

PostgreSQL's fixed-scale numeric coercion can round invalid input, so application writes and seeding must validate before persistence. Direct SQL bypassing validation may still round; no trigger-based precision machinery is planned. The database must explicitly reject `NaN` as well as non-positive salaries. The model is approved documentation; no executable migrations or database checks have run.

## Approved API Decisions

The user approved [API.md](API.md): JSON REST under `/api/v1`; integer strings for IDs/versions and decimal strings for money; server-managed timestamps in ISO 8601 UTC; full `PUT` updates with original-version checks; and an application-owned page envelope. Unknown/missing write fields and invalid references are rejected rather than silently accepted.

Use RFC 9457 Problem Details, including security-filter failures. Duplicate employee numbers and stale updates have distinct `409` codes. Login/session/logout and CSRF endpoints use API responses rather than HTML redirects. Login is URL-encoded and CSRF-protected; refresh the separate CSRF token after login/logout. Preserve stale forms and offer reload rather than silently retrying against the newest record.

The API is an approved design contract, not an implemented or tested service. Detailed runtime configuration and query implementation remain later steps.

## Questions Awaiting Resolution

**Incubyte clarification:** salary definition and components; required analytics and currency conversion; salary history; CSV import/export; authentication and access expectations; deployment requirements. The assessment says "AngularJS for Java"; modern Angular acceptance remains unconfirmed.

**Before affected implementation:** supported country/currency records and reference display-name lengths; any additional HR business salary maximum if required; frontend/database tool versions; exact query/index plans and consistent-snapshot implementation; timestamp initialization; environment-specific deployment/security configuration; hosting constraints; and measurable performance targets. These are open implementation/product details, not extra features to add automatically. Docker CLI availability does not establish a running database, and `psql` was not on PATH during the setup review.

## Future Expansion and Trade-offs

- Current-value editing keeps the first model small but provides no salary history or audit trail. History would require effective dates and additional persistence rules.
- Separate-currency reporting preserves correctness but cannot produce a single organization-wide monetary total. Conversion would require rates, effective dates, and rounding policy.
- Single-account session login supports the demo but provides no individual attribution. In-memory sessions are lost on restart; multiple instances would require a session strategy. Multiple roles would require an explicit authorization model and tests.
- Database pagination and aggregation support the current dataset. Larger-scale readiness must be measured; do not claim support for 100,000 or one million employees without evidence.
- CSV workflows would need validation, duplicate handling, and failure semantics. Payroll and approval workflows would require separately agreed business rules.

## Handling Incubyte's Reply

Compare the response with this register, identify impacts, and present proposed changes for user approval before modifying scope or implementation. Update both documents together where needed. Preserve genuine Git history; do not rewrite earlier decisions to imply they were known in advance.

## AI-Assisted Decision Record

Keep [AI_USAGE.md](AI_USAGE.md) to one brief line per major code change; omit routine documentation, tooling, and Git details. Design decisions belong in their relevant documents. Approval of one step does not authorize later roadmap steps.

# Assumptions and Pending Clarifications

## Status and Evidence

The user approved proceeding with the documented assumptions and saving the requirements baseline. Approval makes these project decisions usable for planning; it does not make them confirmed Incubyte requirements. The user has emailed Incubyte and reported that a response is pending.

The assessment was read in full before drafting [REQUIREMENTS.md](REQUIREMENTS.md). It confirms the HR persona, multi-country context, 10,000-employee scale, end-to-end software, relational database, seed script, tests, AI use, incremental commits, deployment, video, and supporting artifacts. It does not prescribe the detailed workflows or monetary rules below.

## Provisional Product Decisions

| Topic | Working decision | Rationale and boundary |
| --- | --- | --- |
| Salary basis | Gross annual base salary, excluding bonuses, benefits, taxes, and deductions. | Gives reporting a consistent meaning; revise if another salary definition is required. |
| Salary values | Positive, finite amounts stored as `NUMERIC(19,4)` and calculated with `BigDecimal`; validate currency precision before persistence. | Reject meaningful excess precision rather than silently round; insignificant trailing zeros may be accepted. The storage limit is not an HR policy maximum. Analytics rounding remains open. |
| Currency | Preserve each salary's original ISO currency code, referencing supported currency metadata with 0-4 minor units. | Employment country and salary currency are independent. Exact supported records remain to be selected and verified. |
| Reporting | Separate monetary results by currency; do not perform exchange-rate conversion. | Avoid comparing or adding unlike amounts. Overall headcount may span currencies. |
| Employee record | Stable internal identity; unique uppercase employee number up to 32 characters; nonblank full name up to 200 characters; department, country, current salary/currency, version, and creation/latest-change timestamps. | Current salary shares the employee row and lifecycle. Identifier syntax/editability and detailed input normalization remain open. Timestamps/version do not provide history or auditing. |
| Management | List, search, filter, sort, add, and edit, with server-side pagination. | Supports daily HR maintenance without loading the full dataset into the browser. Exact query contracts remain open. |
| Analytics | Predefined headcount and salary total, average, minimum, and maximum by department/country, with monetary groups separated by currency. | Provides a small, verifiable reporting scope. Combined filters, empty results, and rounding require defined behavior. |
| Seeding | Exactly 10,000 realistic synthetic employees from a fixed seed. | Makes initial setup reproducible and avoids real personal data. This is the initial seed size, not an employee-count limit. |
| Access | One configured HR account with Spring Security session login; protect employee and analytics APIs, including reads. | Approved project architecture; credentials come from deployment configuration. Cookie/CSRF integration and session details require implementation design; advanced RBAC is deferred. |
| Exclusions | Defer CSV import/export, payroll calculations, approval workflows, history, deletion, advanced RBAC, and conversion. | Keeps the MVP focused; these remain possible extensions rather than implementation commitments. |

## Approved Architecture Decisions

The user approved the [architecture proposal](ARCHITECTURE.md): a modular monolith with feature boundaries, one Spring Boot backend project, Angular assets served by Spring Boot under one public origin, and PostgreSQL. Frontend and backend production releases are coupled; development uses the Angular server with an API proxy.

Keep clear employee, compensation, analytics, infrastructure, and security responsibilities. Use controllers, services, repositories, DTOs, and straightforward mapping where needed within features. Analytics may use read-only database projections against the application's tables; this internal schema coupling is accepted.

Spring Security is approved for single-account session login/logout, protected reads and writes, and CSRF integration with Angular. HTTP-only authentication cookies, secure deployment cookies, and an explicit same-site policy are part of the design. No identity provider, account administration, or advanced RBAC is introduced.

Prefer Java 21, Spring Boot, modern Angular, and PostgreSQL. Version selection, dependency installation, scaffolding, API contracts, and executable implementation remain separate approval steps. Modern Angular acceptance is still unconfirmed by Incubyte. Clear boundaries support change but do not guarantee future requirements will require no refactoring.

## Approved Data Model Decisions

The user approved [DATA_MODEL.md](DATA_MODEL.md): four tables (`employees`, `departments`, `countries`, `currencies`), current salary on the employee record, controlled lookup data, required foreign keys, and no cascading lookup deletion. Department names are required, at most 100 characters, and unique without regard to case. Country and currency codes are uppercase ISO-shaped keys whose actual values require verified reference records.

Employee management owns the employee entity/repository; compensation owns monetary rules; employee application services coordinate combined updates atomically; analytics owns read-only projections. One employee version supports optimistic locking, and Step 7 must define how the original browser-edit version is carried and checked.

Start with primary-key/uniqueness indexes and employee indexes on `(department_id, id)` and `(country_code, id)`. Finalize other indexes against query contracts and measurements. Do not assume B-tree indexes accelerate arbitrary name substring searches. Use a unique ID as the final sort tie-breaker.

PostgreSQL's fixed-scale numeric coercion can round invalid input, so application writes and seeding must validate before persistence. Direct SQL bypassing validation may still round; no trigger-based precision machinery is planned. The database must explicitly reject `NaN` as well as non-positive salaries. The model is approved documentation; no executable migrations or database checks have run.

## Questions Awaiting Resolution

**Incubyte clarification:** salary definition and components; required analytics and currency conversion; salary history; CSV import/export; authentication and access expectations; deployment requirements. The assessment says "AngularJS for Java"; modern Angular acceptance remains unconfirmed.

**Before affected implementation:** identifier syntax/editability and normalization; supported country/currency records and reference display-name lengths; any additional business salary maximum; analytics rounding; pagination/sorting/search behavior; analytics grouping and empty-result behavior; original-version request and conflict-response contracts; session/cookie details and CSRF integration; hosting constraints; and measurable performance targets. These are open design decisions, not extra features to implement automatically.

## Future Expansion and Trade-offs

- Current-value editing keeps the first model small but provides no salary history or audit trail. History would require effective dates and additional persistence rules.
- Separate-currency reporting preserves correctness but cannot produce a single organization-wide monetary total. Conversion would require rates, effective dates, and rounding policy.
- Single-account session login supports the demo but provides no individual attribution. In-memory sessions are lost on restart; multiple instances would require a session strategy. Multiple roles would require an explicit authorization model and tests.
- Database pagination and aggregation support the current dataset. Larger-scale readiness must be measured; do not claim support for 100,000 or one million employees without evidence.
- CSV workflows would need validation, duplicate handling, and failure semantics. Payroll and approval workflows would require separately agreed business rules.

## Handling Incubyte's Reply

Compare the response with this register, identify impacts, and present proposed changes for user approval before modifying scope or implementation. Update both documents together where needed. Preserve genuine Git history; do not rewrite earlier decisions to imply they were known in advance.

## AI-Assisted Decision Record

See [AI_USAGE.md](AI_USAGE.md) for the consolidated prompts, decisions, corrections, and verification record. Approval of the requirements and architecture does not authorize application implementation or later roadmap steps.

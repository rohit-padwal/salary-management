# Test Coverage and Edge-Case Review

**Step 23 — 2026-10-19.** Reviewed the approved [requirements](REQUIREMENTS.md), production boundaries, and existing assertions at baseline `0f1c37b`. This is a behavioral coverage map; line/branch coverage percentages were not measured. Historical Step 22 and later complete-stack results are recorded in [VERIFICATION.md](VERIFICATION.md).

## Behavior Map

| Requirement or important boundary | Existing automated evidence | Practical limit |
| --- | --- | --- |
| Employee identity, international names, required values and length | `EmployeeInputTest`, `EmployeeApiIT`, `employee-form-validation.spec.ts`, creation/editing component tests | Client feedback supplements authoritative server validation |
| Positive exact salary, currency precision, storage limits and string transport | `SalaryRulesTest`, `EmployeeResponseTest`, `EmployeePersistenceIT`, `employee-api.spec.ts`, form validation tests | Supported currency metadata is controlled by migrations; no exchange-rate conversion |
| Database constraints and schema evolution | `FlywayMigrationIT` covers fresh/repeated migrations, required fields, references, uniqueness and numeric constraints | Production backup/restore and operational migration procedures remain delivery work |
| Pagination, literal search, combined filters and allowlisted sorting | `EmployeePageRequestTest`, `EmployeeApiIT`, `EmployeePersistenceIT`, directory/filter component tests | Query-count/entity-load checks establish bounded reads, not response-time targets |
| Full editing, no-change saves, stale forms and concurrent writes | `EmployeeApiIT`, `EmployeePersistenceIT`, `employee-edit-page.spec.ts` | Both loaded-version checks and commit-time optimistic failures are covered; manual reconciliation remains the approved UI policy |
| Per-currency analytics and exact rounding | `CompensationRollupTest`, `CompensationAnalyticsIT`, `CompensationAnalyticsApiIT`, analytics service/component tests | Weighted rollups, HALF_EVEN ties, large totals, both dimensions, empty results and one-read consistency are covered; historical reporting is outside scope |
| Reference data and unsupported selections | `ReferenceDataApiIT`, `reference-data-api.spec.ts`, creation/filter/analytics component tests | Empty catalogs stay empty; catalog errors do not invent choices |
| Authentication, CSRF and safe failures | `HrAccountPropertiesTest`, `SecurityConfigurationTest`, `SessionSecurityIT`, `ApiExceptionHandlerTest`, auth and form specs | Session restoration, token rotation, failed login/logout, fail-closed navigation and write failures are covered; deployed HTTPS/proxy behavior is pending |
| Deterministic 10,000-employee seed | `EmployeeSeedGeneratorTest`, `EmployeeSeedIT` | Fixed business data, complete load, incomplete catalogs, late rollback, concurrent attempts and rerun refusal are covered; generated IDs/timestamps are intentionally not deterministic |
| UI loading, empty/error states and asynchronous requests | Directory, analytics, creation/editing and authentication component specs | Superseded/destroyed reads, applied versus draft filters, duplicate submission guards, retry behavior and safe error messages are covered |
| Saved changes reflected across directory and reporting | `CompensationAnalyticsApiIT.employeeWritesUpdateDirectoryAndReportsWhileRejectedEditsPreserveBoth` | Repeatable real HTTP/PostgreSQL regression; the connected Chrome workflow is local verification rather than a committed browser runner |

The backend test classes are under [backend tests](../backend/src/test/java/com/acme/salarymanagement/); frontend specs sit beside features under [frontend source](../frontend/src/app/). Browser observations and manual reproduction steps are in [VERIFICATION.md](VERIFICATION.md).

## Gap Found and Fixed

The UI used JavaScript `trim()` for search submission and validation, while the API uses Java 21 `String.strip()`/`Character.isWhitespace`. These rules differ. In particular, non-breaking space (`U+00A0`), figure space (`U+2007`), narrow non-breaking space (`U+202F`), and BOM (`U+FEFF`) remain literal in the existing API contract. The UI previously removed them at search boundaries, broadening literal searches. It also counted some boundary-length names/searches differently from the API.

New regression tests failed against that behavior. The shared [text normalization helper](../frontend/src/app/shared/text-normalization.ts) now supplies the API rule to employee validation and directory/analytics searches. Validation of identifiers and names uses the normalized view; the original form values still go to the server. Salary syntax remains unmodified, including rejection of surrounding whitespace; only required-field classification uses the shared blank rule. There is no backend business-rule change.

Additional coverage checks mixed outer whitespace, preserved internal spacing, literal non-breaking characters, 200/201-code-point boundaries, identifier rejection, salary error classification, exact query transport, and actual PostgreSQL search matching. A local comparison against Java 21 matched all **1,114,112 code points**, including its 25 whitespace code points. This comparison was an ad hoc validation; the committed regression tests cover the relevant boundary examples.

Policy references: [Java 21 whitespace](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Character.html#isWhitespace(int)) and [ECMAScript trim](https://tc39.es/ecma262/multipage/text-processing.html#sec-string.prototype.trim). Recheck this compatibility boundary when upgrading the backend Java version or changing text-normalization policy.

## Verification for This Step

| Executed check | Result |
| --- | --- |
| Frontend regression run before the fix | Failed: searches lost literal outer characters; validation disagreed on whitespace boundaries |
| `npm test` | 194 tests in 17 files passed |
| `npm run build` | Passed within the existing initial-bundle budget |
| `npm run format:check` | Passed |
| `./mvnw -Dit.test=CompensationAnalyticsApiIT verify` | 161 unit tests and 64 selected API integration tests passed; zero failures, errors or skips |
| Java 21/TypeScript code-point comparison | Passed across the complete code-point range |
| Real Chrome → Spring Boot → isolated PostgreSQL | Passed: local validation rejected oversized names/invalid identifiers without writes; created names retained NBSP; directory/analytics distinguished padded and plain names and returned exactly the matching salary |

The targeted Maven command runs all unit tests and only the named integration suite. Other integration suites were not rerun in Step 23; Step 22's full result is historical evidence, not a claim of a new full run. The browser used the deterministic seed plus two temporary employees; database checks found 10,002 records and zero persisted updates. Temporary services were removed and the development database was not targeted.

## Remaining Verification Boundaries

- Browser automation is currently local/ad hoc. A committed browser runner and CI integration would require a separate tooling decision.
- Production asset serving and deep-link fallback were subsequently verified in the local Docker setup; public hosting, deployed HTTPS/session behavior, and the public workflow remain pending.
- Security review, realistic workload measurement and agreed performance targets remain the next quality/delivery work. Existing tests do not establish a load benchmark.
- Memory-only drafts/query state are approved limitations. Salary history, CSV, deletion, advanced RBAC and currency conversion remain outside the current scope.

No additional functional coverage gap was identified in this review. The matrix records the evidence and its limits for future changes without adding a coverage framework or dependencies.

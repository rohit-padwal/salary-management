# API Contracts and Business Rules

**Status:** Approved MVP project contract, subject to Incubyte clarification. The employee-creation application service/persistence is implemented and tested. The employee, analytics, reference-data, and authentication HTTP endpoints described here are not implemented or behaviorally tested. Typed service command/result records are internal application values, not HTTP DTOs.

## Conventions

Use JSON REST endpoints under `/api/v1`, with explicit request/response DTOs. Paths below include this prefix. The [requirements](REQUIREMENTS.md), [architecture](ARCHITECTURE.md), [data model](DATA_MODEL.md), and [assumptions](ASSUMPTIONS.md) define the surrounding scope.

- IDs and versions are base-10 integer strings, preserving exact values across Java and JavaScript. Record IDs are positive; versions are nonnegative. Invalid/out-of-range values are rejected.
- Monetary amounts are decimal strings, never JSON numbers. Pagination values and headcounts are JSON integers.
- Timestamps are ISO 8601 UTC strings. IDs, timestamps, and persistence versions are server-managed; an update supplies the original version as a precondition, not a value to assign.
- Successful JSON responses use `application/json`. Errors use `application/problem+json` as described below. Login uses URL-encoded form fields.
- Employee, reference-data, and analytics endpoints require authentication. API errors must not redirect to an HTML login page.
- Examples are illustrative contract examples, not captured runtime responses or evidence that endpoints exist.

## Employee Endpoints

| Method and path | Behavior | Success |
| --- | --- | --- |
| `GET /api/v1/employees` | Paginated employee search, filtering, and sorting | `200` with page envelope |
| `GET /api/v1/employees/{id}` | Retrieve an employee for viewing/editing | `200` with employee response |
| `POST /api/v1/employees` | Create an employee and current compensation atomically | `201` with employee response and `Location: /api/v1/employees/{id}` |
| `PUT /api/v1/employees/{id}` | Replace all editable fields atomically, checking the original version | `200` with updated employee response |
| `GET /api/v1/reference-data` | Retrieve supported departments, countries, and currencies | `200` with reference-data response |

There is no employee deletion, partial-update, or separate salary-update endpoint in the MVP. `PUT` updates an existing employee; a missing employee returns `404` rather than creating a record.

### Requests and Employee Response

Create requests contain `employeeNumber`, `fullName`, `departmentId`, `countryCode`, `salaryAmount`, and `currencyCode`. Update requests contain all of these fields plus `version`. Missing required fields, unknown write fields, and attempts to assign server-managed fields are rejected with `400`.

Illustrative update request:

```json
{
  "employeeNumber": "EMP-000123",
  "fullName": "Asha Rao",
  "departmentId": "3",
  "countryCode": "IN",
  "salaryAmount": "1250000.00",
  "currencyCode": "INR",
  "version": "2"
}
```

An employee response contains the six editable fields plus `id`, `version`, `createdAt`, `updatedAt`, `departmentName`, and `countryName`. It returns normalized identifiers/codes and salary formatted to the currency's minor units. The returned version is the current persisted version, which must be used for the next edit. Creation requests omit `version`; the initial version is server-managed.

### Validation and Normalization

| Field | Rule |
| --- | --- |
| `employeeNumber` | Trim outer whitespace, allow ASCII letters/digits/hyphens/underscores, normalize letters to uppercase, then require `[A-Z0-9][A-Z0-9_-]{0,31}` and uniqueness. Corrections are allowed because internal `id` stays stable. |
| `fullName` | Trim outer whitespace; preserve internal spacing/case; required and at most 200 characters. Support international names without a first/last-name split. |
| `departmentId` | Positive integer string referencing a supported department. |
| `countryCode` | Trim and uppercase; require a supported employment-country record. |
| `currencyCode` | Trim and uppercase; require a supported currency record independently of employment country. |
| `salaryAmount` | Plain decimal string with no exponent, currency symbol, or grouping separators; positive, finite, representable in `NUMERIC(19,4)`, and compatible with currency minor units. |
| `version` | Required nonnegative integer string on update, matching the original version edited by the client. |

Validate money with `BigDecimal` before persistence. Reject meaningful excess precision instead of rounding it into the column. Insignificant trailing zeros may be accepted. There is no additional HR salary ceiling beyond the current storage limit unless a later requirement establishes one.

Changing currency requires submitting amount and currency together as part of the full update. This replaces the recorded compensation; it does not perform conversion. All employee/compensation changes must succeed or roll back together. Unknown referenced records in request fields are validation errors (`400`); an unknown employee target is `404`.

### Stale Edits and Conflicts

The UI retains the version returned when the employee was loaded. On `PUT`, compare this original version with the stored version and use JPA optimistic locking to cover a concurrent change during the transaction. Either kind of stale update returns `409` with `STALE_EMPLOYEE`.

Do not silently retry against the latest version. The UI preserves the user's form and offers reloading the employee. A duplicate normalized employee number returns `409` with `EMPLOYEE_NUMBER_CONFLICT`, including when a concurrent write reaches the database unique constraint first. Do not expose database exception details.

The body version was selected instead of ETag/`If-Match` for this form-driven MVP. Future conditional-header support would need an explicit contract change.

## Listing Contract

Example: `/api/v1/employees?page=0&size=25&q=asha&countryCode=IN&sort=fullName,asc`.

| Parameter | Behavior |
| --- | --- |
| `page` | Zero-based, default `0`, nonnegative integer. |
| `size` | Default `25`, integer from `1` through `100`. |
| `q` | Trimmed, at most 200 characters; blank means absent. Case-insensitive literal substring of employee number OR full name. |
| `departmentId` | Optional exact supported department filter. |
| `countryCode` | Optional exact supported country filter, normalized as above. |
| `currencyCode` | Optional exact supported currency filter, normalized as above. |
| `sort` | One field/direction pair; default `employeeNumber,asc`; direction is `asc` or `desc`. |

Combine distinct filters with AND. Escape wildcard characters so `%` and `_` in search text are matched literally rather than treated as SQL patterns. Parameterize database queries; do not interpolate search values or unvalidated sort expressions into SQL.

The sort allowlist is `employeeNumber`, `fullName`, `departmentName`, `countryCode`, `currencyCode`, and `salaryAmount`. Append `id ASC` as the unique final tie-breaker. Salary sorting requires a selected currency filter; otherwise return `400` rather than imply amounts in unlike currencies are comparable.

Invalid parameters, unsupported filter values, or unsupported sort fields/directions return `400`. A valid filter matching no employees returns an empty page. A page beyond the result range also returns `200` with empty `items` and the actual totals.

Return an application-owned envelope containing `items` (employee responses), `page`, `size`, `totalElements`, and `totalPages`; do not expose Spring's internal page serialization as the contract. For no matching employees, `totalElements` and `totalPages` are zero.

Offset pagination is appropriate for the current dataset. A unique sort tie-breaker orders an unchanged dataset deterministically; it does not promise snapshot consistency across separate requests while employees are changing. Additional indexes follow actual query measurements rather than every sort option.

## Reference-Data Contract

Return one object containing:

- `departments`: records with `id` (string) and `name`.
- `countries`: records with `code` and `name`.
- `currencies`: records with `code`, `name`, and integer `minorUnits`.

These are the supported catalogs used by forms, filters, and validation. They are controlled through migrations; no write endpoints are included. The implemented catalogs and display-name limits are documented in [DATA_MODEL.md](DATA_MODEL.md). A supported country does not restrict an employee to one inferred currency.

## Compensation Analytics

`GET /api/v1/analytics/compensation?groupBy=department`.

`groupBy` accepts `department` or `country`, defaulting to `department`. Accept the same `q`, `departmentId`, `countryCode`, and `currencyCode` filters as listing, with identical normalization and matching. Analytics has no page or sort parameters.

Return:

- `groupBy`: the selected grouping dimension.
- `totalHeadcount`: number of matching employees across currencies.
- `currencySummaries`: one entry per represented currency, containing `currencyCode`, `headcount`, `totalSalary`, `averageSalary`, `minSalary`, and `maxSalary`.
- `groups`: one entry per represented dimension/currency combination, containing the same aggregate fields plus `groupKey` and `groupLabel`. For department grouping the key is the department ID string; for country grouping it is the country code.

Each employee contributes to one dimension/currency group and one currency summary. Never sum or average monetary values across currencies. Overall headcount may span currencies. Produce the response from a consistent database snapshot so summaries and groups agree; the query/transaction implementation is a later concern.

Use database-side aggregation. Compute averages from exact totals and headcounts, not by averaging rounded subgroup averages. Format monetary strings to the currency's minor units. Round averages once using `HALF_EVEN`; totals, minimums, and maximums remain exact. Do not narrow totals to the per-employee storage range.

For example, two salaries of `1000.00` and `1000.01` in a two-decimal currency produce total `2000.01` and displayed average `1000.00` under `HALF_EVEN`. Adding an employee in another currency creates separate monetary groups, not a combined total.

An empty result returns `totalHeadcount: 0`, `currencySummaries: []`, and `groups: []`. Omit unrepresented groups rather than inventing zero-valued salary statistics. Unknown grouping/filter values return `400`. There is no exchange-rate conversion.

## Session and CSRF Contract

| Method and path | Behavior |
| --- | --- |
| `GET /api/v1/auth/csrf` | Public; establish/refresh the CSRF token cookie; `204` with no body. |
| `POST /api/v1/auth/login` | URL-encoded `username`/`password` with CSRF protection; `204` on success. Invalid credentials return generic `401`. |
| `GET /api/v1/auth/session` | `200` with an object containing the authenticated `username`; otherwise `401`. |
| `POST /api/v1/auth/logout` | CSRF-protected logout invalidates authentication; `204` on success. |

Use Spring Security's login mechanism with API success/failure handlers, not custom password/session cryptography or HTML redirects. Credentials/password hashes come from deployment configuration and must never be committed or logged.

The authentication cookie is HTTP-only, secure over HTTPS in deployment, and uses `SameSite=Lax`. The idle session timeout is 30 minutes. Development HTTP cookie configuration must be explicit rather than weakening deployment settings.

For Angular integration, use a separate readable `XSRF-TOKEN` cookie and the `X-XSRF-TOKEN` request header. The CSRF endpoint must actually issue/materialize the token even though its response body is empty. The authentication cookie must remain inaccessible to JavaScript.

The client obtains a CSRF token before login or other mutating requests and refreshes it after successful login and logout. Spring must validate tokens for state-changing requests, including login/logout. Security-filter failures use the same problem response format as controller errors. A missing/invalid CSRF token can produce `403` before controller processing; the UI must not treat every `403` as bad credentials.

Single-account session access does not implement registration, password recovery, user administration, advanced RBAC, or individual audit attribution.

## Error Contract

Use RFC 9457 Problem Details with `application/problem+json`. Include `type`, `title`, `status`, and useful `detail`; use `instance` for the request path when provided, excluding search/query values. Add a stable application `code` and optional `errors` entries containing `field`, `code`, and `message`. Do not include rejected values, salaries, credentials, SQL details, or stack traces.

Illustrative validation response:

```json
{
  "type": "urn:salary-management:problem:validation-error",
  "title": "Invalid request",
  "status": 400,
  "detail": "Correct the indicated fields.",
  "instance": "/api/v1/employees",
  "code": "VALIDATION_ERROR",
  "errors": [
    {
      "field": "salaryAmount",
      "code": "MUST_BE_POSITIVE",
      "message": "Salary must be greater than zero."
    }
  ]
}
```

| Status | Meaning and application-code examples |
| --- | --- |
| `400` | Invalid fields, parameters, references, or malformed request: `VALIDATION_ERROR`, `INVALID_REQUEST`. |
| `401` | Missing/expired authentication or invalid login: `AUTHENTICATION_REQUIRED`, `INVALID_CREDENTIALS`. |
| `403` | Invalid CSRF token or denied access: `CSRF_INVALID`, `ACCESS_DENIED`. |
| `404` | Employee resource not found: `EMPLOYEE_NOT_FOUND`. |
| `409` | Duplicate employee number or stale update: `EMPLOYEE_NUMBER_CONFLICT`, `STALE_EMPLOYEE`. |
| `415` | Unsupported request content type: `UNSUPPORTED_MEDIA_TYPE`. |
| `500` | Unexpected failure: `INTERNAL_ERROR`, with a generic public message. |

Problem type identifiers are stable URI references; the sample uses a non-HTTP URN rather than adding problem-document endpoints. Never imply a business failure was successful, or expose implementation exceptions as the public error contract.

## Planned Verification and Future Expansion

When implementation is approved, use Red -> Green -> Refactor for business rules and verify:

- Normalization, unique employee-number conflicts, international names, missing/unknown fields, and invalid references.
- Plain decimal-string handling, meaningful excess precision, trailing zeros, storage limits, and exact monetary round trips.
- Literal wildcard search, AND filters, paging boundaries, allowed sorts, and rejection of mixed-currency salary sorting.
- Atomic updates, original-version stale forms, and races detected by optimistic locking/unique constraints.
- Independently calculated analytics, multiple currencies, empty results, and `HALF_EVEN` ties without double rounding.
- Unauthenticated reads, generic login failure, session timeout, CSRF issuance/refresh, login/logout, and consistent security error bodies.

These are planned application tests, not executed checks. Documentation verification covers local links, example JSON syntax, independent decimal arithmetic, formatting, scope consistency, and secret patterns.

DTOs and the `/api/v1` boundary allow future evolution. Partial updates, ETags, cursor pagination, additional analytics, history, and RBAC require separately approved contracts. They are not implemented or promised by the current API.

## References

- [RFC 9457: Problem Details for HTTP APIs](https://www.rfc-editor.org/rfc/rfc9457.html)
- [Spring Security CSRF and SPA integration](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html)
- [Angular security and HttpClient CSRF integration](https://angular.dev/best-practices/security)
- [Java 21 rounding modes](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/math/RoundingMode.html)

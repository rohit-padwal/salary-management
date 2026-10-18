# API Contracts and Business Rules

**Status:** Approved MVP project contract, subject to Incubyte clarification. Employee creation (`POST /api/v1/employees`), retrieval/editing (`GET`/`PUT /api/v1/employees/{id}`), reference-data lookup (`GET /api/v1/reference-data`), and authentication/session/CSRF endpoints are implemented and tested. Paginated employee search is implemented; filtering, selectable sorting, and analytics remain unimplemented. Analytics paths are protected by authentication. Dedicated HTTP DTOs map typed application values to the approved string contract.

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

Creation, retrieval, full editing by ID, and reference-data lookup are currently implemented in this table. Paginated listing and literal case-insensitive search are also implemented. The creation `Location` can be followed to retrieve the record. Filter/selectable-sort parts of the listing contract remain future work, as detailed below.

Retrieval requires authentication but no CSRF header. Parse path IDs with the same positive-`Long` helper used for creation's department ID: ASCII digits only, no signs/whitespace/decimal points/exponents, and no overflow. Leading zeros are accepted. Invalid values return `400 VALIDATION_ERROR` with field `id`; a valid missing ID returns `404 EMPLOYEE_NOT_FOUND`. Authentication is checked before parsing/lookup. A successful read returns the same response representation without changing salary, version, or timestamps.

### Requests and Employee Response

Create requests contain `employeeNumber`, `fullName`, `departmentId`, `countryCode`, `salaryAmount`, and `currencyCode`. Update requests contain all of these fields plus `version`. Missing required fields, unknown write fields, and attempts to assign server-managed fields are rejected with `400`.

Creation accepts a JSON object with six string-valued fields; numbers/booleans are not coerced into strings. Trailing JSON tokens are rejected. Jakarta Bean Validation checks required values; application rules handle normalization, names, references, and money. Department IDs must parse exactly as positive `Long` values, without signs, decimal points, exponents, or surrounding whitespace. HTTP response mapping explicitly formats IDs/versions, currency-scale decimal amounts, and UTC timestamps as strings.

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

PUT accepts exactly the six editable string fields and a nonnegative integer-string `version` within the `Long` range; zero and leading zeros are accepted. Signs, whitespace, exponents, decimals, overflow, numeric JSON values, and server-managed write fields are rejected. Input normalization, reference checks, currency precision, and employee-number uniqueness use the same rules as creation. The client version is checked against the managed entity and is never assigned to its version field.

An effective update returns `200` with a new persisted version/update timestamp, preserving ID and creation timestamp. A normalized no-change submission returns the existing representation without advancing metadata. Its version is still verified through commit using an optimistic lock, so a concurrent writer can produce `409` even when the submitted values cause no write. Flush-time and commit-time optimistic failures use the same `STALE_EMPLOYEE` response. No automatic retry or upsert is performed. A valid missing target returns `404 EMPLOYEE_NOT_FOUND`.

## Listing Contract

### Implemented Pagination and Search

Authenticated `GET /api/v1/employees` currently accepts `page`, `size`, and optional `q`, once each. Pagination defaults are `0` and `25`; page is within `0..2147483647`, and size within `1..100`. For page/size, parse ASCII digits only, accepting leading zeros and rejecting empty values, signs, whitespace, decimals, exponents, non-ASCII digits, and overflow. Return `400 VALIDATION_ERROR` for invalid/repeated parameters. Unsupported parameters, including reference filters and `sort`, return a generic query-field error without echoing their names/values. Authentication is checked before query validation; GET requires no CSRF token.

`q` is stripped of surrounding Unicode whitespace; missing/blank values mean no search. Its normalized length is at most 200 Unicode code points, matching employee-name length semantics. Excess length returns a `q` field error with `TOO_LONG`; null characters return `INVALID_FORMAT`; repeated values return `SINGLE_VALUE_REQUIRED`. Preserve internal spacing and search the whole literal substring of employee number OR full name. Case conversion for both fields and the bound pattern happens in PostgreSQL using the database collation. Match each employee once even if both fields match.

The count and content queries share one JPQL predicate and use the same bound pattern. LIKE uses `!` as its explicit escape character: escape literal `!` first, then `%` and `_`, before adding surrounding substring wildcards. Backslashes and apostrophes remain literal parameter data. Empty search takes the existing unfiltered path. Example: `/api/v1/employees?q=asha&page=0&size=25`. Totals describe matching employees only; no matches return empty items and zero totals.

The implemented response envelope contains `items`, `page`, `size`, `totalElements`, and `totalPages`. Items reuse the full employee response; metadata values are JSON integers. Ordering is fixed at `employeeNumber ASC, id ASC`. Empty databases have empty items and zero totals; out-of-range pages have empty items and actual totals. Counts and page offsets use long arithmetic. The service counts first and skips page fetching when its offset is beyond that count, including very large valid page numbers. In-range pages are bounded database reads with lookup labels fetched together.

The read-only transaction uses the existing default isolation. A concurrent write between the count and page queries may change the visible rows, so the response is not a guaranteed snapshot; separate page requests likewise do not share a snapshot. Filters, selectable sorting, and new indexes remain outside this increment.

### Full Approved Listing Target — Remaining Parameters Are Planned

Future example: `/api/v1/employees?page=0&size=25&q=asha&countryCode=IN&sort=fullName,asc`. This example is currently rejected because country filtering and selectable sorting are not implemented.

| Parameter | Behavior |
| --- | --- |
| `page` | Zero-based, default `0`, integer from `0` through `2147483647`. |
| `size` | Default `25`, integer from `1` through `100`. |
| `q` | Strip surrounding Unicode whitespace; at most 200 Unicode code points; blank means absent. Case-insensitive literal substring of employee number OR full name. |
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

The implemented authenticated `GET /api/v1/reference-data` returns `200 application/json` with one object containing:

- `departments`: records with `id` (string) and `name`.
- `countries`: records with `code` and `name`.
- `currencies`: records with `code`, `name`, and integer `minorUnits`.

Order departments by name ascending, then ID ascending; order countries and currencies by code ascending. Name ordering follows the database collation. Empty catalogs are empty arrays, never null or a missing property. Department IDs are exact strings, including values beyond JavaScript's safe integer range; minor units are JSON integers. GET requires a session but no CSRF cookie/header. Unauthenticated requests return `401 AUTHENTICATION_REQUIRED` without catalog data or an HTML redirect.

The read-only query service fetches current database values through existing repositories; it does not hardcode catalogs or cache responses. Responses retain the existing security `no-store` headers. No new dependencies or schema changes are needed. POST/PUT/DELETE have no handlers and return `405` after authentication/CSRF checks.

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

The implementation uses external `HR_USERNAME` and raw BCrypt `HR_PASSWORD_HASH` configuration, with no default account. Malformed/missing values prevent web startup. Cost 12 is recommended; hashes must have cost at least 10. Default cookies are Secure; the explicit `local` profile allows loopback HTTP development. Spring Security's SPA CSRF support handles raw header tokens and token clearing/refresh. Unsupported login content types produce `415` after CSRF validation. HTTP Basic and request caching/HTML redirects are disabled.

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
| `404` | Unmapped HTTP resource: `RESOURCE_NOT_FOUND`; distinct from employee lookup's business error. |
| `405` | Unsupported method on a mapped resource: `METHOD_NOT_ALLOWED`, retaining the `Allow` header. |
| `406` | Unsupported response media type: `NOT_ACCEPTABLE`. |
| `409` | Duplicate employee number or stale update: `EMPLOYEE_NUMBER_CONFLICT`, `STALE_EMPLOYEE`. |
| `415` | Unsupported request content type: `UNSUPPORTED_MEDIA_TYPE`. |
| `500` | Unexpected failure: `INTERNAL_ERROR`, with a generic public message. |

Problem type identifiers are stable URI references; the sample uses a non-HTTP URN rather than adding problem-document endpoints. Never imply a business failure was successful, or expose implementation exceptions as the public error contract.

Creation validation responses contain safe field/code/message entries. Multiple missing fields are returned in field-name order; business validation may return the first failing rule. Malformed JSON, unknown fields, and wrong JSON types use a generic `INVALID_REQUEST` response rather than parser exception text. Unhandled failures use generic `INTERNAL_ERROR`; application logging records the exception type without the request body or database exception message. Security-filter errors continue to be handled before controller processing.

## Verification and Future Expansion

Implemented tests cover:

- Normalization, unique employee-number conflicts, international names, missing/unknown fields, and invalid references.
- Plain decimal-string handling, meaningful excess precision, trailing zeros, storage limits, and exact monetary round trips.
- Atomic updates, original-version stale forms, unchanged submissions, and races detected by optimistic locking/unique constraints, including commit-time verification.
- Unauthenticated reads, generic login failure, session timeout, CSRF issuance/refresh, login/logout, and consistent security error bodies.
- Complete ordered reference catalogs, exact string IDs/integer minor units, current database values, empty catalogs, read access without CSRF, and absence of catalog write handlers.

Pagination tests cover defaults, traversal/totals, empty/out-of-range pages, parameter validation, and bounded employee/lookup loading without writes. Search tests cover both fields/OR matching, case conversion, literal wildcards/escapes/quotes, blank normalization, Unicode length, matching totals, and bounded reads. Filter/selectable-sorting tests and independently calculated analytics with separate currencies, empty results, and `HALF_EVEN` rounding remain planned. Their contracts do not imply those features are implemented.

DTOs and the `/api/v1` boundary allow future evolution. Partial updates, ETags, cursor pagination, additional analytics, history, and RBAC require separately approved contracts. They are not implemented or promised by the current API.

## References

- [RFC 9457: Problem Details for HTTP APIs](https://www.rfc-editor.org/rfc/rfc9457.html)
- [Spring Security CSRF and SPA integration](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html)
- [Angular security and HttpClient CSRF integration](https://angular.dev/best-practices/security)
- [Java 21 rounding modes](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/math/RoundingMode.html)

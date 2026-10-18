# API Contracts and Business Rules

Implemented JSON REST under `/api/v1`; [assumptions](ASSUMPTIONS.md) distinguish approved product choices from assessment requirements. Examples below illustrate the contract, not captured responses.

## Conventions

- IDs/versions and money are JSON **strings**; counts/page metadata are integers. IDs are positive Long values; versions are nonnegative Long values. ASCII digits and leading zeros are accepted; signs, surrounding whitespace, exponents, decimals, and overflow are rejected.
- Timestamps are ISO 8601 UTC strings. ID, timestamps, and persistence version are server-managed; update `version` is a precondition.
- JSON writes reject unknown/server-managed fields, trailing tokens, and number/boolean-to-string coercion. Required-field errors are ordered by field; business validation may report the first failed rule.
- Employee, reference-data, and analytics APIs require a session. Mutations also require CSRF. Authentication is checked before parameter/target validation. Security failures return JSON problems without login redirects or Basic challenges.

## Endpoints

| Method and path | Success / behavior |
| --- | --- |
| `GET /api/v1/employees` | `200` page envelope; directory criteria below |
| `GET /api/v1/employees/{id}` | `200` employee; malformed ID `400`, valid missing ID `404` |
| `POST /api/v1/employees` | `201` employee and `Location: /api/v1/employees/{id}` |
| `PUT /api/v1/employees/{id}` | `200` employee; full atomic replacement, original version required |
| `GET /api/v1/reference-data` | `200` supported catalogs |
| `GET /api/v1/analytics/compensation` | `200` compensation report |
| `GET /api/v1/health` | Public status-only readiness: `200 {"status":"UP"}`; `503` STARTING or DOWN |

No deletion, PATCH, upsert, separate salary endpoint, or catalog-write handlers are provided. Reads do not change employee metadata and require no CSRF header.

## Employee Writes and Response

Create requires six strings; update requires those plus the original `version`:

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

Creation omits `version`. Choose actual supported catalog values. The response contains these fields plus `id`, `createdAt`, `updatedAt`, `departmentName`, and `countryName`; salary is formatted to currency minor units.

| Field | Rule |
| --- | --- |
| `employeeNumber` | Strip outer Java whitespace, uppercase ASCII letters; `[A-Z0-9][A-Z0-9_-]{0,31}`; normalized value unique. |
| `fullName` | Strip outer Java whitespace; preserve internal spacing/case; nonblank, at most 200 Unicode code points; no null characters. |
| `departmentId` | Positive integer string referencing a supported department. |
| `countryCode` / `currencyCode` | Strip outer whitespace, require ASCII code shape, uppercase, resolve supported catalog. Choices are independent. |
| `salaryAmount` | Positive plain decimal, no surrounding whitespace, plus sign, exponent, symbol, or grouping separator; strictly below `1000000000000000`; meaningful precision must fit selected currency. |
| `version` | Original nonnegative integer string on PUT; never assigned to the entity version. |

Money is validated with `BigDecimal` before persistence; meaningful excess precision is rejected, insignificant trailing zeros accepted. There is no additional business salary ceiling. Currency and amount are replaced together without conversion.

Successful effective edits advance version/update time, preserving ID/creation time. Normalized no-change submissions preserve metadata but still verify the original version through commit. Validation/conflict failures roll back the full replacement. Duplicates return `409 EMPLOYEE_NUMBER_CONFLICT`; stale forms or concurrent optimistic failures return `409 STALE_EMPLOYEE`; missing targets return `404 EMPLOYEE_NOT_FOUND`. No automatic write replay, forced version replacement, or merge is supplied.

## Directory Criteria

Example: `/api/v1/employees?page=0&size=25&q=asha&countryCode=IN&sort=fullName,asc`.

| Parameter | Contract |
| --- | --- |
| `page` | Default `0`; ASCII digits, `0..2147483647`. |
| `size` | Default `25`; ASCII digits, `1..100`. |
| `q` | Optional literal case-insensitive substring of employee number OR full name; strip outer Java whitespace; blank means absent; max 200 code points; null characters rejected. |
| `departmentId` | Optional positive supported department ID. |
| `countryCode` / `currencyCode` | Optional supported code, normalized as on writes. Omitted means unrestricted; supplied blank is invalid. |
| `sort` | Exactly one case-sensitive `field,direction`; default `employeeNumber,asc`; direction `asc` or `desc`. |

Each parameter may occur once, including identical repeats. Unsupported names return `query / UNSUPPORTED_PARAMETER`; invalid/repeated values return `400 VALIDATION_ERROR`. Pagination rejects signs, whitespace, decimals, exponents, non-ASCII digits, and overflow; leading zeros are accepted.

Search preserves the full literal substring and internal spacing. Java 21 `Character.isWhitespace` governs outer stripping: NBSP (`U+00A0`), figure space (`U+2007`), narrow NBSP (`U+202F`), and BOM (`U+FEFF`) remain literal. PostgreSQL case conversion/collation controls matching. `%`, `_`, `!`, backslashes, and apostrophes are literal input, not query structure.

Catalog filters combine with AND around the parenthesized name/number OR. Provided catalogs are validated even with no matching employees. Invalid references return `UNKNOWN_REFERENCE`; repeated parameters `SINGLE_VALUE_REQUIRED`; supplied blank catalog filters `REQUIRED`.

Sort fields: `employeeNumber`, `fullName`, `departmentName`, `countryCode`, `currencyCode`, `salaryAmount`. Typed aliases map to fixed properties; arbitrary property paths/functions are rejected. Every ordering appends `id ASC`, including descending primary order. Salary sorting requires an explicit valid currency filter (`sort / CURRENCY_REQUIRED` otherwise), even for empty results. Country never implies currency; salary order is numeric within one currency.

The response is `{ "items": [...], "page": 0, "size": 25, "totalElements": 10000, "totalPages": 400 }`, with full employee representations. Empty results have zero totals; out-of-range pages have empty items and actual totals. Database-side bounded pages avoid loading the directory. Stable tie ordering applies to unchanged data; count/content and separate requests do not share a guaranteed snapshot during concurrent writes.

## Reference Data

`GET /api/v1/reference-data` returns:

- `departments`: string `id`, string `name`; ordered by database name then ID.
- `countries`: string `code`, string `name`; ordered by code.
- `currencies`: string `code`, string `name`, integer `minorUnits`; ordered by code.

All properties exist; empty catalogs are empty arrays. IDs remain exact beyond JavaScript's safe integer range. Catalogs come from current database records managed by migrations, without caching/admin endpoints. Responses use existing security cache-prevention headers.

## Compensation Analytics

`GET /api/v1/analytics/compensation` accepts `groupBy` (`department` default, or `country`) plus the directory's `q`/three catalog filters. Group names are exact lowercase values. Repeated/unknown/blank parameters are rejected; `page`, `size`, and `sort` are unsupported.

The response contains `groupBy`, integer `totalHeadcount`, `currencySummaries`, and `groups`. Every summary/group has `currencyCode`, integer `headcount`, and string `totalSalary`, `averageSalary`, `minSalary`, `maxSalary`. Groups also have string `groupKey`/`groupLabel`; department keys are exact ID strings, country keys are codes.

One grouped database read supplies both result levels. Each employee contributes once to a dimension/currency group and a currency summary. Headcount can span currencies; money cannot. Averages use exact total/count and one HALF_EVEN rounding step at currency precision, never averaged rounded subgroups. Totals/min/max remain exact and totals can exceed employee storage limits. Example: `1000.00 + 1000.01` gives total `2000.01`, average `1000.00` in a two-decimal currency.

Groups order by numeric department ID or country code, then currency; summaries by currency. Empty data yields headcount zero and empty arrays, without invented zero salary statistics. No conversion or historical reporting is implemented.

## Session and CSRF

| Endpoint | Contract |
| --- | --- |
| `GET /api/v1/auth/csrf` | Public `204`; materialize/refresh readable `XSRF-TOKEN` cookie. |
| `POST /api/v1/auth/login` | URL-encoded `username`/`password` with CSRF; `204` success, generic `401` invalid credentials. |
| `GET /api/v1/auth/session` | `200 {"username":"..."}` for authenticated session; otherwise `401`. |
| `POST /api/v1/auth/logout` | CSRF-protected session invalidation; `204`. |

Obtain CSRF before login/mutations; send cookie plus `X-XSRF-TOKEN` header. Refresh after successful login/logout because tokens rotate. The authentication cookie is HTTP-only, SameSite=Lax, Secure by default, and expires after 30 idle minutes; `local` explicitly permits HTTP. No browser token storage, Basic authentication, registration, or individual account administration.

Missing/invalid CSRF returns `403`, including login/logout. Unsupported login content type returns `415` after CSRF checks. Web startup requires configured HR username/raw BCrypt hash; the local demo configuration supplies public credentials explicitly. See [configuration](../backend/TECHNICAL_DOCUMENTATION.md#configuration).

## Errors

RFC 9457 `application/problem+json` includes `type`, `title`, `status`, `detail`, path-only `instance` where supplied, stable `code`, and optional `errors` entries with `field`, `code`, `message`. No rejected values, credentials, SQL, query strings, or stack traces are returned. Problem types use stable URNs such as `urn:salary-management:problem:validation-error`.

| Status | Codes |
| --- | --- |
| 400 | `VALIDATION_ERROR`, `INVALID_REQUEST` (including malformed/unknown-field/wrong-type JSON) |
| 401 | `AUTHENTICATION_REQUIRED`, `INVALID_CREDENTIALS` |
| 403 | `CSRF_INVALID`, `ACCESS_DENIED` |
| 404 | `EMPLOYEE_NOT_FOUND`, or unmapped `RESOURCE_NOT_FOUND` |
| 405 | `METHOD_NOT_ALLOWED`, preserving `Allow` |
| 406 | `NOT_ACCEPTABLE` |
| 409 | `EMPLOYEE_NUMBER_CONFLICT`, `STALE_EMPLOYEE` |
| 415 | `UNSUPPORTED_MEDIA_TYPE` |
| 500 | Generic `INTERNAL_ERROR` |

Readiness `503` is a status-only health response, not an employee API problem. Clients render known local field/code messages rather than arbitrary server text. See [coverage evidence](COVERAGE_REVIEW.md) for contract tests and limits; partial updates, ETags, cursors, and new reports require separately approved contracts.

References: [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457.html), [Spring SPA CSRF](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html), [Angular security](https://angular.dev/best-practices/security).

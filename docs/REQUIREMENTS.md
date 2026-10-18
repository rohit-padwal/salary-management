# ACME Employee Salary Management — Requirements

**Status:** Approved product baseline; provisional decisions await Incubyte clarification.

## Problem, Goal, and User — Confirmed

ACME's HR team manages salary data for 10,000 employees across multiple countries in Excel. Provide an end-to-end web application enabling the **HR Manager** to maintain salary data and understand organizational compensation.

## Assessment Obligations — Confirmed

Deliver a backend, UI, relational database, seed script for 10,000 employees, meaningful deterministic unit tests, deployed application, and video demonstration. Use AI intentionally, preserve incremental Git history, and include supporting engineering artifacts. Prepare this document before building.

## Functional Scope — Provisional

- List employees with server-side pagination; search by identifier/name, filter by department/country, and sort supported fields.
- Add employees and edit employee details and current salary.
- Maintain a unique employee identifier, name, department, employment country, salary amount, and ISO currency code.
- Treat salary as **positive gross annual base salary**, excluding bonuses, benefits, taxes, and deductions.
- Preserve original currencies. Report headcount and salary total, average, minimum, and maximum by department/country; separate monetary results by currency.
- Validate inputs, reject invalid changes, show actionable errors, and reflect saved changes in listings and analytics.
- Generate exactly 10,000 realistic synthetic employee records reproducibly from a fixed seed.

## Non-Functional Requirements — Proposed

Use exact decimal money calculations, database constraints, and transactional updates. Paginate and aggregate in the database. Provide clear loading, empty, and error states. Protect sensitive operations and credentials; select simple demo access during architecture planning. Keep modules readable and business rules testable. Agree measurable performance targets before acceptance testing.

## Exclusions and Reasons — Provisional

Defer CSV import/export, payroll calculations, approval workflows, salary history, deletion, advanced RBAC, and currency conversion to focus delivery on current compensation management. Distributed infrastructure is unnecessary for present scope.

## Assumptions and Open Questions

Incubyte's response is pending. Confirm salary definitions, reporting expectations, modern Angular acceptance, history/import/export needs, and deployment/access expectations. Define supported currencies, precision, rounding, and demo access before implementing affected behavior. Track details in [ASSUMPTIONS.md](ASSUMPTIONS.md).

## Success Criteria — Proposed

The deployed application supports agreed workflows with 10,000 seeded employees; valid changes persist; invalid changes are rejected; analytics match independently calculated fixtures; monetary results never mix currencies. Core tests pass, setup is reproducible, and delivery artifacts are complete.

## Future Expansion

Preserve clear employee, compensation, analytics, infrastructure, and access-control boundaries. Add history, auditing, RBAC, data exchange, and conversion only when approved.

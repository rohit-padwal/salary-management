# Assumptions and Pending Clarifications

## Status and Evidence

The user approved proceeding with the documented assumptions and saving the requirements baseline. Approval makes these project decisions usable for planning; it does not make them confirmed Incubyte requirements. The user has emailed Incubyte and reported that a response is pending.

The assessment was read in full before drafting [REQUIREMENTS.md](REQUIREMENTS.md). It confirms the HR persona, multi-country context, 10,000-employee scale, end-to-end software, relational database, seed script, tests, AI use, incremental commits, deployment, video, and supporting artifacts. It does not prescribe the detailed workflows or monetary rules below.

## Provisional Product Decisions

| Topic | Working decision | Rationale and boundary |
| --- | --- | --- |
| Salary basis | Gross annual base salary, excluding bonuses, benefits, taxes, and deductions. | Gives reporting a consistent meaning; revise if another salary definition is required. |
| Salary values | Amounts must be positive. | Reject zero and negative values; maximum, precision, and rounding rules remain open. |
| Currency | Preserve each salary's original ISO currency code. | Employment country and salary currency are independent. Supported codes remain to be selected. |
| Reporting | Separate monetary results by currency; do not perform exchange-rate conversion. | Avoid comparing or adding unlike amounts. Overall headcount may span currencies. |
| Employee record | Unique identifier, name, department, employment country, current salary, and currency. | Supports the proposed workflows; identifier format and field constraints remain open. |
| Management | List, search, filter, sort, add, and edit, with server-side pagination. | Supports daily HR maintenance without loading the full dataset into the browser. Exact query contracts remain open. |
| Analytics | Predefined headcount and salary total, average, minimum, and maximum by department/country, with monetary groups separated by currency. | Provides a small, verifiable reporting scope. Combined filters, empty results, and rounding require defined behavior. |
| Seeding | Exactly 10,000 realistic synthetic employees from a fixed seed. | Makes initial setup reproducible and avoids real personal data. This is the initial seed size, not an employee-count limit. |
| Access | Protect sensitive salary-management operations; select a simple demo-access mechanism during architecture planning. | Authentication implementation is undecided; advanced RBAC is deferred. |
| Exclusions | Defer CSV import/export, payroll calculations, approval workflows, history, deletion, advanced RBAC, and conversion. | Keeps the MVP focused; these remain possible extensions rather than implementation commitments. |

## Architecture Direction, Not a Completed Design

Prefer Java, Spring Boot, modern Angular, and PostgreSQL in a modular monolith, following the user's direction. No application scaffolding, dependency selection, or schema design has been implemented or approved by this documentation step.

Keep responsibilities clear between employee management, compensation, analytics, persistence/infrastructure, and access control. Use straightforward boundaries rather than speculative interfaces or distributed services. These boundaries should allow later features to be introduced, but do not guarantee that future requirements will require no refactoring.

## Questions Awaiting Resolution

**Incubyte clarification:** salary definition and components; required analytics and currency conversion; salary history; CSV import/export; authentication and access expectations; deployment requirements. The assessment says "AngularJS for Java"; modern Angular acceptance remains unconfirmed.

**Before affected implementation:** identifier rules; supported countries/currencies; salary precision, maximum, and rounding; field lengths and required values; pagination/sorting/search behavior; analytics grouping and empty-result behavior; update-conflict handling; demo access; hosting constraints; and measurable performance targets. These are open design decisions, not extra features to implement automatically.

## Future Expansion and Trade-offs

- Current-value editing keeps the first model small but provides no salary history or audit trail. History would require effective dates and additional persistence rules.
- Separate-currency reporting preserves correctness but cannot produce a single organization-wide monetary total. Conversion would require rates, effective dates, and rounding policy.
- Simple protected access can support a demo; multiple roles would require an explicit authorization model and tests.
- Database pagination and aggregation support the current dataset. Larger-scale readiness must be measured; do not claim support for 100,000 or one million employees without evidence.
- CSV workflows would need validation, duplicate handling, and failure semantics. Payroll and approval workflows would require separately agreed business rules.

## Handling Incubyte's Reply

Compare the response with this register, identify impacts, and present proposed changes for user approval before modifying scope or implementation. Update both documents together where needed. Preserve genuine Git history; do not rewrite earlier decisions to imply they were known in advance.

## AI-Assisted Decision Record for This Step

- The assistant read all three assessment pages and proposed a one-page scope draft and clarification questions.
- The user reported contacting Incubyte, supplied provisional assumptions, and authorized proceeding without waiting for a reply.
- The assistant inspected the empty repository and presented the revised requirements and a two-file documentation plan.
- The user approved continuation and supplied the configured GitHub remote. This step saves the baseline and assumptions only; verification and Git outcomes are reported from actual commands.

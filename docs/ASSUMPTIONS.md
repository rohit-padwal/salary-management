# Assumptions and Pending Clarifications

The user approved proceeding with these assumptions and the [requirements baseline](REQUIREMENTS.md). They are project decisions, not confirmed additions to Incubyte's assessment. The user reported sending clarification questions; a reply remains pending.

The original assessment establishes the HR persona, multi-country context, 10,000-employee scale, end-to-end application, relational database, seed script, tests, AI use, incremental commits, deployment, video, and supporting artifacts. Detailed workflows and monetary rules below were selected for the MVP.

## Working Product Decisions

| Topic | Decision and boundary |
| --- | --- |
| Salary | One current gross annual base salary per employee; exclude bonuses, deductions, tax, and payroll calculations. |
| Currency | Explicit supported ISO currency, independent of employment country; exact decimals with currency-specific precision. No conversion or cross-currency monetary total. |
| Employee management | Add, view, and fully edit employee number, full name, department, country, salary, and currency. No deletion or history. |
| Directory | Literal case-insensitive name/number search; independent department/country/currency filters; server pagination and allowlisted sorting. Salary sorting requires an explicit currency. |
| Analytics | Headcount and total/average/minimum/maximum salary by department or country, separated by currency; same search/filter semantics as the directory. Each report derives from one captured grouped read. |
| Seed | Exactly 10,000 realistic synthetic records initially; deterministic business data, not generated IDs/timestamps. No employee-count limit. |
| Access | One configured HR account, cookie session, CSRF protection, 30-minute idle timeout. No individual identities, advanced RBAC, or audit attribution. |
| UI state | Explicit Apply; drafts/query state are memory-only. Stale or uncertain edits require explicit discard/reload; no automatic mutation replay. |
| Reviewer setup | Local Docker demo with separate frontend/backend/database containers, public demo credentials, loopback ports, and seed-once startup. Production access remains a separate decision. |

Approved technical choices and their rationale are maintained in [ARCHITECTURE.md](ARCHITECTURE.md), [DATA_MODEL.md](DATA_MODEL.md), and [API.md](API.md), rather than repeated here. Modern Angular was explicitly selected by the user; the assessment's wording “AngularJS for Java” still needs Incubyte confirmation.

## Questions for Incubyte

1. Is salary annual base pay, monthly pay, or total compensation? Are components required?
2. Which reports are mandatory? Is currency conversion expected, and with what rates/date/rounding policy?
3. Are salary history, CSV import/export, or approval workflows required?
4. What authentication/access controls and individual audit evidence are expected?
5. Are there deployment-provider constraints or required public-demo access arrangements?
6. Is modern Angular acceptable in place of the assessment's AngularJS wording?

Before affected work, also agree measurable performance targets, any business salary ceiling beyond storage limits, and production security/hosting configuration. Query-count tests do not establish large-scale latency guarantees.

## Deferred Scope and Change Handling

History requires effective dates and new persistence/report semantics. Conversion needs an exchange-rate model. Individual users/RBAC need identity and authorization rules; multiple backend instances need a session strategy. CSV needs duplicate/error/partial-failure semantics. These are extension options, not commitments.

Compare Incubyte's reply with this register, present impacts for approval, and update affected documents together. Preserve genuine decision and Git history. [AI_USAGE.md](AI_USAGE.md) records only selected major code changes in one brief line each; routine documentation/tooling/Git updates need no entry.

# AI Usage and Engineering Decisions

## Collaboration Method

The user directs scope and approves each step. The assistant analyzes alternatives, proposes changes, and implements only the approved step. Approval does not carry forward to subsequent steps. The user later authorized automatic commit/push for completed, approved steps, with checks and explicit staging of relevant files. No history rewriting or force-pushing is authorized.

This is a concise record of meaningful exchanges, not a complete prompt transcript. Proposed decisions are distinguished from user-approved decisions and confirmed assessment requirements. Application behavior and test results must come from actual implementation and executed checks.

## Assessment Discovery

**User direction:** Read the full assessment, distinguish mandates from proposed features, draft a one-page requirements document in conversation, and stop before creating files or application code.

**AI contribution:** Read and visually reviewed all three assessment pages. Identified the required HR salary-management application, multi-country/10,000-employee context, relational database, seed script, tests, AI use, deployment, video, supporting artifacts, and incremental commits. Proposed a focused MVP and clarification questions.

**Important correction:** The assessment says "AngularJS for Java"; the user's preferred modern Angular is not yet confirmed by Incubyte. Detailed search/edit/reporting workflows, deterministic synthetic seeding, and specific monetary rules are project choices rather than individually mandated assessment features.

**Outcome:** Analysis and a requirements draft only. No application tests, files, dependencies, commits, or deployment were created during that discovery turn.

## Requirements and Assumptions Baseline

**User direction:** Proceed while Incubyte's response is pending, using positive gross annual base salary, original ISO currencies, separate-currency reporting, core employee workflows, predefined analytics, and deterministic synthetic seeding. Defer optional workflows and propose protected demo access. Inspect existing progress before changes.

**AI contribution:** Inspected the repository, found an empty Git repository on `main`, and presented a revised requirements draft plus an assumptions register for review. Reported that no remote was configured at that inspection.

**Approval and change:** The user approved continuation and reported adding the GitHub remote. The assistant verified it, created `docs/requirements`, and saved only `REQUIREMENTS.md` and `ASSUMPTIONS.md`.

**Actual verification:** The requirements contained 401 whitespace-delimited words. Python checks passed for required sections, local links, final newlines, trailing whitespace, a <=500-word editorial budget, expected files, and secret patterns. `git diff --cached --check` passed. These checks establish concise Markdown content; they do not prove printed pagination or application behavior.

**Git evidence:** Commit `603eccc8b6a2d553bf2ec0df5b3006acce6d5807` (`docs: define salary management requirements and assumptions`) was pushed to `origin/docs/requirements`. A remote ref check matched the local commit, and the working tree was clean. No application tests or builds existed to run.

## Step 5: Architecture Selection and Documentation

**User direction:** Continue to architecture review, then approve the presented proposal and three-file documentation step.

**AI contribution:** Compared a conventional layered monolith, modular monolith, and microservices. Proposed feature boundaries, one deployment serving Angular and REST under one origin, PostgreSQL, and a single HR account using Spring Security session login. Consulted official Spring Boot, Spring Security, and Angular documentation; references are recorded in [ARCHITECTURE.md](ARCHITECTURE.md).

**Approved decisions:** Modular monolith; feature-oriented responsibilities; DTO API boundaries; manual mapping initially; read-only analytics projections; single-origin packaging; protected employee/analytics reads and writes; session login/logout and CSRF integration. Spring Security's purpose is approved, but dependencies are not installed in this step.

**Trade-offs retained:** Frontend/backend releases are coupled. Analytics queries depend on the internal schema. One account provides no individual attribution. In-memory sessions require login after a restart and need reconsideration for multiple instances. History, RBAC, conversion, and larger-scale support remain extension paths rather than implemented capabilities.

**Authorized changes:** Create `ARCHITECTURE.md`, update architecture/access decisions in `ASSUMPTIONS.md`, and create this consolidated record. Requirements content, schema, API contracts, application scaffolding, dependencies, and infrastructure are outside this step.

**Actual verification:** Python documentation checks passed for local links, final newlines, trailing whitespace, balanced code fences, consistent table columns, secret patterns, required architecture sections, and exactly the three approved changed files. The requirements baseline was byte-for-byte unchanged at 401 words. `git diff --check` passed. No application tests or builds apply because application implementation has not started. Commit/push outcomes are reported from actual commands in the completion report rather than forecast here.

## Pending Clarification and Subsequent Work

Incubyte's reply remains pending according to the user. Review differences explicitly and obtain approval before changing scope or implementation. Step 7 will define API contracts and remaining business rules; executable migrations, dependencies, and application scaffolding remain later steps.

## Step 6: Data Model Review and Documentation

**User direction:** Continue to data-model review, then approve the presented four-table model and the four-file documentation step.

**AI contribution:** Compared current salary columns with a separate one-to-one salary table and premature history storage. Proposed lookup tables for consistent department/country/currency reporting, exact decimal storage, reference constraints, initial indexes, and clear persistence ownership. Consulted official PostgreSQL, Java currency, and Jakarta Persistence documentation; references are recorded in [DATA_MODEL.md](DATA_MODEL.md).

**Approved decisions:** Store one current salary on the employee row; use `departments`, `countries`, and `currencies` as controlled reference tables; require unique employee numbers and case-insensitive department-name uniqueness; use `NUMERIC(19,4)`/`BigDecimal`, currency-specific precision validation, and explicit non-finite rejection. Add employee version and timestamps, initial department/country filter indexes, and a unique pagination sort tie-breaker. Employee services coordinate atomic changes; compensation owns monetary rules; analytics remains read-only.

**Correctness findings:** A fixed numeric scale can round excess precision, so validate before database coercion. A positive-value check alone does not exclude PostgreSQL `NaN`. JPA versioning must be paired with checking the version originally edited by the browser; loading the latest row on submission alone does not prevent stale-form overwrites.

**Trade-offs retained:** No salary-history table, audit trail, lookup-management UI, or trigger-based precision framework. Timestamp/version metadata is not history. Lookup records and detailed API rules remain to be finalized. The schema supports up to four fractional digits; broader requirements would require a policy/schema review.

**Authorized changes:** Create `DATA_MODEL.md` and update `ARCHITECTURE.md`, `ASSUMPTIONS.md`, and this record. The requirements baseline, API design, executable migrations, database setup, and application code are outside this step.

**Actual verification:** Python checks passed for local links, whitespace, final newlines, code fences, table columns, secret patterns, required model sections, and exactly the four approved documentation changes. The requirements baseline remained byte-for-byte unchanged at 401 words. `git diff --check` passed. No database/application tests or builds ran because no executable implementation exists. Git outcomes are reported from actual commands in the completion report.

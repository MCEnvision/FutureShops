# Phase 000 duplicate before repair gate

Captured 2026-09-01 for P000-TASK-006. The dry run is read only and intentionally performs no repair or issue mutation.

## Finding under test

Sanitized finding: a valid server shop offer reports that the offer service is unavailable after an update, with buy and sell paths affected. Support line: Forge 1.20.1. Candidate revision: the current captured `origin/1.20.1` baseline. Severity: high until the local campaign disproves it. Suspected boundary: catalog migration and readiness publication. This is a hypothesis only.

## Queries and results

The gate searched the repository issue index with these behavior terms.

| query | matching records |
| --- | --- |
| `offer service unavailable` | issue 25, open |
| `server shop offers unavailable` | issue 25, open; issue 34, open; issues 4 and 21, closed |
| `finite stock money purchase` | issue 34, open |
| `catalog stock migration` | issue 25, open |

The exact matching evidence is the issue 25 body and its frozen comments describing unavailable offers after an update, including the reported gunpowder buy and sell failure. Issue 34 is a related but distinct finite stock path and remains separate because infinite stock succeeds in that report.

## Decision

The correct result is reuse and enrichment of issue 25, not creation of a duplicate and not a repair. The finding maps to CORE-PHASE-002 and its compatibility matrix. A later finding that is only finite stock specific maps to issue 34. A materially different behavior or support line must repeat this search before repair.

## Confidential routing

Potential exploit details, credentials, raw private player data, and sensitive NBT are routed to the repository private vulnerability process rather than public issues. The security advisory endpoint is readable and currently has zero records. A public issue is created only for a non confidential repository owned defect after disclosure review.

## Reusable decision schema

Each future finding records the sanitized behavior, support line, affected revision, severity, component hypothesis, exact evidence, search terms, matching issue or new issue decision, acceptance criteria, phase owner, regression fidelity, and invalidation edges. A repair is authorized only after the decision record exists. Scope, trust boundary, destructive behavior, credentials, public behavior, cost, or irreversible remote state changes require `PLAN_REVISION_REQUIRED`.

## Status

The duplicate gate is proven usable against issue 25. No repair, new issue, comment, label, or remote state mutation occurred during this dry run.

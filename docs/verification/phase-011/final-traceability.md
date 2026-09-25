# final beta 3 traceability audit

Date: 2026-09-25

This audit reconciles the final phase contract with the Forge 1.20.1 source tree and the
approved phase evidence. It is an evidence map, not a substitute for the post merge build and
runtime gates. Rows marked `recheck` are rerun against the final merged beta 3 artifact before
the completion tag is created.

| requirement | acceptance | owner evidence | final disposition |
| --- | --- | --- | --- |
| CORE-REQ-001 | CORE-AC-001 | `docs/verification/phase-000/semantic-delta-ledger.md`, `docs/verification/phase-008/donor-ledger-closure-2026-09-24.md` | inherited and rechecked |
| CORE-REQ-002 | CORE-AC-002 | `docs/verification/phase-001/diagnostics-and-dependency-2026-09-24.md`, `docs/security/dependency-alerts-3.0-beta.3.md` | current alerts classified, recheck |
| CORE-REQ-003 | CORE-AC-003 | `docs/verification/phase-001/diagnostics-and-dependency-2026-09-24.md` | inherited and rechecked |
| CORE-REQ-004 | CORE-AC-004 | `docs/verification/phase-002/provider-api-2026-09-24.md` | inherited and rechecked |
| CORE-REQ-005 | CORE-AC-005 | `docs/verification/phase-002/baseline-2026-09-02.md`, `docs/verification/phase-003/completion.md` | inherited and rechecked |
| CORE-REQ-006 | CORE-AC-006 | `docs/verification/phase-003/crash-matrix.md`, `docs/verification/phase-005/shared-routes-2026-09-24.md` | inherited and rechecked |
| CORE-REQ-007 | CORE-AC-007 | `docs/verification/phase-003/completion.md`, `docs/verification/phase-005/persistence-recovery-2026-09-01.md` | inherited and rechecked |
| CORE-REQ-008 | CORE-AC-008 | `docs/verification/phase-004/provider-conformance-2026-09-24.md` | exact Pixelmon Forge path rechecked |
| CORE-REQ-009 | CORE-AC-009 | `docs/verification/phase-004/bridge-conformance-2026-09-24.md` | independent bridge proof rechecked |
| CORE-REQ-010 | CORE-AC-010 | `docs/verification/phase-004/command-matrix-2026-09-02.md`, `docs/verification/phase-005/shared-routes-2026-09-24.md` | inherited and rechecked |
| CORE-REQ-011 | CORE-AC-011 | `docs/verification/phase-006/shop-settlement-2026-09-24.md` | inherited and rechecked |
| CORE-REQ-012 | CORE-AC-012 | `docs/verification/phase-006/backend-integration-2026-09-01.md` | inherited and rechecked |
| CORE-REQ-013 | CORE-AC-013 | `docs/verification/phase-007/bazaar-auction-route-inventory-2026-09-24.md` | inherited and rechecked |
| CORE-REQ-014 | CORE-AC-014 | `docs/verification/phase-005/shared-routes-2026-09-24.md` | inherited and rechecked |
| CORE-REQ-015 | CORE-AC-015 | `docs/verification/phase-008/shop-snapshot-protocol-2026-09-24.md`, `docs/verification/phase-008/production-client-stale-buy-2026-09-24.md`, `docs/verification/phase-011/final-runtime-acceptance-2026-09-25.md` | exact beta 3 Forge pair passed |
| CORE-REQ-016 | CORE-AC-016 | `docs/verification/phase-008/donor-ledger-closure-2026-09-24.md`, `docs/verification/phase-008/preservation-matrix-2026-09-24.md` | inherited and rechecked |
| CORE-REQ-017 | CORE-AC-017 | `docs/verification/phase-009/security-route-map-2026-09-24.md`, `docs/verification/phase-009/dependency-and-persistence-audit-2026-09-24.md` | current source and archive scan recheck |
| CORE-REQ-018 | CORE-AC-018 | `docs/verification/phase-009/gametest-isolation-2026-09-24.md`, `docs/verification/phase-009/dependency-and-persistence-audit-2026-09-24.md` | recovery gates recheck |
| CORE-REQ-019 | CORE-AC-019 | `docs/verification/phase-010/server-matrix.md`, `docs/verification/phase-010/client-matrix.md`, `docs/verification/phase-011/final-runtime-acceptance-2026-09-25.md` | exact beta 3 Forge pair passed |
| CORE-REQ-020 | CORE-AC-020 | `README.md`, `DOCUMENTATION.md`, `docs/verification/phase-010/cleanup-receipt.md` | beta 3 documentation recheck |
| CORE-REQ-021 | CORE-AC-021 | this packet, final packaging and merge records | final phase owner |

## issue 66

The Forge 1.20.1 Pixelmon 9.2.3 native path and the independent transaction aware bridge are
covered by phase 004. DanConomy 1.2.1 is NeoForge 1.21.1 only and remains explicitly refused by
the beta 3 scope. The NeoForge donor remains read only. Issue 66 therefore stays open for the
future NeoForge implementation and exact provider additions rather than being falsely closed by
Forge evidence.

## issue 9

The historical 25 alerts remain documented as platform owned or build tooling risk. Four newer
alerts are recorded in `docs/security/dependency-alerts-3.0-beta.3.md`. They are not shipped in
the FutureShops archive and cannot be safely replaced by a split package inside a Forge mod. The
issue remains open until a compatible Minecraft, Forge, ForgeGradle, or JLine platform update is
planned and passes its own complete matrix.

## invalidation rule

The final merge commit, version, dependency pin, configuration, or production jar identity
invalidates any row that names an older candidate. Those rows must be rerun before the signed
completion tag. No row is treated as complete from a plan statement alone.

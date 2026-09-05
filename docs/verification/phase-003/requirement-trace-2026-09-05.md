# Phase 003 requirement trace

This trace maps every mandatory core requirement to the implemented behavior and current evidence. The authoritative plan remains the source of truth for scope and acceptance criteria.

| Requirement | Validation evidence |
| --- | --- |
| `CORE-REQ-001` | Minecraft 1.21.1, NeoForge 21.1.248, Java 21, GeckoLib 4.8.4, exact Pixelmon 9.4.0, and standard startup without optional providers are recorded in [final validation](final-validation-2026-09-05.md). |
| `CORE-REQ-002` | The capability aware API, deterministic registry, reserved provider registration, typed outcomes, and separate `pixelmon`, `danconomy`, and `vault` adapters pass the focused and complete suites. |
| `CORE-REQ-003` | `internal` remains the default, selection is restart only, invalid or missing external providers fail closed, and no runtime fallback occurred in the packaged refusal profiles. |
| `CORE-REQ-004` | `READY`, `DRAINING`, `RECOVERING`, `FROZEN`, `MISSING`, and `INCOMPATIBLE` behavior passes lifecycle, recovery, restart, and packaged refusal checks. |
| `CORE-REQ-005` | Server authority, packet and command validation, stale client route handling, and provider readiness revalidation pass the existing phase 001 matrices and the complete phase 003 suite. |
| `CORE-REQ-006` | Signed integer minor units, exact currency precision, bounds, insufficient funds, and checked arithmetic pass internal, Pixelmon, DanConomy, and Vault tests. |
| `CORE-REQ-007` | Write ahead journal states, root and leg identities, receipt audit records, provider receipts, lookup, retry, interruption, custody, claims, and compensation pass the GameTest and Vault restart matrices. |
| `CORE-REQ-008` | Provider owned balances remain authoritative, internal data stays dormant in external mode, and analytics cannot become a balance source. Existing state and dashboard tests pass. |
| `CORE-REQ-009` | Buy, sell, cart, player shop, `/pay`, public API, administrator adjustments, refund, compensation, custody, claims, money items, and presentation routes pass the complete surface matrix. |
| `CORE-REQ-010` | Restart only switching, no balance migration, preserved independent data, and rollback guidance are covered by selection tests and the provider runbooks. |
| `CORE-REQ-011` | Money items and ATM mutations remain registered for compatibility but are inactive under external providers. The surface and physical money refusal tests pass. |
| `CORE-REQ-012` | Typed balance availability, provider lifecycle presentation, safe unavailable states, and internal only ranking behavior pass the presentation and networking tests. |
| `CORE-REQ-013` | Bounded `/futureshops debug` commands, module allowlist, correlation, sanitization, rate limits, default off state, and server log first procedure pass focused tests and packaged runtime checks. |
| `CORE-REQ-014` | Provider discovery is startup bound, debug output is rate limited, and no per tick provider or filesystem scan was introduced. Source and runtime audits pass. |
| `CORE-REQ-015` | Exact artifact hashes and provenance were reviewed. Candidate and source scans found no external package, jar, bridge, SQLite, credential, or private log content. The DEC-022 inspection, no-copy, no-modified-jar, no-bundling, and no-redistribution boundary is preserved. |
| `CORE-REQ-016` | Clean draining, flush ordering, clean marker, receipt audit backup scope, unknown record recovery, frozen ambiguity, and no guessed balance restoration pass the persistence matrix and are documented in the recovery runbook. |
| `CORE-REQ-017` | Exact Pixelmon 9.4.0 native `PlayerPartyStorage` mixin application, account classification, request receipts beside `pixelDollars`, durable save, duplicate retry, conflict refusal, unknown record recovery, and two process restart pass. Pixelmon bytes are absent from the candidate. |
| `CORE-REQ-018` | Exact DanConomy 1.2.1 `LedgerData` mixin application, same image balance and receipt durability, default ledger gate, mirrored refusal, ordinary call preservation, restart replay, and no-copy boundary pass. The separate Vault transaction proof passes, while the unmodified legacy hybrid stack remains `MISSING` and fail closed. |
| `CORE-REQ-019` | Focused and full tests, isolated data generation, standard and exact provider GameTests, packaged candidate servers, Pixelmon and DanConomy restarts, Vault exact hybrid replay, debug evidence, dependency review, archive inspection, and cleanup pass. |
| `CORE-REQ-020` | README, maintainer overview, documentation index, provider API, configuration, migration, integration, recovery, support, compatibility, and verification documents match implemented behavior and preserve the client escalation rule. |
| `CORE-REQ-021` | One reproducible unpublished FutureShops 2.3.0 candidate identifies source commit `c2a24295cfcfeb05aa87935b02fb51f290ecd9b3`, has recorded SHA 256 and SHA 512, passed every exact runtime, excludes external bytes, and was not published as a release. |
| `CORE-REQ-022` | The existing open issue 66 was accessed only after `CORE-REQ-019` and `CORE-REQ-021` passed. It was updated in place with Forge 1.20.1 and future NeoForge 1.21.1 guidance, DanConomy obligations, the transaction aware Vault boundary, and DEC-022 no-copy rules, then read back without creating a duplicate. See [issue 66 evidence](github-issue-66-2026-09-05.md). |

## Definition of done trace

The implementation source is frozen at `c2a24295cfcfeb05aa87935b02fb51f290ecd9b3`. Every mandatory requirement has a passing deterministic, runtime, documentation, packaging, or GitHub evidence path. The exact candidate remains unpublished. No 3.0.0 code was changed. Issue 66 remains open for future implementation and port work.

`EVD-VER-001` is the runtime and deterministic evidence packet. `EVD-VER-002` is this requirement trace. `EVD-ART-001` is the candidate manifest and isolation evidence. `EVD-GH-001` is the issue identity, update, and readback evidence.

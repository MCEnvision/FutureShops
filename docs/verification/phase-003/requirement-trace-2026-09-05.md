# Phase 003 requirement trace

This trace maps the mandatory core requirements to the validated implementation and evidence. The plan remains the source of truth for scope and acceptance criteria.

| Requirement | Validation evidence |
| --- | --- |
| `CORE-REQ-001` through `CORE-REQ-005` | Existing phase 000 and phase 001 API, selection, call graph, persistence, custody, and security packets. |
| `CORE-REQ-006` through `CORE-REQ-010` | Existing provider API, registry, restart only selection, version pins, and platform verification packets. |
| `CORE-REQ-011` | Exact Pixelmon 9.4.0 native mixin target, native account gate, and twenty passing GameTests in [final validation](final-validation-2026-09-05.md). |
| `CORE-REQ-012` | Native Pixelmon request UUID receipt, deduplication, durable save, reload, and restart replay evidence in the final validation packet. |
| `CORE-REQ-013` | Unsupported Pixelmon 9.3.1 refusal with clean server startup and no fallback. |
| `CORE-REQ-014` through `CORE-REQ-016` | Durable journal, receipt audit, custody, claims, lifecycle, clean marker, and recovery matrices in the phase 001 and phase 002 packets. |
| `CORE-REQ-017` | Separate Vault proof registrant, one transaction receipt backend, lookup, rollback, retry, and duplicate deduplication in the pure Vault surface and exact hybrid runs. |
| `CORE-REQ-018` | Refusal of the unmodified legacy PixelmonEconomyBridge and FinalEconomy stack when the transaction aware backend contract is absent. |
| `CORE-REQ-019` | Exact standard server with Pixelmon absent and internal provider, plus exact hybrid startup and restart validation. |
| `CORE-REQ-020` | Headless debug command audit with bounded records and required evidence fields. |
| `CORE-REQ-021` | Final candidate packaging, dependency isolation, checksums, source manifest, and no external provider classes in the production jar. |
| `CORE-REQ-022` | Final validation packet, owner acceptance boundary, and post artifact issue 66 update and readback in [the issue 66 evidence packet](github-issue-66-2026-09-05.md). |

## Definition of done trace

The implementation source is frozen at `6346e0ad156472a7c2f8b5d34ec96f7891ef80b9`. Focused tests, all tests, data generation, build, exact Pixelmon GameTests, standard server startup, incompatible version refusal, pure Vault surface tests, hybrid two process replay, debug toggle, jar isolation, and cleanup all passed. The exact candidate remains unpublished. No 3.0.0 code was changed. Issue 66 is kept open and is updated only after this artifact validation packet is committed.

`EVD-VER-001` is the runtime and deterministic evidence packet. `EVD-VER-002` is this requirement trace. `EVD-ART-001` is the candidate manifest and isolation evidence. `EVD-GH-001` is the issue identity, update, and readback evidence.

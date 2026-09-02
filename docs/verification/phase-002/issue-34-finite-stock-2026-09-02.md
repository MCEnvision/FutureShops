# Issue 34 finite stock purchase matrix

This packet records the local Forge 1.20.1 verification for issue 34 at the phase 002 working revision. The report says finite money purchases fail while unlimited purchases succeed and provides no logs. The candidate version is `3.0.0-beta.2`.

## Paired matrix

| case | result | evidence |
| --- | --- | --- |
| finite reserve and commit | pass | `Issue34FiniteInfiniteStockTest.finiteAndInfinitePurchasesShareTheSameAuthoritativeContract` reserves two finite items, commits them, and reports zero available stock |
| finite exhaustion | pass | the same test returns durable `INSUFFICIENT_STOCK` without creating a reservation |
| unlimited reserve and commit | pass | the same test reserves and commits the unlimited control without inventory backing |
| paired catalog reload | pass | `pairedReloadPreservesFiniteAvailabilityAndInfiniteSemantics` preserves finite quantity and unlimited display semantics |
| concurrent finite reservations | pass | `PersistentStockRepositoryTest.concurrentReservationUsesOneAtomicRevisionDecision` allows one application and one conflict |
| restart and replay | pass | stock repository snapshot rebuild tests restore reservations and completed request replay |
| dedicated server restart | pass | the same disposable `phase002-fixture` world restarted on port `25566`; the second startup reached `Done` and `/marketadmin status` again reported ready escrow, durable catalog authority, and `17/17` completed migration entries |

## First divergence and repair

The service and repository paths use the same authoritative stock state for finite and unlimited offers. The current source did not reproduce a finite-only transaction defect. The historical failure shape is consistent with a catalog migration that left finite definitions unavailable while an unlimited legacy path still rendered. The migration repair now verifies and adopts valid materialized state, preserves frozen finite counts across retry, and exposes actionable stock status and request context in the buy and offer service logs.

The isolated dedicated server reached `Done` on initial startup and after a restart of the same disposable world. Both `/marketadmin status` probes reported ready escrow, durable catalog authority, and `17/17` completed migration entries. No FutureShops error or exception was emitted during either startup. The independent client and two-client transaction trace remains part of the phase runtime ladder and must be recorded against the committed candidate before issue closure.

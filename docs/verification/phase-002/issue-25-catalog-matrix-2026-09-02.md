# Issue 25 catalog migration matrix

This packet records the local Forge 1.20.1 verification for issue 25 at the phase 002 working revision. The candidate version is `3.0.0-beta.2`. The source branch is based on `ce6354a166ab74b12a1eeae33fcfe38d513e05c6`, and the candidate artifact produced during verification has SHA256 `fd4fa0e431e85f1789ca7c4efb48ef5837cf713e2fc22173e6d6aa4f10008f13`.

## Evidence matrix

| case | result | evidence |
| --- | --- | --- |
| pristine durable store and legacy source | pass | `CatalogStockMigratorTest` imports finite and unlimited entries, verifies the snapshot, and preserves conservation |
| interrupted migration with empty failure metadata | pass | `verifiedMaterializedDestinationCanBeAdoptedAfterLegacyFailure` retries the materialized store without reseeding it |
| complete durable store with missing migration metadata | pass | `completeMaterializedDestinationCanBeAdoptedWithMissingMetadata` adopts the existing store and preserves its revision and lineage |
| incompatible materialized store | pass | `incompatibleMaterializedDestinationRemainsFailedWithEvidence` remains failed closed and retains the precise verification detail |
| changed legacy source after a partial migration | pass | `nonemptyDestinationAndChangedSourceFailClosed` rejects the changed source without mutating the destination |
| finite and unlimited stock consumers | pass | `Issue34FiniteInfiniteStockTest` covers paired reserve, commit, exhaustion, reload, and conservation semantics |
| dedicated server startup | pass | Forge 47.4.20 reached `Done` on the isolated fixture port and `/marketadmin status` reported `Escrow runtime: READY` and `Catalog stock authority: DURABLE. Migration: COMPLETE (17/17 entries)` |
| admin validation command | pass | `/marketadmin adminshop validate` reported `17 of 512 configured. Validation issues: 0` |

## Root cause and repair

The migration previously treated a nonempty durable stock store as an unrecoverable conflict even when the store was a valid materialized copy created by an earlier attempt. The first attempt also froze the legacy catalog before that check. A retry therefore had no safe path to verify the existing store or recapture the frozen legacy counts.

The repair records bounded failure detail, verifies a materialized destination against the current source before retry, adopts a verified store without reseeding, and recaptures the frozen legacy view when the cutover coordinator is already frozen. Incompatible state remains failed closed. This preserves finite stock, unlimited stock, request receipts, revisions, and conservation.

## Reproduction and reset

All migration fixtures use in memory repositories and deterministic timestamps. The dedicated server uses an isolated world and port. No production world, player data, escrow journal, or user inventory is used. Recreate the matrix with the focused migration and stock tests, then run the server status commands after a clean startup. Dispose of the isolated server and run directory after the evidence is captured.

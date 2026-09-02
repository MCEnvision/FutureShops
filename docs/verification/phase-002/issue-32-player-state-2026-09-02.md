# Issue 32 player state corruption campaign

This packet records the local Forge 1.20.1 campaign for issue 32 at the phase 002 working revision. The report does not identify a trigger, field, file, or matching FutureShops transaction, so the campaign uses disposable deterministic states and preserves all unrelated sentinels.

## Corpus and ownership checks

| seed family | result | evidence |
| --- | --- | --- |
| empty, malformed, truncated, trailing, and oversized item snapshots | pass | `ItemStackSnapshotCodecTest` rejects each input before decoding or mutation |
| old and newer inventory delivery token versions | pass | `PlayerInventoryDeliveryTest.legacyVersionOneReceiptRemainsReadable` and `Issue32PlayerStateCorpusTest.boundedTokenCorpusRejectsMalformedAndNewerStates` cover compatibility and fail closed newer data |
| tampered delivery digest | pass | `PlayerInventoryDeliveryTest.persistedInventoryAndReceiptJointlyProveDelivery` rejects a changed digest |
| invalid delivery evidence with an unrelated modded item | pass | `Issue32PlayerStateCorpusTest.invalidDeliveryEvidenceDoesNotOverwriteUnrelatedInventoryState` leaves the foreign NBT sentinel unchanged |
| full inventory and changed delivery slot | pass | `PlayerInventoryDeliveryTest.fullInventoryRejectsWithoutAChangedSlot` and the receipt inspection checks reject unsafe changes |
| durability failure before directory force | pass | `PlayerDataDurabilityBarrierTest.fileForceFailureStopsBeforeDirectoryForce` stops before the second durability boundary |

## Ownership classification

The exercised malformed and ownership-conflicting inputs are FutureShops delivery and snapshot formats. They are rejected with bounded exceptions or an unknown inspection result and do not write player data. The campaign did not reproduce the historical unusable player file because issue 32 contains no trigger or artifact identifying a FutureShops-owned field. No base-game or other-mod cause is claimed from the available evidence.

## Recovery contract

The existing inventory delivery barrier requires exact changed-slot proofs and a forced player file before committing the receipt. Unrelated player or modded NBT changes remain outside the delivery proof. A mismatch stays protected for recovery inspection instead of deleting or normalizing the player file. Version one receipts remain readable, while newer or incompatible state is rejected without mutation.

The issue remains open for an owner supplied trigger or sanitized artifact. This is an evidence backed local disposition, not a reporter retest requirement.

# phase 003 source locator register

candidate base commit: `4bcbc97e6c77fad320279067564e7453f7539889`
branch: `envy/3.0.0-beta.3-phase-003`
loader: forge 47.4.20 on minecraft 1.20.1
java: 17

| owner | locator | role |
| --- | --- | --- |
| existing wal | `src/main/java/com/enviouse/futureshops/server/escrow/journal/WriteAheadJournal.java` | locked append, forced writes, crc32c validation, bounded replay, tail truncation |
| wal record | `src/main/java/com/enviouse/futureshops/server/escrow/journal/JournalRecord.java` | sequence, transaction identity, step identity, bounded payload |
| wal recovery | `src/main/java/com/enviouse/futureshops/server/escrow/journal/JournalScanResult.java` | valid byte boundary and truncated tail state |
| coordinator | `src/main/java/com/enviouse/futureshops/server/escrow/coordinator/DurableEconomyCoordinator.java` | phase 003 admission, dispatch, receipt lookup, custody state, claims, lifecycle |
| coordinator state | `src/main/java/com/enviouse/futureshops/server/escrow/coordinator/OperationState.java` and `CoordinatorLifecycle.java` | checked operation and runtime transitions |
| immutable identity | `src/main/java/com/enviouse/futureshops/server/escrow/coordinator/RouteContext.java`, `BoundLeg.java`, and `CustodyPlan.java` | account binding, route permission, capability, amount, payload, custody |
| receipt audit path | `src/main/java/com/enviouse/futureshops/server/escrow/coordinator/ReceiptAuditPath.java` | `world/data/futureshops/receipts/economy-coordinator.wal` |
| diagnostic boundary | `src/main/java/com/enviouse/futureshops/server/debug/DebugDiagnostics.java` | default off bounded pseudonymous coordinator events |
| claim owner | existing claim and custody owners under `src/main/java/com/enviouse/futureshops/server/escrow/claim/` and `src/main/java/com/enviouse/futureshops/server/escrow/custody/` | preserved production ownership, no replacement wallet |

The phase 003 coordinator extends the existing `WriteAheadJournal` rather than creating a second spendable ledger. Native provider adapters and production route callers remain disabled until their later phases.

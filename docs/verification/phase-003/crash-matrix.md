# phase 003 coordinator crash matrix

The focused fixture suite is `com.enviouse.futureshops.server.escrow.coordinator.DurableEconomyCoordinatorTest`.

| cut or fault | expected fact | observed test |
| --- | --- | --- |
| before provider dispatch | admission writes a root and leg, provider dispatch count remains zero | `flushesIntentBeforeEffectAndReplaysAfterCleanRestart` |
| provider returns unknown | root is frozen, custody remains held, no automatic retry occurs | `unknownOutcomeFreezesAndRecoveryUsesLookupWithoutRetry` |
| effect persisted before acknowledgement | a fresh effect instance reads the receipt and recovery confirms it by lookup | `receiptSurvivesFreshEffectProcessAndIsResolvedByLookup` |
| clean restart after confirmation | exact admission replays without a second provider effect | `flushesIntentBeforeEffectAndReplaysAfterCleanRestart` |
| unclean restart after intent | prepared intent enters recovery and resolves without dispatch because no effect was submitted | `uncleanRestartRecoversPreparedIntentWithoutDispatching` |
| known partial completion | confirmed predecessor produces an original binding claim and freezes the root | `changedPayloadAndBindingAreRefusedAndClaimsCollectAtMostOnce` |
| changed original binding | collection refuses without redirecting the claim | `changedPayloadAndBindingAreRefusedAndClaimsCollectAtMostOnce` |
| concurrent collection | synchronized durable claim transition yields one collection and one replay result | `concurrentClaimCollectionDeliversOnlyOnce` |
| malformed payload | coordinator refuses startup as storage corruption and preserves the source journal | `malformedCoordinatorPayloadIsRejected` |
| illegal transition | terminal resolution cannot be submitted again | `operationTransitionMatrixRejectsTerminalReplay` |

Command used for the focused gate:

```text
./gradlew test --tests com.enviouse.futureshops.server.escrow.coordinator.DurableEconomyCoordinatorTest --no-daemon
```

Result: ten tests passed on node 1 with Java 17. The JUnit temporary directories were owned by the test task and removed by the test runner. No Minecraft client was started for this server-only phase.

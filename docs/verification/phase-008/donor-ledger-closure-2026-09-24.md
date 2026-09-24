# phase 008 donor ledger closure

Observation date: 2026-09-24
Phase: CORE-PHASE-008
Task: P008-TASK-003
Requirement: CORE-REQ-016

## pinned inputs

The Forge baseline remains `78fad4069d778996c24ecf5acc5cbe0e1edea7a` and the read only NeoForge donor remains `cc2d69425beea2c93a4000e44d49381deba8eed2`. The complete path ledger is `../phase-000/semantic-delta-ledger.tsv` with 1,923 rows. The complete donor history ledger is `../phase-000/history-delta-ledger.tsv` with 525 rows. No donor file, protocol discriminator, provider store, or loader implementation was copied into Forge.

## deterministic closure

The new `SemanticLedgerClosureTest` validates every row at build time. It verifies the pinned donor and Forge revisions, exact tabular field counts, nonempty requirement, phase, regression, and evidence fields, no `unknown` applicability or disposition, resolvable Forge targets against the pinned Forge tree, and the phase 008 ownership totals.

The phase 008 semantic slice contains 334 rows, 328 applicable rows, 141 adapted rows, 187 Forge only preservation rows, and 6 explicitly rejected non product rows. The phase 008 donor history slice contains 342 rows, 120 applicable rows, 120 adapted rows, and 222 explicitly rejected non product rows. Every applicable row has an assigned Forge task and regression identity. The existing ledger remains the authoritative row level record and is not duplicated or rewritten here.

## disposition policy

Rows with a Forge only preservation disposition remain owned by the existing Forge implementation and are protected by the phase specific preservation suites. Adapted rows are implemented in the existing Forge owner or covered by an exact equivalence regression. Loader only, donor evidence, planning, and unsupported provider rows remain rejected with their original source fingerprint and reason. This keeps richer Forge WAL, custody, offers, market, cash, storage, and recovery ownership intact.

## verification

Focused protocol, admission ordering, client state, codec, session, and ledger tests passed after the phase 008 changes. The full `./gradlew test build` suite passed with 2,024 tests and zero failures or errors. The current candidate artifact is `build/libs/futureshops-3.0.0-beta.2.jar` with sha256 `90de004da0f098adb96ccbdb814578c7fc4edab9a880ffde9a409306ffa021d5`.

This evidence proves ledger closure and static or unit equivalence coverage. The isolated Forge startup attempt is recorded separately, but dedicated server stale ordering, split host reconnect behavior, and laptop rendering and input evidence remain P008-TASK-002 and later phase gates. No claim is made for those gates here.

## cleanup

The verification reused the preexisting Gradle dependency and build directories. The separate disposable Forge startup runtime was removed after its owned process exited. No Minecraft client, audio stream, watcher, temporary database, or disposable runtime remained after cleanup. The preexisting repository `run` directory was preserved after the earlier misdirected startup attempt.

# phase 008 stale packet handler evidence

Observation date: 2026-09-24
Phase: CORE-PHASE-008
Task: P008-TASK-001
Loader: Forge 1.20.1 47.4.20
Java: 17.0.19

## result

The server only `runGameTestServer` task ran in the disposable
`.phase008-gametest-runtime` directory with `eula=true`. The test batch
`futureshops.offer_service:1` completed seven required tests with all seven
passing.

The `staleBuyPacketIsRefusedBeforeValueEffects` test created a real connected
`ServerPlayer` and `EmbeddedChannel`, constructed the Forge
`NetworkEvent.Context`, and invoked `C2SBuyRequestPacket.handle`. The packet used
the prior session revision after the server advanced the authoritative revision.
The handler marked the packet handled, returned before provider or custody work,
and left both the finite stock quantity and player inventory unchanged.

This proves the server packet handler and zero value delta for a stale buy. It
does not claim stale sell or cart handler coverage, or laptop client rendering,
input, reconnect, or audio evidence.

## cleanup

The server exited normally. Its disposable runtime was removed after the run.
The preexisting repository `run` directory and shared Gradle caches were left
untouched.

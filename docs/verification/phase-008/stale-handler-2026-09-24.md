# phase 008 stale packet handler evidence

Observation date: 2026-09-24
Phase: CORE-PHASE-008
Task: P008-TASK-001
Loader: Forge 1.20.1 47.4.20
Java: 17.0.19

## result

The server only `runGameTestServer` task ran in the disposable
`.phase008-gametest-runtime` directory with `eula=true`. The test batch
`futureshops.offer_service:1` completed nine required tests with all nine
passing.

The stale buy, cart, and sell tests created real connected `ServerPlayer`
instances and `EmbeddedChannel` connections, constructed Forge
`NetworkEvent.Context` values, and invoked the actual packet handlers. Each
request used the prior session revision after the server advanced the
authoritative revision. The handlers marked the packets handled, returned
before provider or custody work, and preserved the finite stock and player
inventory values.

This proves server packet handler refusal and zero value deltas for stale buy,
cart, and sell requests. It does not claim laptop client rendering, input,
reconnect, or audio evidence.

## cleanup

The server exited normally. Its disposable runtime was removed after the run.
The preexisting repository `run` directory and shared Gradle caches were left
untouched.

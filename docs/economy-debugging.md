# Bounded economy diagnostics

FutureShops includes a server side diagnostic capture for administrators who need a sanitized support packet. It is off by default and does not own balances, items, claims, or provider decisions.

## Controls

Operators with permission level 2 can use these commands from a player or the server console.

```text
/futureshops debug on <module>
/futureshops debug on <module> request <root-id>
/futureshops debug on <module> actor <player>
/futureshops debug status
/futureshops debug off
```

The module must be `economy`, `escrow`, `shop`, `market`, `cash`, `network`, `persistence`, or `all`. A request selector and an actor selector are mutually exclusive. The server resolves actor selectors, so raw player identifiers are never written to the capture.

Each capture expires after 60 seconds. It accepts at most 100 events per second, 2,000 events, 4 KiB per event, and 5 MiB total. Events are queued for the server logger with bounded backpressure. The status line reports the capture id, selector, remaining time, counters, limits, and the `server.log` output location. Turning diagnostics off more than once is safe.

## Shop snapshot stale requests

Forge shop sessions carry a monotonic snapshot revision. Buy and sell requests retain
their request identity, payment source, and observed revision. If the catalog changed or
the session was replaced, the server returns `stale_request` with `stale_snapshot` before
provider, inventory, custody, or balance effects, then sends a silent authoritative refresh.
The client ignores an older same shop revision and keeps newer cart state. Reopen, forced
close, shop switching, and reconnect create a new session boundary. Capture `shop` or
`network` diagnostics when investigating a stale action, and compare the request revision
with the authoritative refresh rather than retrying blindly.

## Provider selection capture

For provider API support, capture one normal internal query, one rejected registration, one staged
reload, and one unavailable provider refusal. Start a fresh capture after every reload or restart
because captures are intentionally reset:

```text
/futureshops debug status
/futureshops debug on economy
/futureshops debug status
```

The sanitized lines should show `registration_result`, `binding_validation`,
`selection_resolution`, `selection_staged`, `query_result`, `mutation_refusal`, and
`metadata_projection`. Confirm that desired and active provider ids are distinct during staged
reload, that an unavailable query is not a numeric zero, and that the internal wallet has no delta
when an external mutation is refused. Browse and pure barter may remain available in this state.
Do not claim external mutation support from a capability declaration alone.

## Native and external provider capture

Provider conformance is exact. For Pixelmon, record the unmodified Forge 1.20.1
9.2.3 artifact hashes, the target class and method descriptors, the save image,
and the original root and leg references. Capture a clean save, a process cut
before save, a fresh process lookup, an identical retry, and a changed payload.
Missing Pixelmon, a wrong version, an altered descriptor, a custom account, or a
contradictory receipt must refuse before another effect.

For an independently registered provider, record the API compatibility version,
provider id, backend identity projection, transaction commit boundary, receipt
lookup, and a fresh process lookup. A boolean bridge, a missing receipt, an
unavailable backend, or a changed account or currency binding is refused. Do not
include raw account ids, balances, NBT, backend paths, or provider jars in a
support packet.

After each capture, run `debug off`, confirm `debug status` reports capture off,
stop only the owned fixture process, and remove the exact disposable runtime,
database, logs, and reports after their final evidence consumer.

## Privacy and support

Diagnostic events use schema version 2 and capture scoped pseudonyms for request, leg, and actor references. Do not paste raw logs into a public issue. A support packet should contain the mod version, Minecraft and Forge versions, the selected module, capture id, command window, sanitized decisive lines, expected and actual result, and the cleanup result. Remove balances, NBT, chat, credentials, raw UUIDs, private paths, and private addresses before sharing.

Diagnostics do not change transaction behavior. If a capture reaches a limit, expires, loses its output worker, or observes a removed target, it records a bounded drop or stop state and never retries a monetary operation. Disable the capture after the reproduction and confirm `debug=off` with the status command.

## Failure handling

An unauthorized command is rejected. An unknown module is rejected. A missing or stale request or actor selector does not broaden the capture. If a server restart, reload, or shutdown occurs, the capture is not restored automatically. Open a new, narrow capture only after the server is ready.

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

## Privacy and support

Diagnostic events use schema version 2 and capture scoped pseudonyms for request, leg, and actor references. Do not paste raw logs into a public issue. A support packet should contain the mod version, Minecraft and Forge versions, the selected module, capture id, command window, sanitized decisive lines, expected and actual result, and the cleanup result. Remove balances, NBT, chat, credentials, raw UUIDs, private paths, and private addresses before sharing.

Diagnostics do not change transaction behavior. If a capture reaches a limit, expires, loses its output worker, or observes a removed target, it records a bounded drop or stop state and never retries a monetary operation. Disable the capture after the reproduction and confirm `debug=off` with the status command.

## Failure handling

An unauthorized command is rejected. An unknown module is rejected. A missing or stale request or actor selector does not broaden the capture. If a server restart, reload, or shutdown occurs, the capture is not restored automatically. Open a new, narrow capture only after the server is ready.

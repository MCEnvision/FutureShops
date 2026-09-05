# Phase 002 receipt audit amendment

This checkpoint records the durable local receipt audit work added for FutureShops `2.3.0` on Minecraft `1.21.1` and NeoForge `21.1.248`. It does not close Phase 002 or authorize a release.

## Implemented behavior

The transaction coordinator now writes one receipt audit record for every transaction transition. The records live under `world/data/futureshops/receipts` as versioned properties files with request identity, actor, optional counterparty, exact amount, mutation kind, transaction state, result status, provider binding, diagnostic, optional provider receipt, and a SHA 256 checksum. Provider resulting balances are retained only as facts returned in a provider receipt. No independent balance ledger is created.

Each record is written through a temporary file, flushed, forced, atomically replaced, and followed by a directory force. A bounded record count, file size limit, clean marker, transition validator, checksum validator, and journal comparison protect startup. Unknown, malformed, partial, contradictory, or mismatched records keep the economy in `RECOVERING` or `FROZEN`. A new receipts directory is backfilled from a valid existing transaction journal during a 2.2.x upgrade, then follows the normal recovery and clean marker transition. A nonempty or invalid directory is never silently backfilled.

Shutdown flushes the audit directory with the transaction journal, custody, claims, internal receipts, escrows, settlements, and clean marker gate. The backup runbook now requires the receipt directory as part of the complete matching recovery set.

Local receipts prove only what FutureShops durably recorded. They do not prove that an external Vault or Pixelmon operation was atomic, idempotent, or safe to replay. The existing strict capability gate remains authoritative, so the reviewed direct Pixelmon API and bridge candidate remain mutation unavailable.

## Verification

The following commands passed on the active phase branch.

```text
bash ./gradlew test --no-daemon
bash ./gradlew build --no-daemon
timeout 180s bash ./gradlew runGameTestServer --no-daemon
timeout 75s bash ./gradlew runServer --no-daemon
unzip -tq build/libs/futureshops-2.3.0.jar
```

The clean GameTest run completed all 16 required tests. The dedicated server reached `Done` and `FutureShops server starting` with Pixelmon `9.4.0`, GeckoLib `4.8.4`, Minecraft `1.21.1`, and NeoForge `21.1.248`; its bounded process ended by the test timeout. Client smoke remains unverified on this headless node.

The current artifact is `build/libs/futureshops-2.3.0.jar`.

```text
sha256 198bc350c072d7731cfd00f9c1d0a0fcdca2d1bf5cda6828ef7b96f640bc6d9d
sha512 87ca13d2689d00f3bc03490eeaa0e71f679df15a632d1c510286f880fb89a4fa0018aadc6716a32547a5d99a4fc75c60d923c3893732c44be836780b77572a57
```

Phase 002 remains open because the reviewed external bridge does not provide durable provider receipts or idempotent retry, and the complete external mutation and recovery matrix is therefore not proven.

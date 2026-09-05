# Backup and restore runbook

This runbook protects the FutureShops economy during upgrades, provider changes, and recovery. Use a disposable copy for rehearsal. Never use a backup as authority to invent or rewrite an external balance.

## What to back up

Stop the server before taking or restoring a backup. Keep one complete, matching snapshot containing all of the following.

1. The complete world directory, including `world/data` and every dimension save.
2. The complete `config/futureshops` directory and the server configuration files that select the loader and mod set.
3. The exact FutureShops jar, Minecraft version, NeoForge version, GeckoLib version, and any other required mod jars.
4. The complete data directory for the selected external provider, bridge, or economy plugin. For Pixelmon, include the exact Pixelmon configuration and world data used by its economy implementation.
5. A manifest containing the snapshot timestamp, source commit or jar identity, provider identifier, loader versions, and SHA 256 and SHA 512 hashes for every copied file.

FutureShops recovery records are stored in the world SavedData files and the receipt audit directory at `world/data/futureshops/receipts`. They include the transaction journal, receipt audit records and clean marker, custody, claims, internal receipts, player shop barter escrow, player shop sale escrow, and player shop settlements. Do not omit the receipt directory or copy only selected records.

Do not put credentials, tokens, private player exports, raw provider logs, or local secret files in the manifest or repository evidence. Keep those items in the operator's protected backup store.

## Create a snapshot

1. Announce maintenance and stop the server normally.
2. Confirm the log contains `FutureShops server stopping` and that the stop completed without a flush or clean marker failure.
3. Copy the complete world, including `world/data/futureshops/receipts`, configuration, mod set, and provider data into a new timestamped snapshot directory. Do not overwrite an older snapshot.
4. Generate SHA 256 and SHA 512 hashes for the copied files and store the manifest beside the snapshot.
5. Record the active provider, lifecycle result, source revision, and exact runtime versions.
6. Keep at least one prior known good snapshot until the replacement has passed a disposable restore rehearsal.

Example hash commands on Linux or macOS are:

```text
find SNAPSHOT -type f -print0 | sort -z | xargs -0 sha256sum > SNAPSHOT.sha256
find SNAPSHOT -type f -print0 | sort -z | xargs -0 sha512sum > SNAPSHOT.sha512
```

The hash files must not include themselves. Generate them outside the snapshot directory or exclude them from the input list.

## Restore a matching snapshot

1. Stop the server and preserve the current world, configuration, jar, and provider data as a separate incident snapshot. Do not delete the current state.
2. Verify the backup manifest and every hash before copying anything.
3. Restore the complete matching world, configuration, mod set, and provider data together. Do not combine a FutureShops world with a different provider version or a different bridge data directory.
4. Start the server with the exact recorded runtime and inspect the lifecycle, recovery, and provider logs before admitting players.
5. If a clean marker is missing or an incomplete, unknown, or checksum invalid journal or receipt audit record is found, allow `RECOVERING` to reconcile only through the original provider and request identity.
6. If the provider outcome remains unknowable, keep the lifecycle `FROZEN`. Resolve it with a durable provider receipt or an evidence backed operator decision. Do not retry, refund, compensate, or restore an external balance from a local snapshot by guesswork.
7. Rehearse the restored copy before using it as the production replacement. Record the restored hashes, startup result, recovery result, and any pending claims.

## Provider selection changes

Provider selection is restart only. Before changing `economy.provider`, create a complete snapshot of both FutureShops and the provider data. A selection change does not transfer, convert, seed, mirror, or erase balances. Existing internal balances remain dormant while an external provider is selected and become visible again only after a restart that selects a ready `internal` provider.

An unresolved request remains bound to its originating provider. If that provider is unavailable, restore that exact provider or use the matching backup and keep new mutations blocked. Never replay an unresolved request against a newly selected provider.

## Recovery evidence record

For each snapshot or restore rehearsal, record:

* date and operator
* source commit and exact jar hashes
* Minecraft, loader, mod, and provider versions
* selected provider and lifecycle state
* snapshot paths and manifest hashes
* expected and observed startup and shutdown results
* sanitized log paths
* pending request, custody, claim, escrow, and settlement counts
* restore decision and remaining operator action

Keep the evidence with the protected operational backup. Commit only sanitized procedures and results that contain no private player data, credentials, or raw external provider logs.

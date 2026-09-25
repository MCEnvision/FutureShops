# phase 009 security route audit

## candidate and coverage

This audit covers the Forge 1.20.1 beta 3 candidate at base commit `97e1639acb5d510954ea54d27c6faab1e8a81b4c` and the phase 009 working tree change that makes headless GameTest directories disposable. The audit is static and server side. It does not claim client rendering or multiplayer acceptance, which belongs to phase 010.

The route inventory covers every command under `src/main/java/com/enviouse/futureshops/command`, every registered packet under `src/main/java/com/enviouse/futureshops/network/ShopPackets.java`, the public API under `src/main/java/com/enviouse/futureshops/api`, and the transaction, market, shop, escrow, persistence, and diagnostic services reached by those entrypoints.

## authorization and bounds

| boundary | observed control | disposition |
| --- | --- | --- |
| administrator commands | command requirements use permission levels and `MarketPermissions.canAdmin`. Maintenance, validation, quarantine, and high impact recovery subcommands require the higher levels declared by the command tree. | accepted for server side audit |
| admin edit packets | `AdminShopEditService` and `AdminBulkListingService` recheck `ServerPlayer` permission level before applying any draft or bulk mutation. | accepted |
| ordinary player packets | handlers resolve the sender as `ServerPlayer`, validate the request identity and snapshot revision, then call the server transaction service. | accepted |
| packet values | codecs use bounded strings, counts, item identifiers, NBT sizes, and checked minor unit arithmetic. Shop snapshot encoding is bounded at 1 MiB and NBT at 65535 bytes. | accepted |
| replay and stale requests | request UUID, session identity, snapshot revision, and exact line snapshots are compared before custody or wallet mutation. | accepted by existing stale request GameTests |
| diagnostics | debug commands require permission level 2, captures are default off, bounded, pseudonymized, and scoped by module, request, or actor. | accepted for static route review |
| provider registration | provider identifiers are normalized and bounded. The registry rejects duplicate and reserved identifiers. No reflection, Bukkit, Vault, or service loader discovery was found in the audited provider path. | accepted |

## negative route review

The reviewed refusal cases are unauthorized admin mutation, malformed or oversized packet fields, stale shop snapshots, duplicate request identities, provider mismatch, unavailable capability, invalid catalog state, and ambiguous recovery receipts. The route services refuse before inventory, custody, provider, or WAL mutation, or retain the operation in recovery when an outcome is unknown. No source path was found that silently converts an unavailable provider into the internal wallet.

## evidence and limits

The full Java suite passed on 2026-09-24. The Forge GameTest server passed all nine required tests in an isolated runtime. This document is not a proof of graphical behavior, client input, or a universal absence of future defects. A newly reproduced defect must receive a GitHub issue before a fix is accepted.

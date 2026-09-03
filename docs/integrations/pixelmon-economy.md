# Pixelmon economy integration

FutureShops 2.3.0 for NeoForge 1.21.1 includes an optional adapter for exactly Pixelmon 9.4.0. The adapter is bundled in the FutureShops jar as source and runtime class names only. Pixelmon is not a build dependency, is not copied into the jar, and is not required for standard client or dedicated server startup.

## Supported stack

The adapter accepts only the following runtime identity.

| Component | Required value |
| --- | --- |
| Minecraft | 1.21.1 |
| NeoForge | 21.1.248 or a compatible 21.1 release supported by Pixelmon 9.4.0 |
| Pixelmon | 9.4.0 |
| FutureShops | 2.3.0 |
| Provider setting | `economy.provider = "pixelmon"` |

Selection is restart only. A missing Pixelmon installation or any version other than `9.4.0` does not register the adapter. FutureShops keeps the configured identifier and reports the provider as missing on that start. It does not fall back to `internal` during the lifecycle.

## Capability boundary

The exact Pixelmon API exposes `BankAccountProxy.hasImplementation()`, `getBankAccountNow(UUID)`, `BankAccount.getIdentifier()`, `getBalance()`, and `hasBalance(BigDecimal)`. FutureShops maps these to authoritative balance query and non mutating precheck capabilities. Balances must convert to an exact integer PokéDollar amount. Null accounts, identity mismatches, thrown calls, fractional values, and overflow return typed unavailable results. They never become zero or an inferred insufficient funds result.

The same API exposes boolean `take` and `add` methods, but it has no durable request identity, operation receipt, receipt lookup, or idempotent retry. The adapter therefore declares `WITHDRAW`, `DEPOSIT`, `RECEIPT_LOOKUP`, and `IDEMPOTENT_RETRY` unsupported. Its mutation, lookup, and retry methods return `CAPABILITY_MISSING` without invoking `take` or `add`.

This refusal happens in the coordinator capability gate before write ahead journal intent, item custody, inventory movement, claims, analytics, success events, or external calls. Buy, sell, cart, player shop, `/pay`, fees, refunds, physical money, and administrative value changes remain unavailable while Pixelmon is selected. Query only screens and diagnostics may continue to show the external balance.

Money items and ATM behavior remain internal provider features. They are inert when an external provider is selected. FutureShops never creates a balance mirror or mints a Pixelmon backed physical currency item.

## Installation and rollback

Install Pixelmon 9.4.0 and FutureShops 2.3.0 on the server. The same FutureShops jar must be present on clients. Back up the complete world, `config/futureshops`, the FutureShops jar, and Pixelmon economy data before changing provider selection. Set the provider, stop the server, replace the configuration, and restart. A provider change never migrates internal balances.

To roll back, stop the server, restore the matching backup, set `economy.provider = "internal"`, and restart. Do not delete the FutureShops journal, custody, claims, escrow, or world data to force a provider change. If a Pixelmon query is unavailable, correct the exact installation or return to the matching internal backup. Never guess a refund from a local balance snapshot.

## Evidence

The reviewed Pixelmon artifact was kept outside this repository at `/tmp/Pixelmon-1.21.1-9.4.0-universal.jar`.

* SHA 256, `9020393f98382ae8794ef2694e7bec1984c1a0eca735ea3eea06e0cb151c61f2`.
* SHA 512, `b1485031c27cbe0dd7125f11d3b003954e654f66c102479d443841071a37131067371bfc5e1fc2d8bf96a7195afa3ca02fc1525d343fc096d5bc598680bccafe`.

The exact API map and negative mutation classification are recorded in [Phase 002 Pixelmon API evidence](../verification/phase-002/pixelmon-api-2026-09-03.md). The adapter and fixture refusal proof are recorded in [Phase 002 Pixelmon refusal evidence](../verification/phase-002/pixelmon-refusal-2026-09-03.md). The available hybrid bridge stack is classified in the [Phase 002 bridge review](../verification/phase-002/bridge-review-2026-09-03.md). Legal terms and full live mutation recovery remain open gates. No Pixelmon `add` or `take` call is made by the test or production adapter.

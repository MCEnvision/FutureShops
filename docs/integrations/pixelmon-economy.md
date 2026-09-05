# Pixelmon economy integration

FutureShops 2.4.0 for NeoForge 1.21.1 includes an optional adapter for exactly Pixelmon 9.4.0. The adapter is bundled in the FutureShops jar as source and runtime class names only. Pixelmon is not a build dependency, is not copied into the jar, and is not required for standard client or dedicated server startup.

## Supported stack

The adapter accepts only the following runtime identity.

| Component | Required value |
| --- | --- |
| Minecraft | 1.21.1 |
| NeoForge | 21.1.248 or a compatible 21.1 release supported by Pixelmon 9.4.0 |
| Pixelmon | 9.4.0 |
| FutureShops | 2.4.0 |
| Provider setting | `economy.provider = "pixelmon"` |

Selection is restart only. A missing Pixelmon installation or any version other than `9.4.0` does not register the adapter. FutureShops keeps the configured identifier and reports the provider as missing on that start. It does not fall back to `internal` during the lifecycle.

## Capability boundary

The exact Pixelmon API exposes `BankAccountProxy.hasImplementation()`, `getBankAccountNow(UUID)`, `BankAccount.getIdentifier()`, `getBalance()`, and `hasBalance(BigDecimal)`. FutureShops maps these to authoritative balance query and non mutating precheck capabilities. Balances must convert to an exact integer PokéDollar amount. Null accounts, identity mismatches, thrown calls, fractional values, and overflow return typed unavailable results. They never become zero or an inferred insufficient funds result.

The same API exposes boolean `take` and `add` methods, but it has no durable request identity, operation receipt, receipt lookup, or idempotent retry. The unmodified account path therefore remains query and precheck only. Its mutation, lookup, and retry methods return `CAPABILITY_MISSING` without invoking `take` or `add`.

When the exact Pixelmon `9.4.0` `PlayerPartyStorage` class is transformed by the optional FutureShops mixin, the native account declares the complete mutation capability set. The mixin carries the FutureShops request UUID into the account, writes a pending receipt beside Pixelmon's `pixelDollars` data, forces Pixelmon's save adapter before the external effect, applies the native `add` or `take`, writes the completed receipt, and forces the save adapter again. A repeated request returns the stored receipt without a second balance change. A pending receipt, save failure, malformed record, unknown state, duplicate request, or contradictory record returns `RECOVERY_REQUIRED` and keeps the account unavailable for new writes. The receipt records use `pixelmon:<request uuid>` as the external operation identity and survive `PlayerPartyStorage` reload.

For a custom or hybrid `BankAccountProxy` account, the provider precheck rejects `CAPABILITY_MISSING` before write ahead journal intent, item custody, inventory movement, claims, analytics, success events, or external calls. For an unmodified account, the coordinator capability gate makes the same refusal. Buy, sell, cart, player shop, `/pay`, fees, refunds, physical money, and administrative value changes are available only for the exact native receipt account. Query only screens and diagnostics may continue to show the external balance for other account types.

Money items and ATM behavior remain internal provider features. They are inert when an external provider is selected. FutureShops never creates a balance mirror or mints a Pixelmon backed physical currency item.

## Installation and rollback

Install Pixelmon 9.4.0 and FutureShops 2.4.0 on the server. The same FutureShops jar must be present on clients. Back up the complete world, `config/futureshops`, the FutureShops jar, and Pixelmon economy data before changing provider selection. Set the provider, stop the server, replace the configuration, and restart. A provider change never migrates internal balances.

To roll back, stop the server, restore the matching backup, set `economy.provider = "internal"`, and restart. Do not delete the FutureShops journal, custody, claims, escrow, or world data to force a provider change. If a Pixelmon query is unavailable, correct the exact installation or return to the matching internal backup. Never guess a refund from a local balance snapshot.

## Evidence

The reviewed Pixelmon artifact remained outside this repository and was used unchanged. Its SHA 256 is `9020393f98382ae8794ef2694e7bec1984c1a0eca735ea3eea06e0cb151c61f2`. Exact class and bytecode inspection was limited to interoperability research. FutureShops ships only its original adapter and mixin code, never copied Pixelmon code, assets, or jar bytes.

* SHA 256, `9020393f98382ae8794ef2694e7bec1984c1a0eca735ea3eea06e0cb151c61f2`.
* SHA 512, `b1485031c27cbe0dd7125f11d3b003954e654f66c102479d443841071a37131067371bfc5e1fc2d8bf96a7195afa3ca02fc1525d343fc096d5bc598680bccafe`.

The exact API map and negative direct mutation classification are recorded in [Phase 002 Pixelmon API evidence](../verification/phase-002/pixelmon-api-2026-09-03.md). The native mixin, reload, retry, unknown-record recovery, Vault proof fixture, and headless debug procedure are recorded in [Phase 002 integration evidence](../verification/phase-002/p002-integration-evidence-2026-09-04.md). The exact SQLite backend and hybrid startup transaction are recorded in [exact hybrid Vault proof](../verification/phase-002/vault-hybrid-proof-2026-09-05.md). The available hybrid bridge stack is classified in the [Phase 002 bridge review](../verification/phase-002/bridge-review-2026-09-03.md). The owner authorized the exact disposable terms before both full launches, as recorded in the [Phase 002 runtime terms authorization](../verification/phase-002/runtime-terms-2026-09-03.md). The unmodified PixelmonEconomyBridge and FinalEconomy stack remains refused because it does not expose the required durable receipt and idempotent retry contract. No unmodified direct Pixelmon `add` or `take` call is made by the provider.

The current `2.4.0` artifact reached readiness with the exact Pixelmon profile and with the exact hybrid Vault stack plus the separate proof registrant. The hybrid run proves public `vault` registration and one request aware SQLite balance and receipt transaction. It does not certify the unmodified PixelmonEconomyBridge and FinalEconomy stack for production mutations. The [Phase 000 Pixelmon environment verification](../verification/phase-000/p000-task-011-2026-09-04.md) records the live query and typed refusal, and the [Phase 000 hybrid environment verification](../verification/phase-000/p000-task-012-2026-09-04.md) records plugin lifecycle startup and the same refusal through the legacy hybrid stack. These runs used unmodified external jars and a disposable proof component outside the FutureShops artifact.

# phase 002 pixelmon api evidence

## Artifact identity

The exact Pixelmon 1.21.1 9.4.0 universal artifact was inspected outside the repository at `/tmp/Pixelmon-1.21.1-9.4.0-universal.jar`.

| Field | Value |
| --- | --- |
| Mod id | `pixelmon` |
| Version | `9.4.0` |
| Minecraft range | `[1.21.1,1.21.2)` |
| NeoForge range | `[21.1.0,)` |
| Java range | `[21,)` |
| SHA 256 | `9020393f98382ae8794ef2694e7bec1984c1a0eca735ea3eea06e0cb151c61f2` |
| SHA 512 | `b1485031c27cbe0dd7125f11d3b003954e654f66c102479d443841071a37131067371bfc5e1fc2d8bf96a7195afa3ca02fc1525d343fc096d5bc598680bccafe` |

The artifact is evidence input only. It is not copied into `libs`, declared as a Gradle dependency, or bundled in the FutureShops jar. The [CurseForge listing](https://www.curseforge.com/minecraft/mc-mods/pixelmon) identifies Pixelmon as All Rights Reserved, so the jar remains outside the repository. Owner terms acceptance for the exact disposable runtime was recorded before the full launch in [Phase 002 runtime terms authorization](runtime-terms-2026-09-03.md).

An incompatible 1.21.1 Pixelmon 9.3.1 universal artifact was separately acquired from the [Modrinth 9.3.1 release](https://modrinth.com/mod/pixelmon/version/9.3.1) for the negative registration check. It is licensed All Rights Reserved and remains outside the repository. Its SHA 256 is `bc96795ce283da39a92c5110275498c93d207a9f773c31286bdbdd85ca5df315`, its SHA 512 is `a9630ff8ad3a50d3d70bd958b87984f31336947bf4ea442830579a22c847a3f8af71dc3da02f00b245769ce1a965d33378a7b53aedb82ded9034272362ed4128`, and `unzip -tq` passed. The disposable negative profile was `/tmp/futureshops-pixelmon-incompatible.MapAQG` with the rebuilt FutureShops jar SHA 256 `828100961451f6c17aab94f3408280a88ebf7f10a329281becb692aaf98f22f2` and `eula=true`. The 9.3.1 jar contains its own GeckoLib 4.7.5.1 metadata, so no second GeckoLib jar was installed in that profile.

The official [Pixelmon downloads page](https://pixelmonmod.com/downloads.php) lists this 9.4.0 release and links the public [Pixelmon MDK](https://github.com/EnvyWare/Pixelmon-MDK). The linked MDK main revision `4309ac5fc79b6a167edfc922f055d1b4d2d56744` is an example project configured for Pixelmon 9.3.1 and NeoForge 21.1.170, so it is not an exact 9.4.0 development bundle. The exact interface injection file is available at the official artifact path and was retained outside the repository at `/tmp/Pixelmon-1.21.1-9.4.0-universal-interfaceinjection.json`. Its SHA 256 is `79bc83342ba0a3ee170c2883dbe30910adcb13fb6c73743ab70180ea30f9e666`.

## Reviewed API map

The published [Pixelmon 9.4.0 `BankAccount` API](https://reforged.gg/docs/1211/com/pixelmonmod/pixelmon/api/economy/BankAccount.html) documents `getIdentifier()`, `getBalance()`, `setBalance(BigDecimal)`, `hasBalance(BigDecimal)`, boolean `take(BigDecimal)`, boolean `add(BigDecimal)`, and primitive overload defaults. The [economy event API](https://reforged.gg/docs/1211/com/pixelmonmod/pixelmon/api/economy/EconomyEvent.html) documents balance and transaction events, but no request identity or receipt surface. The exact artifact inspection additionally confirmed that `BankAccountProxy` provides `hasImplementation()`, `getBankAccountNow(UUID)`, and an asynchronous `getBankAccount(UUID)`.

A disposable Java probe compiled successfully against the exact 9.4.0 universal artifact. It exercised the reviewed `BankAccount` type, UUID identity check, exact `BigDecimal` amount construction, balance null guard, and `hasBalance` call. The probe source and classes remain outside the repository at `/tmp/pixelmon-api-probe`.

The complete public economy surface was also enumerated. `BankAccountManager` only supplies synchronous and asynchronous account lookup. `EconomyEvent.PreTransaction` is cancellable and exposes the transaction type, current balance, and mutable change. `EconomyEvent.PostTransaction` exposes the transaction type and old and new balances. `EconomyEvent.SetBalance` is cancellable. None of these manager or event types carries a FutureShops request UUID, an operation token, a durable receipt, a receipt lookup method, or an idempotent retry method. `BankAccount` mutation methods remain direct boolean `take` and `add` calls, and `setBalance` is a direct void setter.

The exact 9.4.0 Pixelmon consumers confirm the same boundary. `BankTransferCommand`, `GiveMoneyCommand`, and `ShopTransactionPacket` call the direct account methods and do not pass a request identity or retain a durable receipt. Their command and shop paths also use primitive numeric overloads and discard the boolean mutation result. These consumers do not provide a transaction coordinator that FutureShops can safely adopt.

The concrete `PlayerPartyStorage` implementation stores the balance as the `pixelDollars` NBT field. `add`, `take`, and `setBalance` update that field, call `updatePlayer`, and mark the storage dirty with `setNeedsSaving`; `add` and `take` then post `PostTransaction`. This is ordinary player-data persistence, not an operation journal. A crash between the field update and a FutureShops confirmation still leaves no external request identity or receipt to reconcile.

The usable strict capabilities are balance query and precheck. The API does not expose an operation UUID, durable receipt, receipt lookup by request identity, or idempotent retry. A local FutureShops request UUID cannot make a boolean Pixelmon call idempotent. `setBalance`, `take`, and `add` are therefore classified as unsafe for production mutation under the current contract.

## Mapping

| Pixelmon operation | FutureShops result |
| --- | --- |
| `hasImplementation` and account lookup | `ProviderReadiness` and typed availability |
| `getIdentifier` and `getBalance` | `BALANCE_QUERY`, exact integer `BalanceSnapshot` |
| `hasBalance` | `PRECHECK`, only for debit kinds |
| `PreTransaction`, `PostTransaction`, and `SetBalance` events | Observability and cancellation only, no durable operation identity |
| `take` | Refused, `WITHDRAW` false |
| `add` | Refused, `DEPOSIT` false |
| no receipt API | `RECEIPT_LOOKUP` false |
| no idempotent request API | `IDEMPOTENT_RETRY` false |

The implementation loads optional classes by runtime class name after exact mod registration. Standard NeoForge startup does not link Pixelmon types. All reflection failures become typed provider failure or unavailable results, and no exception is used to infer a balance.

## Scope decision

This evidence supports exact read only Pixelmon integration in 2.3.0. It does not support a generic Pixelmon, Vault, Bukkit, hybrid, or economy plugin compatibility claim. A later provider can enable mutation only after a reviewed exact API or bridge supplies durable request identity, receipt lookup, idempotent retry, and crash recovery evidence for every enabled surface.

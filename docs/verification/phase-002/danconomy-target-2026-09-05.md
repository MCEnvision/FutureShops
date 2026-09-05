# Phase 002 DanConomy target evidence

This record closes the exact artifact acquisition, provenance, source review, bytecode review, persistence map, and implementation boundary for `P002-TASK-015`.

## Exact inputs

| Field | Value |
| --- | --- |
| Mod | DanConomy |
| Version | `1.2.1` |
| Modrinth version identifier | `9rRHHMnY` |
| Release file | `danconomy-1.2.1.jar` |
| Release size | `161611` bytes |
| Release SHA 256 | `61d3eb69a3a235929ac2376d151130e61ea4fe65c2f84990618c79e27e954b72` |
| Release SHA 512 | `865aba88f26d1a78ec92b4981f9a9b5af701a5f62cb38904d9439138f7a95ac740ce80d6e717b95169e4df6e07bac3893a90ae78de8565e9c168c8b0190713f0` |
| Source repository | `https://github.com/Dandelion1608/danconomy.git` |
| Reviewed source revision | `63aecdac12e437ae1f3de2801cdea0105b3d7e06` |
| Declared Minecraft version | `1.21.1` |
| Declared NeoForge range | `21.1.209` or newer |
| Declared side | Both |
| Declared license | All Rights Reserved |

The release jar and source checkout were used as disposable interoperability research inputs. FutureShops does not alter, rebuild, copy source from, bundle, or redistribute DanConomy. The production artifact contains original FutureShops adapter and mixin code only. The optional Gradle property points at the exact unmodified jar outside the repository, verifies its hash, and supplies it as `compileOnly`.

## Source and bytecode map

Source and release bytecode were inspected together. The relevant release descriptors match the reviewed source behavior.

| Class | Relevant contract |
| --- | --- |
| `com.danners45.danconomy.currency.CurrencyRegistry` | `getDefaultCurrencyId()Ljava/lang/String;`, `get(Ljava/lang/String;)Lcom/danners45/danconomy/currency/Currency;`, and `getAll()Ljava/util/Map;` select the configured currency. |
| `com.danners45.danconomy.currency.Currency` | `getId()`, display names, `getDecimalPlaces()I`, and `getBackingType()` provide immutable provider metadata. |
| `com.danners45.danconomy.currency.Currency$BackingType` | Exact values are `LEDGER` and `PIXELMON_MIRRORED`. |
| `com.danners45.danconomy.data.LedgerData` | `get(ServerLevel)`, `getOrCreateAccount(UUID)`, `getAccount(UUID)`, `load(CompoundTag, HolderLookup.Provider)`, and `save(CompoundTag, HolderLookup.Provider)` own account persistence. |
| `com.danners45.danconomy.account.Account` | `getBalance(String)J`, `setBalance(String,J)V`, `deposit(String,J)V`, and `withdraw(String,J)Z` own ledger balances. |
| `com.danners45.danconomy.economy.EconomyAccess` | Server level overloads expose ordinary balance, funds, withdrawal, and deposit behavior for UUID accounts. |

`LedgerData` uses SavedData identifier `danconomy_ledger`. Its root `accounts` compound maps account UUIDs to account compounds, and each account stores a `balances` map keyed by currency identifier. Ordinary ledger mutations change `Account` and mark `LedgerData` dirty. They expose no request UUID, durable operation receipt, lookup, or retry deduplication.

DanConomy also supports `PIXELMON_MIRRORED`. Online calls may target Pixelmon and mirror values, while UUID level calls may use the DanConomy ledger mirror. That is not one authoritative same image balance and receipt path. FutureShops therefore accepts exact `LEDGER` only and refuses mirrored currencies before capability admission.

## NeoForge durability map

The pinned NeoForge 21.1.248 bytecode was inspected for the SavedData write path. `DimensionDataStorage.save` schedules SavedData output through NeoForge IO utilities. `IOUtilities.atomicWrite` writes a temporary file, forces its file channel, and performs an atomic replacement when supported. `IOUtilities.waitUntilIOWorkerComplete` joins queued SavedData work.

The DanConomy compatibility mixin does not treat an ordinary asynchronous dirty mark as transaction confirmation. It serializes the changed balance and completed FutureShops receipt into one `danconomy_ledger.dat` image, calls the NeoForge atomic NBT writer synchronously, forces the containing directory, reads the bounded image back, and verifies the exact request and balance before acknowledgement.

## Stable mixin boundary

The exact target is `com.danners45.danconomy.data.LedgerData`. The mixin adds an isolated interface used only after runtime version and backing checks. It injects receipt decoding at the return of the exact static `load` descriptor and receipt encoding at the return of the exact `save` descriptor. It does not redirect DanConomy commands, replace ordinary economy calls, or modify DanConomy bytecode on disk.

The mixin plugin applies the target only when mod identifier `danconomy` is loaded at version `1.2.1` and the exact target class is present. Without that dependency, the same FutureShops jar loads normally and the optional target is skipped.

## Result

The reviewed exact target is sufficient for a FutureShops owned request aware `LEDGER` adapter. It is not sufficient for `PIXELMON_MIRRORED`, so that path remains intentionally incompatible. Artifact licensing is handled by the no alteration, no copy, no bundle, and no redistribution boundary and is not an implementation blocker.

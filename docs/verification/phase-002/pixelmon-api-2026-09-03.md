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

The artifact is evidence input only. It is not copied into `libs`, declared as a Gradle dependency, or bundled in the FutureShops jar.

## Reviewed API map

`com.pixelmonmod.pixelmon.api.economy.BankAccountProxy` provides `hasImplementation()`, `getBankAccountNow(UUID)`, and an asynchronous `getBankAccount(UUID)`. `com.pixelmonmod.pixelmon.api.economy.BankAccount` provides `getIdentifier()`, `getBalance()`, `setBalance(BigDecimal)`, `hasBalance(BigDecimal)`, boolean `take(BigDecimal)`, boolean `add(BigDecimal)`, and primitive overload defaults.

The usable strict capabilities are balance query and precheck. The API does not expose an operation UUID, durable receipt, receipt lookup by request identity, or idempotent retry. A local FutureShops request UUID cannot make a boolean Pixelmon call idempotent. `setBalance`, `take`, and `add` are therefore classified as unsafe for production mutation under the current contract.

## Mapping

| Pixelmon operation | FutureShops result |
| --- | --- |
| `hasImplementation` and account lookup | `ProviderReadiness` and typed availability |
| `getIdentifier` and `getBalance` | `BALANCE_QUERY`, exact integer `BalanceSnapshot` |
| `hasBalance` | `PRECHECK`, only for debit kinds |
| `take` | Refused, `WITHDRAW` false |
| `add` | Refused, `DEPOSIT` false |
| no receipt API | `RECEIPT_LOOKUP` false |
| no idempotent request API | `IDEMPOTENT_RETRY` false |

The implementation loads optional classes by runtime class name after exact mod registration. Standard NeoForge startup does not link Pixelmon types. All reflection failures become typed provider failure or unavailable results, and no exception is used to infer a balance.

## Scope decision

This evidence supports exact read only Pixelmon integration in 2.3.0. It does not support a generic Pixelmon, Vault, Bukkit, hybrid, or economy plugin compatibility claim. A later provider can enable mutation only after a reviewed exact API or bridge supplies durable request identity, receipt lookup, idempotent retry, and crash recovery evidence for every enabled surface.

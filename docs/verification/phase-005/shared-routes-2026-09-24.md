# Phase 005 shared route evidence

## Candidate

The candidate is the Forge 1.20.1 phase branch at commit `5bdb4b1`.
The current packaged jar is `build/libs/futureshops-3.0.0-beta.2.jar`.
Its SHA-256 is `5d4cc96dc39351169e257f9e43011fac9b257c42ebb1b23dfad730e84f408f21`.
Its SHA-512 is
`c911912fde3bae1edbc6c09ea5f5284527dc782fa97a7d5c5e187692081a66d50da6e326c609a5778cbd7745c1a601bec59db377e96e234e96f46762cbef149c`.

## Route disposition

| Route | Internal provider | Ready selected provider | Unavailable or unsupported provider |
| --- | --- | --- | --- |
| `balance` and `bal` | Existing internal wallet query | Resolved provider balance query | Typed unavailable error, no internal fallback |
| `baltop` | Existing ranked wallet query | Selected provider leaderboard when supported | Typed refusal, never an empty success |
| `shopadmin bal` and `ShopModAPI` credit or debit | Existing audited wallet mutation | Selected provider single account request with the supplied request identity | Refused before internal wallet mutation |
| `shopadmin bal set`, reset, and transfer | Existing audited wallet mutation | Refused until the provider exposes an atomic multi account contract | Refused before internal wallet mutation |
| `pay` | Existing escrow payment and atomic internal transfer | Refused before escrow commit because API v1 has no atomic two account operation | Refused before escrow commit |
| deposit, withdraw, ATM, cash claims, and physical funding | Existing protected and configured foreign cash | Refused before custody, mint, claim, account, event, analytics, or WAL effects | Refused before effects |

The selected ready provider is no longer resolved and discarded. Its currency metadata and
authoritative balance queries are exposed through the existing command and API projections. Single
account mutations retain the caller request UUID and use the provider receipt path. No external
operation is copied into the internal wallet. Multi account operations remain explicitly refused
until an atomic provider contract is available.

## Static bypass audit

The production source inventory found the four direct `BalanceManager` mutation call sites in
`LiveAdministrativeBalanceBackend`. No other production source directly calls the internal wallet
credit, debit, transfer, or set methods. Payment now checks the selected provider before escrow
commit. Physical route checks exist at the command, ATM access and mutation, cash deposit,
recovery, withdrawal, money claim, and cash claim boundaries. A dedicated Forge 1.20.1 server
started successfully from the candidate source on Java 17, reached `Done`, loaded FutureShops,
and generated its disposable world and server configuration under the phase runtime. The server
was stopped after readiness and the exact runtime was removed.

## Verification

```text
./gradlew test --tests com.enviouse.futureshops.server.economy.SelectedEconomyProviderTest --no-daemon
BUILD SUCCESSFUL

./gradlew test --no-daemon
BUILD SUCCESSFUL

./gradlew build --no-daemon --max-workers=1
BUILD SUCCESSFUL
```

The focused adapter tests prove selected provider balance ownership, request based debit and
credit, and fail closed cross account transfer. The complete Forge unit suite and packaged build
also pass. The dedicated server smoke proves the Forge 1.20.1 runtime reaches readiness with the
candidate code and no startup crash. No graphical client was started because this phase owns only
server authoritative routes. The remaining phase gate is the final cumulative matrix and review.

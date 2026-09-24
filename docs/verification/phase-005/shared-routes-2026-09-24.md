# Phase 005 shared route evidence

## Candidate

The candidate is the Forge 1.20.1 phase branch at commit `5bdb4b1`.
The current packaged jar is `build/libs/futureshops-3.0.0-beta.2.jar`.
Its SHA-256 is `4242543ea9b3233b1a8ec77b9ab21e0e5aa4ec7c756a6d2c2763bc6b07befbee`.
Its SHA-512 is
`da2f37f9ef13ccee910bf9bcb39293676c2bdda5e746d915190702efa9117b65a8698c53da496c4c33e77f7ba2d19fac2add823c668fd0f7b3831513b80824f4`.

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

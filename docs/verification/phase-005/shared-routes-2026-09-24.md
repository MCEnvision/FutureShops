# Phase 005 shared route evidence

## Candidate

The candidate is the Forge 1.20.1 phase branch at commit `2d573f2`.
The current packaged jar is `build/libs/futureshops-3.0.0-beta.2.jar`.
Its SHA-256 is `bb94b8a1985976f47a215a3b9d1dfcb00a9b259313e0be6f8bf8a5f4c12cd3cc`.
Its SHA-512 is
`deb3fe3ebf5726ef28e730a15a18a9823ba2860c58501d3f36fd39828d97f7aa1f2bc52cb01da88ba7e32393ea4d6928f9995ab17850b9030caaf661d87ae033`.

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

Unadapted server shop, player shop, Bazaar, and Auction House money routes now refuse fresh
external-provider mutations before inventory, escrow, or ledger work. Existing replay and
cancellation paths remain readable so stored internal liabilities can be resolved without creating
new external or internal value.

## Static bypass audit

The production source inventory found the four direct `BalanceManager` mutation call sites in
`LiveAdministrativeBalanceBackend`. No other production source directly calls the internal wallet
credit, debit, transfer, or set methods. Payment now checks the selected provider before escrow
commit. Physical route checks exist at the command, ATM access and mutation, cash deposit,
recovery, withdrawal, money claim, and cash claim boundaries. A dedicated Forge 1.20.1 server
started successfully from the candidate source on Java 17, reached `Done`, loaded FutureShops,
and generated its disposable world and server configuration under the phase runtime. The server
was stopped after readiness and the exact runtime was removed. Fresh unadapted shop and market
money admission checks are covered by `PaymentSourceRegressionTest` and the direct service guard
tests.

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

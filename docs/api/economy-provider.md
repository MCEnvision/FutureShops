# FutureShops economy provider API

The public provider contract is in `com.enviouse.futureshopsp.api.economy`. It is provider neutral and does not expose FutureShops internal economy, Pixelmon, Bukkit, Vault, or bridge classes. A provider may be supplied by another NeoForge mod through the registry contract defined in the active phase plan.

## Compatibility

`EconomyApi.COMPATIBILITY_VERSION` is `1`. A provider must report the same compatibility version before it can be registered. Provider identifiers are lowercase resource identifiers between two and sixty four characters. The identifiers `internal` and `vault` are reserved by FutureShops.

## Provider contract

`EconomyProvider` exposes the stable identifier, compatibility version, currency metadata, immutable capabilities, lifecycle readiness, balance queries, prechecks, withdrawals, deposits, durable receipt lookup, and idempotent retry. All methods are server authoritative and must execute on the logical server thread. Implementations must not block that thread on unbounded or remote work.

The six capabilities are independent.

| Capability | Meaning |
| --- | --- |
| `BALANCE_QUERY` | A provider can return an authoritative balance. |
| `PRECHECK` | A provider can validate funds and the requested operation without mutating value. |
| `WITHDRAW` | A provider can debit a request amount. |
| `DEPOSIT` | A provider can credit a request amount. |
| `RECEIPT_LOOKUP` | A provider can find the durable outcome for a request identity. |
| `IDEMPOTENT_RETRY` | A provider can repeat the same request identity without duplicating its effect. |

Capabilities are not inferred. A balance query does not imply a mutation, and a boolean mutation result does not prove a receipt or safe retry.

## Registration and selection

Optional integrations call `EconomyProviderRegistry.register` before the registry freezes. Registration is deterministic and rejects invalid identifiers, duplicate identifiers, incompatible arguments, and late calls. `registerVault` is the only public path for the reserved `vault` identifier. The registry exposes a sorted immutable snapshot and resolves each factory once for a server lifecycle. A factory that throws, reports another identifier, or reports an unsupported compatibility version is unavailable or incompatible.

The server configuration key is `economy.provider`. An absent value selects `internal`. A reload stages a new identifier and reports that a restart is required. It never changes the active identifier during the current server lifecycle. Unknown or malformed identifiers remain selected for diagnostics and do not silently fall back to `internal`.

## Values and requests

Balances and amounts use signed `long` integer minor units. `MutationRequest` requires a positive amount, a server supplied `RequestId`, an actor UUID, an optional counterparty, and a `MutationKind`. Providers must preserve the request identity across their durable operation and receipt records. A locally generated UUID does not make an external operation idempotent unless the external system binds that identity to lookup and replay behavior.

`EconomyAmounts` provides checked addition, subtraction, multiplication, sign validation, and exact decimal parsing. Lossy precision and overflow are rejected before a provider call.

`CurrencyMetadata` validates singular and plural display names and a decimal precision from zero through six. The selected provider owns this metadata for the server lifecycle.

## Results and recovery

`ProviderResult` is explicit. `CONFIRMED` carries a value and, for mutation results, a `MutationReceipt`. `REJECTED`, `UNAVAILABLE`, `AMBIGUOUS`, and `RECOVERY_REQUIRED` carry a typed `ProviderError` and no implicit balance. An unavailable or rejected result may be retried only when the caller has independently established that no external effect occurred. Ambiguous and recovery required results are not safe to retry automatically.

`MutationReceipt` contains the original request identity, mutation kind, amount, provider operation identity, and an optional resulting balance. The optional balance is evidence only and is never a FutureShops shadow ledger.

## Lifecycle

The provider readiness snapshot exposes `UNRESOLVED`, `READY`, `DRAINING`, `MISSING`, `INCOMPATIBLE`, `FAILED`, `RECOVERING`, `FROZEN`, and `STOPPED`. FutureShops admits monetary operations only when the selected provider is ready and the operation's required capabilities are proven. Registration and selection are frozen before monetary readiness. Unclean startup and unknown outcomes require recovery, and unknown outcomes freeze external mutation until an operator resolves them with evidence.

## Server coordinator

`EconomyTransactionCoordinator` is the server side boundary used by the legacy provider view. It checks lifecycle and capabilities before calling a provider, writes `PREPARED` and `EXTERNAL_PENDING` journal records before a mutation, validates the returned receipt, and records `EXTERNAL_CONFIRMED` and `RESOLVED` only for a matching confirmed result. A duplicate request returns the recorded result without a second provider call. An ambiguous or exception result becomes `UNCERTAIN` and freezes the lifecycle. Recovery performs durable receipt lookup before any retry.

Multi step server shop paths use `executeWithCustody`. It runs the same preflight and mutation pipeline while holding a checksummed delivery entitlement first. A confirmed online buy can advance that entitlement to `DELIVERED` and then `CLAIMED`; a sell can retain it as `HELD` until stock settlement and release it only after the credit is confirmed. Rejected outcomes release custody. Ambiguous outcomes retain custody and freeze the lifecycle for evidence based recovery.

The journal is stored as versioned `futureshops_economy_journal` SavedData with checksums, request identity, mutation state, provider result status, diagnostic, and optional receipt. It contains no external balance field. The clean marker is written only after the lifecycle enters `DRAINING` and the journal, custody, claims, and checkpoint flush gate succeeds. Missing or invalid startup state enters `RECOVERING`.

Item custody and offline proceeds use separate versioned SavedData indexes. `futureshops_economy_custody` stores a bounded item identity, owner, quantity, content hash, and the monotonic `HELD`, `DELIVERED`, `CLAIMED`, or `RELEASED` state. `futureshops_economy_claims` stores the claimant, exact amount, bounded description, and the non expiring `PENDING`, `DELIVERED`, or `RESOLVED` state. `futureshops_player_shop_barter_escrow` stores exact serialized player shop payment or buyback item stacks and advances them through `PREPARED`, `REMOVED`, `STORED`, and `COMPLETE`, or ends in `REFUNDED` or `RECOVERY_REQUIRED`. `futureshops_player_shop_sale_escrow` stores exact serialized sale output stacks and advances them through `PREPARED`, `REMOVED`, `DELIVERED`, and `CLAIMED`, or ends in `REFUNDED` or `RECOVERY_REQUIRED`. Every record has a checksum and a clean marker. Invalid or newer records fail closed and are not interpreted as completed value movement. Repeated hold, delivery, claim, and resolution calls return the existing terminal record when the immutable request fields match.

`BalanceManager` loads the journal, custody, claims, barter escrow, sale escrow, and settlement indexes before provider readiness, marks them unclean before admission, and includes their integrity and incomplete record state in lifecycle resolution. Shutdown drains the coordinator, flushes all indexes, and writes each clean marker only after the complete flush gate passes. Internal fixtures use in memory implementations only when no world exists, such as unit test servers.

Existing internal provider calls are routed through this boundary. The internal adapter supplies the same typed receipt and retry contract, while unresolved external selections stay unavailable. The bundled Pixelmon 9.4.0 adapter supplies authoritative balance queries and non mutating funds prechecks through the exact `BankAccountProxy` API. Its capability descriptor intentionally disables withdrawals, deposits, receipt lookup, and idempotent retry. Every mutation result is a typed capability refusal before journal, custody, inventory, claims, analytics, or event effects, because Pixelmon's boolean `add` and `take` methods cannot prove a durable outcome after a crash. The adapter is isolated behind runtime class loading, does not add a Pixelmon dependency, and does not mirror balances. A separately installed Vault bridge remains subject to the same complete capability and recovery proof before it can register or enable mutations.

The registry, server selection, transaction journal, custody, claims, and surface routing are implemented in their owning phase. This document describes the stable public contract and must be kept aligned with the API source and compatibility tests.

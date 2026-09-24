# Economy coordinator recovery

FutureShops records account bound monetary intent in the existing escrow write ahead journal before it calls a provider. Each root has immutable legs, the original account binding, a request payload fingerprint, and local custody facts. The journal is the local source of truth for intent and liabilities. It is not an external balance mirror.

## Lifecycle

The coordinator accepts new roots only while it is `ready`. During startup recovery it is `recovering`. A missing or contradictory provider receipt, an unclean shutdown, or a storage validation failure places the affected root in `frozen` and prevents new monetary admissions. `draining` is used while a server is stopping. Claims remain readable while a root is unavailable.

Legs move through `prepared`, `submitted`, `confirmed`, `rejected`, `unknown`, `replayed`, `compensating`, `frozen`, and `resolved`. A provider receipt must match the original leg amount and payload fingerprint before a leg is confirmed or custody is released.

## Recovery procedure

1. Stop the affected server and preserve the complete world and `config/futureshops` directory.
2. Start the same FutureShops build with the matching world snapshot.
3. Allow escrow startup to replay the journal. Do not delete the journal, custody, ledger, or claim files.
4. For each submitted or unknown root, query the original provider binding and receipt identity. Never replay a mutation only because a receipt is missing.
5. Leave the root frozen when the provider cannot prove the original effect or returns contradictory evidence.
6. Collect a claim only through the original provider, account, currency, backend, and generation binding. A changed provider selection is a typed refusal, not a redirect.
7. Reopen trading only after lifecycle status is ready and the affected claims and custody facts are reconciled.

Known partial completion creates a durable claim. Claim collection is idempotent and concurrent attempts deliver at most once. Unknown effects are neither retried nor compensated without authoritative outcome facts. A compensation, when a later phase enables it, is a new linked leg with its own intent and receipt.

Malformed, duplicated, truncated, or checksum-invalid records fail closed. The source data is preserved for operator inspection and restoration from one complete matching snapshot. Never reset a journal or grant a starting balance to bypass recovery.

The coordinator fixture in the Forge 1.20.1 beta line exercises intent flush, provider dispatch, receipt lookup, restart, duplicate identity, changed payload, original binding, reentrancy, corruption, and concurrent claim collection. Native provider adapters and production shop and market routes remain gated by their later phase contracts.

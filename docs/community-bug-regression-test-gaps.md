# Community bug regression test gaps

## Durable checkout replay

Cart response retention is now separated behind `CartResponsePolicy` and covered by `CartResponsePolicyTest`, `ShopClientStateCartPolicyTest`, `PlayerShopCartStateCheckoutPolicyTest`, and the correlated packet wire round trip tests. Server and player shop carts use correlated request UUIDs and player line tokens. Matching terminal success clears only acknowledged quantities. Timeouts, stale responses, nonterminal responses, and failures retain unconfirmed lines.

A timed out request remains tracked with its original UUID, exact line snapshot, shop identity, and payment source. The client accepts a delayed matching response and fails closed while the terminal outcome is uncertain. It does not resend, abandon, mutate, clear, or start another checkout.

Safe retry and abandonment remain blocked on durable commerce idempotency. The escrow commerce composite must persist a bounded record keyed by player and operation, reject reuse of a UUID with a different canonical payload, atomically couple the terminal outcome to the committed transaction, and replay the exact terminal response across restart. Only after that exists should the retained retry API be wired to a network resend.

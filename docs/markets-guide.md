# Auction House and Bazaar guide

FutureShops 3.0 adds two markets alongside the Shop: the **Auction House** (player-to-player
auctions, red theme) and the **Bazaar** (commodity order book, green theme). All three share one
market interface — header, search, category rail, balance pill, claims counter — and every value
movement in both markets runs through escrow: money and items are held durably before anything
changes hands, and anything that cannot be delivered becomes a claim instead of disappearing.

Server owners: module toggles and rules live in the config files documented in
[config-3.0-examples.md](config-3.0-examples.md).

## Opening the markets

| Command | Opens |
|---|---|
| `/ah` or `/auctionhouse` | Auction House — Browse |
| `/ah browse` | Browse listings |
| `/ah create` | Create Listing wizard |
| `/ah mine` | My Auctions |
| `/ah bids` | My Bids |
| `/ah watched` | Watched listings |
| `/ah claims` | Claims |
| `/ah history` | History |
| `/bz` or `/bazaar` | Bazaar — Products |
| `/bz products` | Product list |
| `/bz buy` | Buy Orders |
| `/bz sell` | Sell Orders |
| `/bz orders` | My Orders |
| `/bz portfolio` | Portfolio |
| `/bz watched` | Watched products |
| `/bz claims` | Claims |
| `/bz history` | History |

You can also switch between Shop, Bazaar, and Auction House with the module chips in the shared
market header (shown when the server enables `modules.show_module_navigation`). A disabled module
disappears from navigation unless you still have claims in it, in which case it appears as
**Claims only**.

## Payment sources

Wallet balance is the default payment source for both markets, and in this release it is the only
one that works there. The payment picker ("Pay from: Wallet / Inventory Cash") exists in the
shared shell, but market operations answer physical payment with *"That payment source is not
accepted here — pay from your wallet instead."*

Why: a bid or a buy order can live for days. Escrow therefore has to *consume* physical money into
custody at placement — it can never merely check your pockets and leave the cash spendable. The
funding bridge that turns physical bills into escrowed value for long-lived contracts is not in
this release, so markets are wallet-funded for now. Shop purchases, `/deposit`, `/withdraw`, and
the ATM continue to support physical currency as before; deposit cash first if your wealth is in
bills.

## Auction House

### Creating a listing

Run `/ah create` or press **Create Listing** in the footer.

1. Pick an item from your main inventory (armor and offhand slots are not listable).
2. Choose the listing type: **Buy Now** (fixed price), **Timed Auction**, or **Auction with
   Buyout** (timed, with an optional instant-buy price).
3. Enter the price (and buyout, if any) and pick a duration. The wizard offers 1h / 6h / 12h /
   24h / 48h; the server enforces the configured minimum and maximum duration.
4. Review the listing fee shown by the wizard and submit.

The server re-validates everything, then escrow takes custody of the exact item — full NBT
included — plus the listing fee. The listing only becomes publicly visible after custody is
durable; if activation fails, both the item and the fee come back. If the auction ends unsold, the
item returns as a claim, but the listing fee is not refunded.

Default limits (server-configurable): 14 active listings per player, 64 items per listing, one
logical stack per listing. Container items, capability-backed items, and anything on the server's
denied item/tag lists are rejected by default.

### Bidding and Buy Now

- The minimum next bid is the current bid plus the configured increment (default: 1.00, i.e. 100
  minor units flat).
- When you bid, the amount is held in escrow immediately. When someone outbids you, your held
  money comes back as a claim — check the Claims tab if automatic delivery could not reach you.
- Raising your own leading bid holds only the difference.
- You cannot bid on your own listing, and bid retraction is disabled by default.
- Equal bids never replace an earlier accepted bid — first come, first served.
- **Buy Now** locks the listing, refunds any current leading bidder, and settles instantly.
- Anti-sniping is on by default: a valid bid inside the last 60 seconds extends the auction by 60
  seconds (up to 10 extensions / 10 minutes total per auction).

Sellers may cancel an active auction only before its first accepted bid (default policy).

### Expiry and settlement

Auctions run on real time by default and expire on schedule whether or not anyone is online.

- **Sold** — the winner receives the item, the seller receives the proceeds minus the sale tax
  (default 2.5%). Both are delivered automatically when possible; otherwise they wait as claims.
- **Unsold** — the exact item returns to the seller as a claim.
- A committed sale is never erased by a delivery failure — a full inventory or a full wallet just
  means the value waits in Claims.

## Bazaar

The Bazaar trades server-defined products, not arbitrary items. By default a product accepts only
plain, undamaged, untagged stacks; named, damaged, or enchanted variants trade only where the
server has defined an explicit variant product.

### Instant buy and instant sell

Product cards show instant buy, instant sell, spread, and recent volume. Instant orders execute
immediately against the best resting orders, bounded by your maximum spend or minimum proceeds and
by the server's slippage cap (default 10%). What cannot execute within those bounds is not
executed — an instant order never chases the book beyond your limit.

### Limit orders

A limit order rests on the book until it fills, you cancel it, or it expires (default 7 days,
maximum 30). Matching is price-time priority, and crossed orders execute at the resting maker's
price by default.

- **Limit buy** — escrow reserves limit price × quantity plus the maximum possible fee. If your
  order fills below your limit, the price improvement is released back to you.
- **Limit sell** — the items enter escrow custody before the order opens. You cannot "sell" items
  you are still carrying around.
- Orders fill partially; each fill settles through escrow individually, and committed fills never
  reverse.
- **Cancelling returns the unfilled remainder only** — remaining reserved money for a buy,
  remaining held items for a sell. Filled portions are final.
- Expiry behaves like a cancellation of the remainder, and you are notified.

Fees are charged in basis points per fill (default: maker 0.10%, taker 0.25%) and are snapshotted
when the order is accepted — a config change never rewrites a live order's fees. Self-trades are
prevented (your incoming order is cancelled rather than matched against your own resting order,
by default). Products can halt automatically when the price moves outside the configured band;
a halt stops matching but never blocks cancellation or claims.

Default limits: 32 open orders per player (8 per product), 1,000,000 units per order.

## Claims (Lost and Found)

Claims are the safety net for both markets and the wider economy: auction winnings, outbid
refunds, expired listings, Bazaar goods and proceeds, price improvement refunds, and anything a
full inventory or maximum wallet balance could not accept. Open them with `/ah claims`,
`/bz claims`, or the Claims tab and counter in the market header.

- Claims are delivered automatically when you are online and have room; anything else waits.
- Claims never expire, survive going offline, and are idempotent — collecting can never pay twice.
- Claim collection is bounded per request and reports partial success; collect again for the rest.

## When a module is disabled

Disabling a module in `futureshops-common.toml` does not delete anything. The default and
recommended behavior is **freeze**:

- No new listings, bids, orders, or trades; the interface reports "This module is disabled" or
  "Frozen, read only".
- All custody, listings, orders, and history remain exactly as they were.
- Cancellation, refunds, and claims stay available the whole time — a disabled module with
  outstanding claims shows up as **Claims only**.
- Remaining auction time pauses while frozen (configurable via `lifecycle.pause_while_frozen`).
- Re-enabling resumes after recovery and validation.

`lifecycle.disable_mode` can instead be set to `drain` or `cancel_and_refund` per module, but a
config reload never silently mass-cancels contracts.

## Administration

### What exists in this build

- **Module control** — enable/disable per module in `futureshops-common.toml`; all market rules in
  the per-module TOMLs. Config hot-reloads; rule changes apply to new contracts only.
- **Bazaar catalog** — product JSON files under `config/futureshops/bazaar/products/`; removing a
  product retires it (historical orders, fills, and claims are preserved). Product `status` can be
  set to `halted` to stop matching on one product without retiring it.
- **Balances** — `/shopadmin bal add|remove|set|check|reset` runs through journaled administrative
  ledger mutations with explicit confirmation, and every action lands in the immutable
  administrative audit store.
- **Inspection** — `/shopadmin view <player>` opens a player's marketplace dashboard;
  `/shopadmin coinaudit` inspects mint state.
- Internal freeze/resume, product halt, forced expiry, and settlement operations exist inside the
  escrow runtime and market control store, and every administrative mutation requires a reason and
  confirmation when `administration.require_reason` / `require_confirmation` are enabled (both are
  by default).

### `/marketadmin` (alias `/madmin`)

The §13 administrative surface. The command tree stays registered while modules are disabled.

| Subcommand | Level | What it does |
| --- | --- | --- |
| `status` | 2 | Per-module control status + escrow runtime state + open listing/order counts + pending recovery counts |
| `audit [count]` | 2 | Latest administrative audit records (1–50, default 10) |
| `recovery` | 2 | Pending auction/bazaar create-recovery intents with age |
| `freeze <module> <reason…>` | 3 | Freeze a module (timers pause; claims stay available) |
| `resume <module> <reason…>` / `enable <module> <reason…>` | 3 | Return a module to ENABLED |
| `disable <module> <reason…>` | 3 | Drain mode (plan §11): no new value operations, existing ones resolve |
| `sweep` | 3 | Run both expiration schedulers immediately |
| `bazaar product <id> <active\|halted\|retired>` | 3 | Product lifecycle mutation |
| `auction cancel <listingId> <reason…>` | 4 | Forced listing cancel — two-step confirm (re-run within 30s), written reason, immutable audit record, idempotent request id |

Every mutation records a bounded reason; the forced auction cancel routes through the same durable
escrow path as a player cancel and returns the item to the seller as claims. Bid-bearing listings
cannot be force-cancelled in this release (the book refuses; the armed message warns first).

Players reach their claims from the Claims tab, the header counter, `/ah claims`, `/bz claims`, or
the dedicated `/claims` (aliases `/claimall`, `/escrow`) command, which opens the claims view
directly — collection itself always runs inside a route-validated market session.

### Permissions

Market commands are intentionally open: `/ah` and `/bz` (like `/shop`, `/balance`, `/pay`) require
no permission level, so every player can trade and — critically — collect claims. `/marketadmin`
uses vanilla permission levels 2 (read-only), 3 (module control), and 4 (forced value operations):

```
# Console: grant a moderator the admin commands (vanilla op level covers hasPermission(2))
/op ChiefModerator
```

Permission-mod integration with dedicated nodes (auction use/create/bid, bazaar use/order,
escrow claim/admin) remains a follow-up; in this build, command access is vanilla permission
levels only. Two guarantees hold regardless of permissions: revoking a player's access blocks new
actions but never confiscates their claims, and disabling a module never blocks ownership claims.

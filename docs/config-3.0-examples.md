# FutureShops 3.0 configuration examples

FutureShops registers four server (COMMON) TOML files and one client TOML file. Every file is
registered unconditionally, so a disabled module can be configured before it is enabled.

| File | Owns |
|---|---|
| `config/futureshops-common.toml` | Module toggles, economy, currency provider, shop behavior |
| `config/futureshops-escrow.toml` | Recovery, checkpoints, claims, asset bounds, request security |
| `config/futureshops-auction-house.toml` | Auction House rules |
| `config/futureshops-bazaar.toml` | Bazaar market rules |
| `config/futureshops-client.toml` | Client-only presentation and accessibility |

Bazaar product definitions live in `config/futureshops/bazaar/products/*.json` (see
[bazaar-products.md](bazaar-products.md) and the examples at the end of this document). Server shop
catalogs live in `config/futureshops/shops/*.json` as before.

Two rules to keep in mind before editing anything:

- **Escrow has no enable toggle.** Escrow protects every operation that moves money, items, stock,
  or claims, and cannot be disabled while those features are available. The escrow TOML tunes how
  escrow works; nothing in it turns escrow off.
- **Module toggles live only in the main config.** `futureshops-common.toml` `[modules]` is the
  single source of module enablement. The per-module TOMLs contain no enabled flag.

All money values are integer minor units: with the default two `economy.currency_decimals`,
`100` means `1.00` and `100000000000` means `1,000,000,000.00`. Percentage rules use basis
points: `250` basis points is `2.50%`.

## Reload behavior

All five files hot-reload through Forge's config watcher. What a reload can change differs by kind
of setting:

- **Safe immediate reload** — module enablement, branding and accent colors, notifications, page
  sizes, order book depth, rate limits, recovery and delivery work budgets.
- **New contracts only** — fees, taxes, durations, bid increments, expiration windows, price
  ticks, lot sizes, and anti-snipe rules are snapshotted into each listing and order when it is
  created. A reload changes the rules for *new* listings and orders only; every live contract
  keeps the exact rules it was accepted under. Nothing you change here retroactively edits an
  active auction or an open Bazaar order.
- **Restart or migration required** — currency decimals, product identity semantics,
  `bazaar_control` catalog reconciliation, and persistent key or journal formats. The control
  value is enforced immediately for new Browse Items selections, but switching back to admin mode
  reconciles the JSON catalog on server start.

If a reloaded file contains an invalid value, the whole reload for that file is rejected: the last
valid settings snapshot stays active and the server log reports the rejected field, for example
`Rejected FutureShops Bazaar configuration. ...`.

## futureshops-common.toml

```toml
[modules]
	# Enable the Bazaar. Disabling preserves existing orders, custody, refunds, and claims.
	bazaar_enabled = true
	# Enable the Auction House. Disabling preserves existing listings, bids, custody, and claims.
	auction_house_enabled = true
	# Show the Shop / Bazaar / AH switcher chips in the shared market header.
	show_module_navigation = true
	# Module opened by shared market navigation: "shop", "bazaar", or "auction_house".
	# If the chosen module is disabled, navigation falls back to "shop".
	default_module = "shop"

[economy]
	currency_name = "Coins"
	# Decimal places for displayed balances (0-6). Changing this changes what one minor unit
	# means everywhere — treat it as a migration, not a live tweak.
	currency_decimals = 2
	# 100000 minor units = 1,000.00 at two decimals.
	starting_balance_minor_units = 100000
	max_balance_minor_units = 99999999999
	# Only admin /shopadmin bal remove may push a balance negative when true.
	# Player-driven transactions never can, regardless of this setting.
	allow_negative = false

[permissions]
	# Vanilla operator fallback when no permission plugin changes the market nodes.
	# 0 keeps ordinary browsing and trading open to every player.
	market_use_op_level = 0
	# Vanilla operator fallback for market administration nodes.
	market_admin_op_level = 2

[currency]
	# Physical item layer used by /withdraw, /deposit, and the ATM.
	# "futureshops" (protected, default), "apocalypsenow" (preset), or "custom".
	# WARNING: any provider other than "futureshops" disables all FutureShops physical
	# currency duplication protection (no mint IDs, checksums, or spent-mint tracking).
	provider = "futureshops"
	# For provider = "custom": "modid:item=value_in_minor_units", largest handed out first.
	items = []
	# Accepted by /deposit but never dispensed (e.g. block forms of the currency).
	accept_only_items = []

[money]
	# Server-side salt for protected bill checksums. Change before production.
	checksum_salt = "change-me-before-production"
	mint_server_id = "futureshops-dev"
	max_age_days = 365

[session]
	# Distance from the shop block before an open session auto-closes. 0 = disabled.
	max_distance_blocks = 8
	close_on_damage = false

[dynamic_pricing]
	enabled = false
	recalc_interval_sec = 300
	max_increase_pct = 50.0
	max_decrease_pct = 30.0
	demand_weight = 0.6
	supply_weight = 0.4
	decay_rate = 0.95

[stock_refresh]
	enabled = true
	check_interval_sec = 60

[events]
	# Fire ShopTransactionEvent and BarterTradeEvent on every trade.
	transaction_events = true

[player_shops]
	max_link_distance_blocks = 8

[local_listings]
	# Radius searched for player shops by /shop Local Listings. 0 = whole dimension (loaded chunks).
	scan_radius_blocks = 64
```

## futureshops-escrow.toml

Escrow cannot be disabled; these settings tune throughput, durability, and recovery. The journal
and checkpoints these settings govern live in `<world>/futureshops/escrow/` — see
[backup-restore.md](backup-restore.md) before touching that directory.

```toml
[recovery]
	# Recoverable transactions processed per server tick.
	work_per_tick = 64
	# Retry backoff for a recoverable transaction, in server ticks.
	initial_retry_delay_ticks = 20
	maximum_retry_delay_ticks = 1200
	# Above this many pending transaction records, new value mutations fail closed.
	max_pending_transactions = 100000

[migration]
	# Legacy 2.x wallet entries imported per tick during the one-time 3.0 migration.
	wallet_entries_per_tick = 256

[persistence]
	# Target interval between durable escrow checkpoints.
	checkpoint_interval_seconds = 30
	# Verified checkpoint + journal generation pairs kept on disk (minimum 2:
	# the current pair and the previous pair).
	checkpoint_generation_retention = 2
	# A checkpoint is forced when the active journal reaches either threshold.
	checkpoint_maximum_journal_bytes = 67108864
	checkpoint_maximum_journal_records = 50000
	# Retention for terminal transaction audit summaries, in days.
	archive_retention_days = 365

[claims]
	# Attempt automatic delivery when the beneficiary is online.
	# Failed delivery never loses value — it stays claimable.
	automatic_delivery = true
	# Bounds one Claim All style request.
	max_entries_per_request = 64
	delivery_work_per_tick = 32

[assets]
	# Item lots one transaction may hold.
	max_per_transaction = 256
	# Serialized NBT bytes accepted per asset lot, and per transaction in total.
	max_nbt_bytes = 1048576
	max_total_nbt_bytes = 8388608

[storage]
	# Reject external storage operations that cannot provide deterministic
	# reconciliation evidence.
	strict_external_storage = true

[request_security]
	# Player/action rate limit buckets retained per server. Applied at server start.
	tracked_key_cap = 8192
	idle_retention_seconds = 600

	# Each action uses a token bucket: burst "capacity", restoring "refill_tokens"
	# every "refill_period_millis". refill_tokens cannot exceed capacity.
	[request_security.atm_data]
		capacity = 4
		refill_tokens = 1
		refill_period_millis = 1000
	[request_security.atm_withdrawal]
		capacity = 2
		refill_tokens = 1
		refill_period_millis = 2000
	[request_security.atm_cash_collection]
		capacity = 2
		refill_tokens = 1
		refill_period_millis = 2000
	[request_security.atm_deposit]
		capacity = 2
		refill_tokens = 1
		refill_period_millis = 2000
	[request_security.pay]
		capacity = 4
		refill_tokens = 1
		refill_period_millis = 1000

[currency]
	# Refund destination for physical money held by long-lived bids and orders:
	# "wallet_claim" or "original_source". Carries the foreign currency warning
	# in the generated file.
	physical_refund_policy = "wallet_claim"

[audit]
	# "minimal", "standard", or "verbose".
	detail = "standard"

[administration]
	# Administrative recovery actions require a written reason and explicit confirmation.
	require_reason = true
	require_confirmation = true
```

## futureshops-auction-house.toml

```toml
[branding]
	display_name = "Auction House"
	# RRGGBB or AARRGGBB metadata. The client applies one global marketplace theme.
	accent_color = "#9184D9"

[lifecycle]
	# Behavior when modules.auction_house_enabled is false:
	# "freeze" (default, recommended), "drain", or "cancel_and_refund".
	# Freeze stops new listings and bids but keeps custody, cancellation, and claims.
	disable_mode = "freeze"
	# Auction deadline clock: "real_time" or "online_time".
	time_basis = "real_time"
	# Pause remaining auction time while the module is frozen.
	pause_while_frozen = true

[listings]
	allow_buy_now = true
	allow_timed_auctions = true
	# Optional buyout price on timed auctions. Requires allow_timed_auctions.
	allow_auction_buyout = true
	maximum_active_per_player = 14
	# Total item count in one listing.
	maximum_item_count = 64
	maximum_item_nbt_bytes = 262144
	# Total bid value one player may hold in escrow, in minor units.
	maximum_held_value_minor = 100000000000
	minimum_duration_minutes = 5
	maximum_duration_minutes = 10080
	# One to eight unique choices, validated against the duration range and sent to the
	# create wizard. Reloading changes the editor choices for new listings only.
	duration_presets_minutes = [60, 360, 1440, 4320, 10080]
	# Sellers may cancel an active auction only before its first accepted bid.
	allow_seller_cancel_before_bid = true

[bids]
	# Auctions on which one player may hold the leading bid.
	maximum_active_per_player = 50
	# Minimum next bid = current bid + max(fixed increment, basis-point increment).
	minimum_increment_minor = 100
	minimum_increment_basis_points = 0
	maximum_requests_per_second = 8
	# Retraction of the leading bid. Off by default for market safety.
	allow_retraction = false

[anti_snipe]
	# A valid bid inside trigger_seconds of the deadline extends the auction.
	enabled = true
	trigger_seconds = 60
	extension_seconds = 60
	maximum_cumulative_seconds = 600
	maximum_extension_count = 10

[fees]
	# Flat fee charged when a listing is created (escrowed with the item; returned
	# if activation fails). Snapshotted per listing — reloads affect new listings only.
	listing_fee_minor = 100
	# Seller tax on completed sales. 250 basis points = 2.5%.
	sale_tax_basis_points = 250
	# "void" destroys fees; "treasury" routes them to the server treasury account.
	destination = "void"

[payment]
	allow_wallet = true
	# Allow inventory currency for listing fees, bid increases, and Buy Now purchases.
	# Cash is deposited into the wallet through escrow before the auction mutation runs.
	# Protected FutureShops bills use mint validation and spent-mint tracking. Foreign
	# currency uses request deduplication but has no FutureShops duplication protection.
	allow_physical = true
	# "wallet", "physical", or "prompt".
	default_source = "wallet"
	# Destination for unused physical payment value: "wallet_claim" or "original_source".
	physical_remainder_policy = "wallet_claim"

[restrictions]
	# Registry IDs and tag IDs that cannot be listed, e.g. ["minecraft:bedrock"].
	denied_item_ids = []
	denied_item_tags = []
	# Container items: "deny" (default), "allow_empty", or "allow_all".
	container_policy = "deny"
	# Capability-backed items (energy/fluid/inventory capabilities): "deny" or "allow".
	capability_policy = "deny"

[notifications]
	outbid = true
	sold = true
	ending_soon = true

[privacy]
	# Expose bidder identities in public bid history.
	public_bid_history = false

[browse]
	# Listing cards per browse page (4-100).
	page_size = 28
```

## futureshops-bazaar.toml

Global market rules only — individual products are defined in
`config/futureshops/bazaar/products/*.json` (below).

```toml
# "admin" uses the curated JSON catalog. "players" lets players browse registered base items.
bazaar_control = "admin"

[branding]
	display_name = "Bazaar"
	# The unified default matches Server Shop and Auction House.
	accent_color = "#9184D9"

[lifecycle]
	# "freeze" (default), "drain", or "cancel_and_refund" when modules.bazaar_enabled
	# is false. Freeze halts matching but keeps cancellation and claims available.
	disable_mode = "freeze"

[orders]
	allow_buy = true
	allow_sell = true
	# Persistent limit orders.
	allow_limit = true
	# Bounded instant buy / instant sell.
	allow_instant = true
	# Advanced time-in-force modes, off by default.
	allow_immediate_or_cancel = false
	allow_fill_or_kill = false
	maximum_open_per_player = 32
	maximum_open_per_product_per_player = 8
	# Product units per order.
	maximum_quantity = 1000000
	# price × quantity cap per order, in minor units.
	maximum_notional_minor = 100000000000
	# Total Bazaar money held in escrow per player, in minor units.
	maximum_escrowed_value_per_player_minor = 500000000000
	# Persistent order expiration, in hours (defaults to 7 days, capped at 30).
	default_expiration_hours = 168
	maximum_expiration_hours = 720

[products]
	# Used when a product JSON omits lotSize / priceTickMinor.
	default_lot_size = 1
	default_price_tick_minor = 1

[matching]
	# Fills committed per server tick.
	maximum_fills_per_tick = 128
	# Execution price for crossed orders: "maker" (resting order's price, default),
	# "taker", or "midpoint".
	execution_price_policy = "maker"
	# Self-trade prevention: "cancel_taker" (default), "cancel_maker", or "skip_self".
	self_trade_policy = "cancel_taker"
	maximum_order_requests_per_second = 8

[fees]
	# Basis points. Maker rests on the book; taker crosses it. 10 = 0.10%, 25 = 0.25%.
	# Snapshotted per order — reloads affect new orders only.
	maker_basis_points = 10
	taker_basis_points = 25
	destination = "void"

[market]
	# Hard cap on instant-order slippage. 1000 basis points = 10%.
	maximum_slippage_basis_points = 1000
	# Price bands and automatic product halts.
	circuit_breaker = true
	# Deviation from the reference price before a product halts. 5000 = 50%.
	price_band_basis_points = 5000
	circuit_breaker_cooldown_seconds = 300

[history]
	retention_days = 90
	# OHLCV aggregation bucket length.
	bucket_minutes = 15
	maximum_chart_points = 256

[payment]
	allow_wallet = true
	# Allow inventory currency for Bazaar buy orders and instant buys. The exact requested
	# value is deposited through escrow before order custody is created. Protected bills
	# retain mint protection; foreign provider items remain outside FutureShops supply
	# protection. Sell orders always escrow the exact configured product items instead.
	allow_physical = true
	default_source = "wallet"
	physical_remainder_policy = "wallet_claim"

[notifications]
	filled = true
	partial_fill = true
	expired = true

[browse]
	page_size = 28
	# Price levels returned per side of an order book.
	order_book_depth = 20
```

## futureshops-client.toml

Client-only presentation. Accessibility settings always win over server branding.

```toml
[ui]
	use_12_hour_time = false
	# "compact", "normal", or "comfortable".
	density = "normal"
	# "small", "medium", or "large".
	card_size = "medium"
	# Currency symbol shown before market prices. Set this to an empty string to hide it.
	currency_symbol = "$"
	remember_tab = true
	remember_filter = true
	remember_sort = true
	# Remember the payment source for the current client session.
	remember_payment_source = true

[search]
	# Predictive matching while search text is incomplete.
	predictive = true
	# Delay before a remote market search request is sent.
	debounce_millis = 180

[theme]
	# Applies one theme to Server Shop, Player Shops, Bazaar, and Auction House.
	# "server" and "nocturne" use blurple. "emerald" and "crimson" recolor the whole marketplace.
	preset = "server"
	custom_accent_enabled = false
	custom_accent = "#9184D9"

[accessibility]
	high_contrast = false
	# "none", "deuteranopia", "protanopia", "tritanopia", or "monochrome".
	colorblind_mode = "none"
	reduced_motion = false

[motion]
	# 0 disables animation; up to 300.
	animation_speed_percent = 100

[sound]
	enabled = true
	volume_percent = 70

[confirmation]
	# "always", "large_only", or "never".
	purchase_mode = "always"
	# Minimum purchase value that triggers confirmation in large_only mode.
	large_purchase_threshold_minor = 100000
```

## Bazaar product JSON examples

Products are read from `config/futureshops/bazaar/products/*.json` as one atomic catalog. The
loader enforces hard limits: at most 512 files, 2048 product definitions, 256 KiB per file, and
4 MiB for the whole directory. Unknown fields, duplicate JSON fields, and symbolic links are
rejected, so **do not put comments inside the JSON**. Full field semantics are in
[bazaar-products.md](bazaar-products.md); an editor schema is at
[schemas/futureshops-bazaar-product.schema.json](schemas/futureshops-bazaar-product.schema.json).

### Plain commodity

An ordinary stackable resource. `identityPolicy: "commodity"` accepts only tagless, undamaged
stacks — named, damaged, enchanted, container, and capability-backed variants are rejected at the
sell path unless a restriction explicitly allows them. Only `id`, `version`, and `item` are
required; everything else shown here falls back to the Bazaar TOML defaults when omitted.

```json
{
  "schema": 1,
  "id": "emerald",
  "version": 1,
  "item": "minecraft:emerald",
  "category": "gems",
  "displayName": "Emerald",
  "iconItem": "minecraft:emerald",
  "status": "active",
  "identityPolicy": "commodity",
  "lotSize": 1,
  "priceTickMinor": 1,
  "minimumPriceMinor": 1,
  "maximumPriceMinor": 100000000,
  "maximumQuantity": 100000,
  "allowedDimensions": [],
  "restrictions": {
    "allowDamaged": false,
    "allowNamed": false,
    "allowEnchanted": false,
    "allowContainers": false,
    "allowCapabilities": false
  }
}
```

### Explicit NBT variant

`identityPolicy: "exact"` requires `exactNbt`. The SNBT is parsed and canonicalized at load time
(the canonical form must stay within 256 characters), and only stacks matching that exact identity
can enter custody. NBT variants never trade on a commodity product — each variant needs its own
explicit definition. The partial `restrictions` object is valid; omitted flags stay `false`.

```json
{
  "schema": 1,
  "id": "mending_book",
  "version": 1,
  "item": "minecraft:enchanted_book",
  "category": "enchants",
  "displayName": "Mending Book",
  "status": "active",
  "identityPolicy": "exact",
  "exactNbt": "{StoredEnchantments:[{id:\"minecraft:mending\",lvl:1s}]}",
  "lotSize": 1,
  "priceTickMinor": 100,
  "minimumPriceMinor": 100,
  "maximumQuantity": 1000,
  "restrictions": {
    "allowEnchanted": true
  }
}
```

Increase `version` whenever identity or trading rules change; a version already recorded in escrow
can never be redefined, and historical versions must be `retired`. Status changes between
`active`, `halted`, and `retired` do not require a version bump.

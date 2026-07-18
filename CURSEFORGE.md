# 🏪 FutureShops — The Ultimate Minecraft Economy & Shop Mod

### 🎮 Minecraft Forge 1.20.1 · Server-Side Economy · Player Shops · Bartering · Physical Currency

---

> **Transform your Minecraft server into a thriving marketplace.**
> FutureShops brings a fully-featured, Amazon-style economy system to Minecraft — complete with server shops, player-owned storefronts, physical currency, bartering, promo sales, leaderboards, and a sleek modern UI — all without a single external texture.

<!-- ═══════════════════════════════════════════════════════════════════════
     📸 HERO BANNER  (recommended: 1200×400 or wider, landscape)
     Suggested shot: a row of decorated player-shop blocks in a market
     district with the GUI open to one side. This is the first thing
     visitors see, so make it pop.
     ═══════════════════════════════════════════════════════════════════════ -->

---

## 🎬 Trailer / Showcase Video

<!-- ═══════════════════════════════════════════════════════════════════════
     🎥 OPTIONAL VIDEO EMBED
     Paste a YouTube link below — CurseForge will auto-embed it.
     Suggested: 30–60 sec showcase of placing a shop, listing items,
     and a buyer walking up to purchase.
     ═══════════════════════════════════════════════════════════════════════ -->
<!-- https://www.youtube.com/watch?v=YOUR_VIDEO_ID -->

---

## 🆕 What's New in 3.0 — The Markets Update

> **Three markets. One interface. Zero lost items.**
> 3.0 rebuilds the entire economy on top of a crash-safe escrow core, then adds two brand-new ways to trade.

### 🛡️ Escrow-Protected Economy
Every coin and every item that changes hands now moves through **durable escrow** — a write-ahead journal, verified checkpoints, and a double-entry money ledger.

- 💥 **Crash-proof trades** — the server can crash mid-purchase and nobody loses a thing; recovery finishes the trade or returns everything
- 📬 **Claims / Lost and Found** — full inventory? Maxed wallet? Logged off? Your money and items wait for you as claims that **never expire**
- 🔁 **No double-charges** — every request is idempotent; retries and duplicate packets can't charge you twice
- 🧾 **Journaled admin actions** — balance changes are audited, confirmed, and reversible the right way

### 🔨 Auction House (`/ah`)
A crimson-themed, full-featured auction floor.

- ⏱️ **Timed auctions, Buy Now, and auctions with buyout**
- 🔔 **Outbid notifications** with instant escrow refunds
- 🛡️ **Anti-sniping** — last-second bids extend the clock
- 👁️ **Watchlist, My Auctions, My Bids, History** tabs
- 🧊 Exact-NBT custody — the enchanted sword you list is *exactly* the one the winner gets

### 📈 Bazaar (`/bz`)
An emerald-themed commodity market with a real order book.

- ⚡ **Instant buy / instant sell** with slippage protection
- 📊 **Limit orders** with price-time priority matching and partial fills
- 💹 **Spread, volume, trends, and price history** per product
- 🧮 **Maker/taker fees in basis points**, price bands, and circuit breakers
- 🎛️ Server-defined product catalog — commodities by default, explicit NBT variants when you want them

### 🧭 One Shared Market Shell
Shop (blurple), Bazaar (green), and Auction House (red) share one polished interface — header, search, category rail, balance pill, claims counter — with a module switcher, per-module themes, colorblind modes, high contrast, and reduced motion. Each market can be enabled or disabled independently; disabling **freezes** a market (claims and cancellations stay available — nothing is ever deleted).

### ⚠️ Updating to 3.0
- 🔌 **Network protocol is now 45** — the server and **all** clients must update to 3.0 together; mixed versions cannot connect
- 💾 **Back up your world first** — 3.0 performs a one-way migration of balances, mint records, and pending settlements into escrow (see `docs/backup-restore.md` in the repo)
- ⚙️ New config files generate on first launch: `futureshops-escrow.toml`, `futureshops-auction-house.toml`, `futureshops-bazaar.toml`, and `futureshops-client.toml` (see `docs/config-3.0-examples.md`)

### 🎯 First-Release Scope (honest edition)
- 🖥️ **Single server, single world** — cross-server markets would need an external database and are out of scope
- 🎒 **Listings come from your inventory** — auction and Bazaar items are listed from player inventory into a FutureShops vault; listing straight out of linked/RS storage comes once those adapters provide transaction receipts
- 👛 **Markets are wallet-funded for now** — bids and orders can live for days, so physical cash must be *consumed* into escrow rather than merely checked; until that bridge ships, pay markets from your wallet (shops, ATM, and `/deposit` still take physical cash as always)

---

## ✨ Feature Highlights

### 🛒 Server Shop (`/shop`)
Browse an infinite-stock admin shop with a gorgeous dark-themed GUI. Categories, search, filters — it's like shopping online, but in Minecraft.

- 📂 **Category sidebar** with scrollable tabs and item icons
- 🔍 **Real-time search** — find any item instantly
- 🏷️ **Animated promo badges** — pulsating red `-X%` discount chips on sale items
- 🛍️ **Shopping cart** — queue up purchases, adjust quantities, checkout all at once
- 📜 **Transaction history** — filterable log with search, sort, time-window, and push updates
- 💰 **Balance display** — always know how much you've got

<!-- ═══════════════════════════════════════════════════════════════════════
     📸 SCREENSHOT — ShopMainScreen
     Show: main grid, category sidebar, promo badges, balance footer.
     ═══════════════════════════════════════════════════════════════════════ -->
![Server Shop — main grid](https://placehold.co/900x500/1E293B/F8FAFC?text=Server+Shop+%E2%80%94+ShopMainScreen)

---

### 🏠 Player-Owned Shop Blocks
Place a **Shop Block**, configure your listings, link your chests, and open for business.

- 👤 **Owner assignment on placement** — your block, your shop
- 📦 **Single or multi-item mode** — sell one item or up to 12 per block
- 🏷️ **Custom shop names** — brand your storefront
- 💵 **Three trade modes**: Money, Barter, or **Both** simultaneously
- 🔗 **Storage linking** — link chests, barrels, or an entire **Refined Storage network** for automatic stock management
- 📊 **Owner dashboard** — revenue tracking, settlement claims, stock alerts
- 🎁 **Owner promo editor** — set percentage discounts, Buy-X-Get-Y deals, flash sales, and scheduled windows
- 👁️ **Owner preview** — Shift+right-click to test your shop as a visitor
- 🛒 **Buyer experience** — same polished UI as the server shop, familiar and intuitive

<!-- ═══════════════════════════════════════════════════════════════════════
     📸 SCREENSHOT — Shop Block in-world
     Show: placed shop block with the 3D GeckoLib model, spinning listing
     preview, floating nameplate, and owner skin decal on the front.
     ═══════════════════════════════════════════════════════════════════════ -->
![Shop Block — in-world](https://placehold.co/900x500/1E293B/F8FAFC?text=Shop+Block+%E2%80%94+3D+model+%2B+nameplate+%2B+owner+head)

<!-- ═══════════════════════════════════════════════════════════════════════
     📸 SCREENSHOT — PlayerShopBlockScreen (owner config)
     Show: listings list, price/barter fields, storage-link status.
     ═══════════════════════════════════════════════════════════════════════ -->
![Player Shop — owner config](https://placehold.co/900x500/1E293B/F8FAFC?text=Player+Shop+%E2%80%94+Owner+Config+Panel)

---

### 💰 Full Economy System
A server-authoritative economy that keeps your server fair.

- `/balance` — check your funds with a beautiful dashboard
- `/pay <player> <amount>` — send money to friends
- `/baltop` — leaderboard with player heads, top balances, most transactions, top sellers, and most popular products
- `/withdraw <amount> [yes|no]` — convert balance to physical coins
  - **Smart denominations**: $1, $5, $10, $20, $50, $100, $1,000 bills
  - Example: `/withdraw 132 yes` → 1×$100 + 1×$20 + 1×$10 + 2×$1
- `/deposit` — convert coins back to balance

<!-- ═══════════════════════════════════════════════════════════════════════
     📸 SCREENSHOT — BalanceOverviewScreen
     Show: player head, balance, revenue, shop count, low-stock warnings.
     ═══════════════════════════════════════════════════════════════════════ -->
![Balance Dashboard](https://placehold.co/900x500/1E293B/F8FAFC?text=Balance+Dashboard+%E2%80%94+profile+%2B+revenue)

<!-- ═══════════════════════════════════════════════════════════════════════
     📸 SCREENSHOT — BalTopOverviewScreen
     Show: top 10 with player heads, transaction/seller/product spotlights.
     ═══════════════════════════════════════════════════════════════════════ -->
![Leaderboard — /baltop](https://placehold.co/900x500/1E293B/F8FAFC?text=Leaderboard+%E2%80%94+Top+10+%2B+Spotlights)

---

### 🪙 Physical Currency (CoinItem)
Hold your wealth in your hands — or trade it in person.

- 🖱️ **Right-click to deposit** — simply right-click any coin in your hand to instantly deposit its full value into your balance. No commands needed!
- 🔐 **Anti-dupe protection** — every coin is minted with a unique ID, cryptographic checksum, and server-side spent-mint tracking
- 📝 **NBT-based denominations** — coins show their value on hover
- ♻️ **Deposit validation** — previously spent mint IDs are permanently rejected and destroyed
- 🗑️ **Invalid coin auto-destroy** — tampered or duplicated coins are automatically destroyed on use

<!-- ═══════════════════════════════════════════════════════════════════════
     📸 SCREENSHOT — Coin Item showcase
     Reference file: Mod Implementation/DollarBIll.png
     Show: a Dollar Bill in hand / in inventory with the tooltip
     visible (value + "Right-click to deposit" hint).
     ═══════════════════════════════════════════════════════════════════════ -->
![Coin Item — Dollar Bill](https://placehold.co/600x600/1E293B/F8FAFC?text=CoinItem+%E2%80%94+Dollar+Bill+%28Mod+Implementation%2FDollarBIll.png%29)

---

### ⚒️ Bartering System
Not everything has to be about money.

- 🔄 **Item-for-item trades** — trade diamonds for emeralds, or whatever you configure
- 🎨 **Dedicated barter screen** — clean half-and-half layout: "You Receive" ⟵⟶ "You Give"
- 📋 **Multiple recipes per item** — tabs for different trade options
- 🤝 **Works in player shops too** — owners can set barter items and quantities
- 🧮 **Smart Max button** — auto-calculates the most you can trade based on your inventory

<!-- ═══════════════════════════════════════════════════════════════════════
     📸 SCREENSHOT — BarterScreen
     Show: the half-and-half "You Receive ⟵⟶ You Give" layout with
     recipe tabs and the Max button.
     ═══════════════════════════════════════════════════════════════════════ -->
![Barter Screen](https://placehold.co/900x500/1E293B/F8FAFC?text=Barter+%E2%80%94+You+Receive+%E2%9F%B5%E2%9F%B6+You+Give)

---

### 📊 Dashboard & Leaderboards
Know where you stand in the economy.

- 🧑 **Player profile** — your head, balance, revenue, shop count, supply alerts
- 🏆 **Baltop leaderboard** — top 10 balances with player heads
- 📈 **Transaction spotlight** — most transactions, top seller, most popular product
- 🏪 **Storefront management** — see all your placed shops, stock levels, and low-supply warnings

<!-- ═══════════════════════════════════════════════════════════════════════
     📸 SCREENSHOT — FranchiseManagementScreen
     Show: all of your owned shops listed with stock status and revenue.
     ═══════════════════════════════════════════════════════════════════════ -->
![Franchise Management](https://placehold.co/900x500/1E293B/F8FAFC?text=Franchise+Management+%E2%80%94+Multi-shop+Overview)

---

### 🎉 Promo & Discount System
Run sales like a real business.

- 💥 **Percentage discounts** — animated pulsating red badges with `-X%` text
- 🎁 **Buy-X-Get-Y** — promotional bundles
- ⏰ **Scheduled windows** — set start/end times for flash sales
- 🔥 **Flash sale toggle** — limited-time urgency
- 🏷️ **Works everywhere** — server shop, player shops, all trade modes

<!-- ═══════════════════════════════════════════════════════════════════════
     📸 SCREENSHOT — PromoEditorModal
     Show: the promo editor overlay with type picker, value slider,
     BxGy config, and start/end time fields.
     ═══════════════════════════════════════════════════════════════════════ -->
![Promo Editor](https://placehold.co/900x500/1E293B/F8FAFC?text=Promo+Editor+%E2%80%94+Discount+Builder)

<!-- ═══════════════════════════════════════════════════════════════════════
     📸 OPTIONAL ANIMATION / GIF — Pulsating promo badges
     Show: a shop grid with several animated -X% badges.
     GIFs embed fine on CurseForge — use imgur or similar hosting.
     ═══════════════════════════════════════════════════════════════════════ -->
<!-- ![Promo Badges animated](https://i.imgur.com/YOUR_GIF.gif) -->

---

## 🖼️ Full UI Showcase

> *All UI is rendered entirely with Minecraft's built-in drawing primitives — no external textures required!*

| Screen | Description |
|---|---|
| **ShopMainScreen** | Dark modern grid with category sidebar, animated promo badges, profile footer |
| **ItemDetailScreen** | Large item preview, pricing info, quantity controls, buy/sell/barter actions |
| **CartScreen** | Row-based checkout with inline quantity controls and gold-accented totals |
| **BarterScreen** | Half-and-half "You Receive ⟵⟶ You Give" with recipe tabs |
| **TransactionHistory** | Filterable log with ALL/BUY/SELL/BARTER tabs, time windows, search |
| **Player Shop** | Owner config panel + visitor storefront — same polish as server shop |
| **Promo Editor** | Modal overlay for setting discount type, value, BxGy, schedules |
| **Balance Dashboard** | Player head, balance, revenue, shop stats, low-stock warnings |
| **Leaderboard** | Top 10 with heads, transaction/seller/product spotlights |
| **Settlement History** | Paged revenue log with SALE/CLAIM/ROLLBACK filters |

<!-- ═══════════════════════════════════════════════════════════════════════
     📸 SCREENSHOT GRID — Item Detail + Cart + Transaction History
     These are the three most-used buyer-flow screens. Consider a 3×1
     grid or three back-to-back full-width shots.
     ═══════════════════════════════════════════════════════════════════════ -->
![Item Detail](https://placehold.co/900x500/1E293B/F8FAFC?text=Item+Detail+%E2%80%94+Buy+Flow)

![Cart & Checkout](https://placehold.co/900x500/1E293B/F8FAFC?text=Cart+%E2%80%94+Checkout)

![Transaction History](https://placehold.co/900x500/1E293B/F8FAFC?text=Transaction+History+%E2%80%94+Filterable+Log)

---

## 🎮 Commands

| Command | Description |
|---|---|
| `/shop` | Open the server shop storefront |
| `/shop <shopId>` | Open a specific named shop |
| `/balance` or `/bal` | View your balance & dashboard |
| `/balance ui` | Open the full balance dashboard GUI |
| `/baltop` | View the economy leaderboard |
| `/baltop ui` | Open the leaderboard GUI |
| `/pay <player> <amount>` | Send money to another player |
| `/withdraw <amount> [yes\|no]` | Convert balance to physical coin items |
| `/deposit` | Convert held coins back to balance |
| `/link` | Confirm storage link (look at chest first) |

### 🔧 Admin Commands

| Command | Description |
|---|---|
| `/shopadmin reload` | Hot-reload shop catalog from config |
| `/shopadmin coinaudit` | Inspect active vs consumed mints per player |
| `/shopadmin promo set <shop> <item> <type> <value>` | Set a promo on a catalog item |
| `/shopadmin promo clear <shop> <item>` | Remove a promo from a catalog item |

---

## ⚙️ Configuration

Shop catalogs are defined in JSON files at:
```
config/futureshops/shops/*.json
```

Each file defines categories, items, prices, barter recipes, promo rules, and stock settings. Hot-reloadable with `/shopadmin reload`.

Server economy config lives in:
```
config/futureshops-common.toml
```

---

## 🔒 Security & Anti-Dupe

FutureShops takes server integrity seriously:

- ✅ **Server-authoritative transactions** — all buy/sell/barter validated server-side
- ✅ **Cryptographic coin checksums** — tampered coins are rejected
- ✅ **Spent-mint registry** — deposited coins are permanently tracked, preventing re-use
- ✅ **Per-shop transaction locking** — prevents race conditions on player shops
- ✅ **Staged rollback** — failed transactions cleanly revert items and balance
- ✅ **Raycast storage linking** — anti-exploit validation on linked containers
- ✅ **Session management** — shops auto-close on logout, death, or distance

---

## 📦 Installation

1. Install **Minecraft Forge 1.20.1** (47.x)
2. Install the **GeckoLib 4.4+** dependency (required on both client and server)
3. Drop the `futureshops-1.0.jar` into your `mods/` folder
4. Launch the game — shop catalog and config files auto-generate
5. Configure your shops in `config/futureshops/shops/`
6. Use `/shop` in-game to open the storefront — you're in business! 🎉

---

## 🪙 Coin System — Full Documentation

FutureShops features a **physical currency system** that lets players hold, trade, and manage wealth as real items in their inventory.

### How Coins Work

Coins are special items with **no crafting recipe** — the only way to create them is through the `/withdraw` command. Each coin carries hidden NBT data that makes it unique and tamper-proof.

### Getting Coins

| Method | How |
|---|---|
| **Withdraw from balance** | `/withdraw <amount>` or `/withdraw <amount> yes` |
| **Single large coin** | `/withdraw <amount> no` — one coin worth the full amount |

**Smart denomination breakdown** (`/withdraw 132 yes`):

| Bill | Count |
|---|---|
| $100 | ×1 |
| $20 | ×1 |
| $10 | ×1 |
| $1 | ×2 |

Available denominations: **$1, $5, $10, $20, $50, $100, $1,000**

### Depositing Coins

There are **two ways** to deposit coins back into your digital balance:

| Method | How | Details |
|---|---|---|
| 🖱️ **Right-click** | Hold a coin and right-click | Instantly deposits the entire stack in your hand. The fastest way! |
| ⌨️ **Command** | `/deposit` | Deposits **all** valid coins in your inventory at once |
| ⌨️ **Partial command** | `/deposit <amount>` | Deposits only the specified amount, consuming largest bills first |

### Coin Tooltip

When you hover over a coin in your inventory, you'll see:
- 🟡 **Value: $X.XX** — the denomination of this coin
- ⬜ **Right-click to deposit** — a reminder of the quick-deposit feature

<!-- ═══════════════════════════════════════════════════════════════════════
     📸 SCREENSHOT — Coin tooltip close-up
     Show: hover tooltip over a coin revealing value + deposit hint.
     ═══════════════════════════════════════════════════════════════════════ -->
![Coin Tooltip](https://placehold.co/700x400/1E293B/F8FAFC?text=Coin+Tooltip+%E2%80%94+Value+%2B+Deposit+Hint)

### Anti-Dupe Security

Every coin minted by FutureShops contains hidden security data:

| NBT Field | Purpose |
|---|---|
| `mint_id` | Unique UUID — no two coins share the same ID |
| `denomination` | The coin's value in minor units |
| `mint_timestamp` | When the coin was created |
| `mint_player` | UUID of the player who withdrew it |
| `mint_server` | Server identity hash |
| `checksum` | Cryptographic integrity check |

**What happens to invalid coins:**

| Scenario | Result |
|---|---|
| Tampered NBT (edited checksum/value) | ❌ Coin destroyed on use |
| Duplicated coin (same mint ID used twice) | ❌ Second copy destroyed on deposit |
| Already-deposited coin (mint ID consumed) | ❌ Coin destroyed, player warned |
| Missing coin data | ❌ Coin destroyed silently |

### Coin Behavior Rules

| Interaction | Behavior |
|---|---|
| **Right-click (use)** | ✅ Instantly deposits into balance |
| **Drop on ground** | ✅ Allowed — 5-minute despawn |
| **Player death** | ✅ Drops like normal items (respects `keepInventory`) |
| **Store in chests/barrels** | ✅ Allowed — validated when extracted |
| **Crafting** | ❌ Cannot be used in recipes |
| **Anvil renaming** | ❌ Blocked by the server |
| **Hopper extraction** | ❌ Blocked by default (configurable) |

---

## 📋 Technical Details

| Property | Value |
|---|---|
| **Mod Loader** | Minecraft Forge |
| **Minecraft Version** | 1.20.1 |
| **Forge Version** | 47.4.20+ |
| **Java Version** | 17 |
| **Mappings** | Official Mojang |
| **Server-side required** | ✅ Yes |
| **Client-side required** | ✅ Yes (for GUI) |
| **Dependencies** | 🦎 GeckoLib 4.4+ (client & server) |

---

## 🤝 Mod Compatibility

### 🧠 Refined Storage integration (shipped)
FutureShops ships with **native Refined Storage support** — link a shop directly to an **RS Controller, Grid, External Storage, or any RS network node**, and the shop will query and pull stock straight from the **entire RS network**.

- 🔌 **Soft dependency** — integration auto-activates when RS is detected; if RS isn't installed, nothing changes.
- 🌐 **Full-network aware** — unlike hopper-style adapters, the RS bridge traverses `INetworkNodeProxy → INetworkNode → INetwork` so your shop sees **every disk and every external-storage container** on the network, not just one block.
- 🧪 **NBT-strict** — enchanted, named, and damage-data items are matched exactly (no more selling the "wrong" diamond pickaxe).
- ⚛️ **Atomic extract/insert** — if anything fails mid-transaction, changes roll back cleanly on the RS network.
- ♻️ **Graceful fallback** — if the RS API shifts, the adapter falls back to the standard Forge `IItemHandler` capability automatically.
- 🏷️ **Supported mod IDs**: `refinedstorage`, `refinedstorage2`, and `refinedstorageaddons`.

> 💡 **Just look at a Controller / Grid / Crafter / External Storage block and run `/link`** — that's it. The shop is now backed by your RS network.

<!-- ═══════════════════════════════════════════════════════════════════════
     📸 SCREENSHOT — Shop linked to an RS network
     Show: a shop block next to an RS Controller/Grid setup, the shop
     GUI open with live stock counts pulled from the RS network.
     Bonus: split-view showing disks on one side and shop inventory
     on the other.
     ═══════════════════════════════════════════════════════════════════════ -->
![Shop linked to Refined Storage](https://placehold.co/900x500/1E293B/F8FAFC?text=Shop+%E2%9C%95+Refined+Storage+%E2%80%94+Network-backed+Stock)

### 🔌 Other storage & transport
- ✅ **Vanilla inventories** — chests, barrels, shulkers: link any `IItemHandler` container
- ✅ **Vanilla hoppers** — pipe items into linked shop storage
- ✅ **Common item-pipe mods** — anything exposing the Forge `IItemHandler` capability works out of the box
- ✅ **CarryOn** — shops can be picked up and moved (ownership preserved)
- ✅ **Dedicated servers** — fully supported; shops persist across restarts
- 🧩 **Extensible** — third-party mods can register their own `ExternalStorageAdapter` via the addon API to expose custom storage networks

<!-- ═══════════════════════════════════════════════════════════════════════
     📸 OPTIONAL — compatible-mods logo strip
     Show: a small horizontal strip of logos of the mods FutureShops plays
     nicely with. Keep each logo consistent in height.
     ═══════════════════════════════════════════════════════════════════════ -->
<!-- ![Compatible Mods](https://placehold.co/900x120/1E293B/F8FAFC?text=Compatible+Mods+%E2%80%94+Logo+Strip) -->

---

## 🗺️ Roadmap

- ✅ ~~Refined Storage integration~~ — **shipped**
- 🔜 Applied Energistics 2 (AE2) adapter
- 🔜 Dynamic pricing system (supply/demand curves)
- 🔜 Stock refresh scheduler (timed restocks)
- 🔜 Public `ExternalStorageAdapter` developer API & event hooks for addon mods
- 🔜 Hopper/pipe compatibility matrix (Create, Mekanism, etc.)
- 🔜 Advanced analytics & export tools
- 🔜 Localization (ES, FR, DE, PT-BR, ZH-CN)

---

## ❓ FAQ

**Q: Does this work on dedicated servers?**
🟢 Yes. Built server-authoritative from day one.

**Q: Will my shops survive a server restart?**
🟢 Yes. Shops, balances, listings, and transaction history are all persisted to disk.

**Q: Can players steal from my shop?**
🔴 No. Only the owner (or server ops) can modify listings or pull from linked storage.

**Q: Can I use this on an existing world?**
🟢 Yes. Add the mod, restart, and your old world loads normally.

**Q: Is it compatible with Fabric?**
🔴 No — this is a **Forge-only** mod.

**Q: Do I need GeckoLib?**
🟢 Yes — GeckoLib 4.4+ is required for the 3D shop block model. CurseForge will prompt you to install it automatically.

**Q: Can I link my shop to a Refined Storage network?**
🟢 Yes! Look at any RS network block (Controller, Grid, External Storage, Crafter, etc.) and run `/link`. The shop will pull stock from your **entire** RS network — all disks, all external-storage containers — NBT-strict.

**Q: What if I don't have Refined Storage installed?**
🟢 Totally fine. RS is a **soft dependency** — everything else works without it. Standard chest/barrel linking still works out of the box.

---

## ❤️ Support & Community

- 🐛 **Found a bug?** Report it on the Issues tab
- 💡 **Have an idea?** Drop it in the comments
- ⭐ **Enjoying the mod?** Leave a rating — it helps a ton!

<!-- ═══════════════════════════════════════════════════════════════════════
     🔗 OPTIONAL LINKS
     Fill in these once the repo/Discord/Ko-fi links are public.
     ═══════════════════════════════════════════════════════════════════════ -->
<!-- - 💬 Discord: https://discord.gg/YOUR_INVITE                            -->
<!-- - 🐙 GitHub:  https://github.com/YOUR_USER/FutureShops                  -->
<!-- - ☕ Support: https://ko-fi.com/YOUR_USER                                -->

---

<!-- ═══════════════════════════════════════════════════════════════════════
     📸 FOOTER IMAGE  (recommended: 1200×200 wide)
     Suggested: a closing shot of a bustling market district at sunset.
     ═══════════════════════════════════════════════════════════════════════ -->

> *Built with ❤️ for the Minecraft community. No external textures were harmed in the making of this mod.* 🟪⬛

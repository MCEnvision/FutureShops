# 🏪 FutureShops — The Ultimate Minecraft Economy & Shop Mod

### 🎮 Minecraft Forge 1.20.1 · Server-Side Economy · Player Shops · Bartering · Physical Currency

---

> **Transform your Minecraft server into a thriving marketplace.**
> FutureShops brings a fully-featured, Amazon-style economy system to Minecraft — complete with server shops, player-owned storefronts, physical currency, bartering, promo sales, leaderboards, and a sleek modern UI — all without a single external texture.

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

### 🏠 Player-Owned Shop Blocks
Place a **Shop Block**, configure your listings, link your chests, and open for business.

- 👤 **Owner assignment on placement** — your block, your shop
- 📦 **Single or multi-item mode** — sell one item or up to 12 per block
- 🏷️ **Custom shop names** — brand your storefront
- 💵 **Three trade modes**: Money, Barter, or **Both** simultaneously
- 🔗 **Storage linking** — link chests/barrels for automatic stock management
- 📊 **Owner dashboard** — revenue tracking, settlement claims, stock alerts
- 🎁 **Owner promo editor** — set percentage discounts, Buy-X-Get-Y deals, flash sales, and scheduled windows
- 👁️ **Owner preview** — Shift+right-click to test your shop as a visitor
- 🛒 **Buyer experience** — same polished UI as the server shop, familiar and intuitive

### 💰 Full Economy System
A server-authoritative economy that keeps your server fair.

- `/balance` — check your funds with a beautiful dashboard
- `/pay <player> <amount>` — send money to friends
- `/baltop` — leaderboard with player heads, top balances, most transactions, top sellers, and most popular products
- `/withdraw <amount> [yes|no]` — convert balance to physical coins
  - **Smart denominations**: $1, $5, $10, $20, $50, $100, $1,000 bills
  - Example: `/withdraw 132 yes` → 1×$100 + 1×$20 + 1×$10 + 2×$1
- `/deposit` — convert coins back to balance

### 🪙 Physical Currency (CoinItem)
Hold your wealth in your hands — or trade it in person.

- 🔐 **Anti-dupe protection** — every coin is minted with a unique ID, cryptographic checksum, and server-side spent-mint tracking
- 📝 **NBT-based denominations** — coins show their value on hover
- ♻️ **Deposit validation** — previously spent mint IDs are permanently rejected

### ⚒️ Bartering System
Not everything has to be about money.

- 🔄 **Item-for-item trades** — trade diamonds for emeralds, or whatever you configure
- 🎨 **Dedicated barter screen** — clean half-and-half layout: "You Receive" ⟵⟶ "You Give"
- 📋 **Multiple recipes per item** — tabs for different trade options
- 🤝 **Works in player shops too** — owners can set barter items and quantities
- 🧮 **Smart Max button** — auto-calculates the most you can trade based on your inventory

### 📊 Dashboard & Leaderboards
Know where you stand in the economy.

- 🧑 **Player profile** — your head, balance, revenue, shop count, supply alerts
- 🏆 **Baltop leaderboard** — top 10 balances with player heads
- 📈 **Transaction spotlight** — most transactions, top seller, most popular product
- 🏪 **Storefront management** — see all your placed shops, stock levels, and low-supply warnings

### 🎉 Promo & Discount System
Run sales like a real business.

- 💥 **Percentage discounts** — animated pulsating red badges with `-X%` text
- 🎁 **Buy-X-Get-Y** — promotional bundles
- ⏰ **Scheduled windows** — set start/end times for flash sales
- 🔥 **Flash sale toggle** — limited-time urgency
- 🏷️ **Works everywhere** — server shop, player shops, all trade modes

---

## 🖼️ UI Showcase

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
2. Drop the `futureshops-1.0.jar` into your `mods/` folder
3. Launch the game — shop catalog and config files auto-generate
4. Configure your shops in `config/futureshops/shops/`
5. Use `/shop` in-game to open the storefront — you're in business! 🎉

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
| **Dependencies** | None — standalone mod |

---

## 🗺️ Roadmap

- 🔜 Dynamic pricing system (supply/demand curves)
- 🔜 Stock refresh scheduler (timed restocks)
- 🔜 Developer API & event hooks for addon mods
- 🔜 Hopper/pipe compatibility matrix (Create, Mekanism, etc.)
- 🔜 Advanced analytics & export tools

---

## ❤️ Support & Community

- 🐛 **Found a bug?** Report it on the Issues tab
- 💡 **Have an idea?** Drop it in the comments
- ⭐ **Enjoying the mod?** Leave a rating — it helps a ton!

---

> *Built with ❤️ for the Minecraft community. No external textures were harmed in the making of this mod.* 🟪⬛


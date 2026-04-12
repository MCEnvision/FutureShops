# MINECRAFT 1.20.1 FORGE — SHOP MOD
## Complete Development Specification
### Client UI · Server Architecture · Economy · Bartering · Physical Currency · Storage Linking · Anti-Dupe

> **Platform:** Minecraft Forge 1.20.1 (47.x)
> **Asset Rule:** ZERO external textures — all UI is rendered with `fill()`, `fillGradient()`, `drawString()`, `renderItem()`, vanilla widgets, and code-drawn primitives. The mod registers custom Items and Blocks that intentionally have no texture (purple-black missing texture) until art is provided later.
> **Version:** 2.0 — Combined & Expanded

---

# TABLE OF CONTENTS

1. [Design Philosophy & Constraints](#1-design-philosophy--constraints)
2. [Rendering Toolkit](#2-rendering-toolkit)
3. [Color Palette & Typography](#3-color-palette--typography)
4. [Screen Architecture & Navigation](#4-screen-architecture--navigation)
5. [ShopMainScreen](#5-shopmainscreen)
6. [ItemDetailScreen](#6-itemdetailscreen)
7. [CartScreen](#7-cartscreen)
8. [ConfirmationModal](#8-confirmationmodal)
9. [TransactionHistoryScreen](#9-transactionhistoryscreen)
10. [BarterScreen](#10-barterscreen--new)
11. [StorageLink GUI](#11-storagelink-gui--new)
12. [Promo & Discount Display System](#12-promo--discount-display-system--new)
13. [Reusable Widget Component Library](#13-reusable-widget-component-library)
14. [Animation & Transition Specifications](#14-animation--transition-specifications)
15. [Input Handling & Keyboard Shortcuts](#15-input-handling--keyboard-shortcuts)
16. [Architecture Overview & Authority Model](#16-architecture-overview--authority-model)
17. [Economy System](#17-economy-system)
18. [Physical Currency Item (CoinItem)](#18-physical-currency-item-coinitem--new)
19. [Barter / Item-Exchange System](#19-barter--item-exchange-system--new)
20. [Shop Block & Block Entity](#20-shop-block--block-entity--new)
21. [Storage Linking System](#21-storage-linking-system--new)
22. [Hopper, Pipe & Mod Compatibility](#22-hopper-pipe--mod-compatibility--new)
23. [Seller / Owner Promo System](#23-seller--owner-promo-system--new)
24. [Shop Configuration System](#24-shop-configuration-system)
25. [Database Schema & Persistence](#25-database-schema--persistence)
26. [Networking Protocol — Packet Specifications](#26-networking-protocol--packet-specifications)
27. [Transaction Processing Engine](#27-transaction-processing-engine)
28. [Session Management](#28-session-management)
29. [Commands & Permissions (Full List)](#29-commands--permissions-full-list)
30. [Dynamic Pricing System](#30-dynamic-pricing-system)
31. [Stock Refresh Scheduler](#31-stock-refresh-scheduler)
32. [Localization](#32-localization)
33. [Event Hooks & Developer API](#33-event-hooks--developer-api)
34. [Anti-Dupe & Security Hardening](#34-anti-dupe--security-hardening--critical)
35. [Performance Considerations](#35-performance-considerations)
36. [Complete Class Structure](#36-complete-class-structure)
37. [Server Startup & Lifecycle](#37-server-startup--lifecycle)
38. [Testing Checklist](#38-testing-checklist)

---

# PART I — CLIENT-SIDE UI SPECIFICATION

---

## 1. Design Philosophy & Constraints

This mod's UI is built **entirely without external image assets**. No PNGs, no SVGs, no custom textures, no resource pack assets. Every pixel on screen is rendered using Minecraft's built-in GUI drawing methods or code-generated graphics.

The mod registers custom Items (CoinItem) and Blocks (ShopBlock) that ship with **no texture file** — they will display Minecraft's default purple-black missing-texture checkerboard until the art team provides assets. All functionality works regardless of whether textures exist.

**Mod Loader:** Forge for Minecraft 1.20.1 (mappings: official Mojang mappings).

---

## 2. Rendering Toolkit

Everything visible on screen must be achievable with these primitives:

| Primitive | Minecraft Method / Class | Use Case |
|---|---|---|
| Solid Color Rect | `GuiGraphics.fill()` | Backgrounds, panels, buttons, dividers, progress bars |
| Gradient Rect | `GuiGraphics.fillGradient()` | Panel backgrounds, hover effects, header gradients |
| Bordered Rect | 4× `fill()` calls composited | Bordered panels, input fields, card outlines |
| Text Label | `GuiGraphics.drawString()` / `drawCenteredString()` | All text labels, prices, names, descriptions |
| Scaled Text | `PoseStack.scale()` + `drawString()` | Titles, headers, emphasized prices |
| Item Rendering | `GuiGraphics.renderItem()` / `renderItemDecorations()` | Item icons in shop listings, cart, inventory, barter slots |
| Tooltip | `GuiGraphics.renderTooltip()` or custom | Hover info for items, buttons |
| Scissor Clipping | `GuiGraphics.enableScissor()` / `disableScissor()` | Scrollable areas, overflow hiding |
| Vanilla Widgets | `Button`, `EditBox`, `AbstractSliderButton` | Text input, sliders, standard buttons (reskinned) |
| Vanilla Textures | `GuiGraphics.blit()` with MC atlas | Only for inventory slots, scrollbar knob (optional) |

> **RULE:** If it cannot be made with the above primitives, it does not go in the mod.

---

## 3. Color Palette & Typography

### 3.1 Color Palette

All colors are defined as constants in a `ShopColors` utility class. **Never hardcode hex values outside this class.**

| Token Name | Hex (ARGB) | Role | Usage |
|---|---|---|---|
| `BG_PRIMARY` | `0xCC1A1A2E` | Main backdrop | Full-screen overlay behind shop panels |
| `BG_PANEL` | `0xE616213E` | Panel fill | Main shop window, sidebar, modal backgrounds |
| `BG_CARD` | `0xFF1E293B` | Card/item tile | Individual item cards in the grid |
| `BG_CARD_HOVER` | `0xFF2D3A4F` | Hovered card | Card background on mouse hover |
| `BORDER_DEFAULT` | `0xFF334155` | Subtle border | Card edges, dividers, input field outlines |
| `BORDER_ACCENT` | `0xFFE94560` | Highlight border | Selected card, focused input, active tab |
| `TEXT_PRIMARY` | `0xFFFFFFFF` | Main text | Item names, headers, primary labels |
| `TEXT_SECONDARY` | `0xFF94A3B8` | Muted text | Descriptions, quantities, helper text |
| `TEXT_PRICE` | `0xFF4ADE80` | Currency | All price displays, balance amounts |
| `TEXT_BARTER` | `0xFF60A5FA` | Barter/trade | Barter cost display, trade indicators |
| `BTN_PRIMARY` | `0xFFE94560` | Main CTA | Buy Now, Checkout, Confirm buttons |
| `BTN_PRIMARY_HOVER` | `0xFFFF6B81` | CTA hover | Primary button hover state |
| `BTN_SECONDARY` | `0xFF334155` | Secondary action | Cancel, Back, Add to Cart buttons |
| `BTN_BARTER` | `0xFF7C3AED` | Barter action | Trade/Exchange buttons |
| `BTN_BARTER_HOVER` | `0xFF8B5CF6` | Barter hover | Trade button hover state |
| `ACCENT_GOLD` | `0xFFFBBF24` | Currency icon | Coin indicators, premium items, sale badges |
| `ACCENT_PROMO` | `0xFFEF4444` | Promo/discount | Sale banners, discount percentage text |
| `SUCCESS` | `0xFF22C55E` | Positive feedback | Purchase confirmed, item added, in-stock |
| `ERROR` | `0xFFEF4444` | Error state | Insufficient funds, out of stock, invalid |
| `PROMO_BANNER` | `0xFFDC2626` | Promo background | Sale banner strips on cards and detail view |
| `PROMO_TEXT` | `0xFFFFFFFF` | Promo text | Text on promo banners |
| `INVENTORY_HIGHLIGHT` | `0x4422C55E` | Inventory owned | Highlight color for items the player owns |
| `STORAGE_LINKED` | `0xFF3B82F6` | Storage link | Indicator for linked storage systems |

### 3.2 Typography Rules

Minecraft's default font renderer is the only text engine. All sizing via `PoseStack` scaling.

| Style | Scale | Shadow? | Color Token | Where Used |
|---|---|---|---|---|
| Screen Title | 1.5× | Yes | `TEXT_PRIMARY` | "Server Shop" header, "Barter Exchange" header |
| Section Header | 1.0× | Yes | `TEXT_PRIMARY` | Category names, panel titles, "Your Inventory" |
| Item Name | 1.0× | Yes | `TEXT_PRIMARY` | Item name on cards |
| Body / Description | 0.85× | No | `TEXT_SECONDARY` | Item lore, stock count, helper text |
| Price Label | 1.0× | Yes | `TEXT_PRICE` | Price on cards, cart totals |
| Barter Label | 1.0× | Yes | `TEXT_BARTER` | Barter costs on cards |
| Price Large | 1.25× | Yes | `TEXT_PRICE` | Cart total, balance display |
| Badge Text | 0.75× | No | `TEXT_PRIMARY` | "SALE", "NEW", stock tags, "BARTER" |
| Promo Banner | 0.85× | Yes | `PROMO_TEXT` | "30% OFF", "BUY 2 GET 1", etc. |
| Button Label | 1.0× | Yes | `TEXT_PRIMARY` | All button text |
| Inventory Count | 0.75× | No | `TEXT_SECONDARY` | "You have: 5" overlays |

---

## 4. Screen Architecture & Navigation

The mod has **eight distinct screens/overlays**:

| Screen | Purpose | Entry Point |
|---|---|---|
| `ShopMainScreen` | Browse items in grid, filter by category, search, view inventory counts | Keybind / `/shop` / right-click ShopBlock |
| `ItemDetailScreen` | View one item: description, buy/sell/barter controls, quantity picker, promo info | Click any item card |
| `CartScreen` | Review queued purchases, adjust quantities, view totals, checkout | Cart button in header |
| `TransactionHistoryScreen` | Scrollable log of past transactions with timestamps | History button in header |
| `BarterScreen` | Item-for-item exchange UI — place offered items, see required items, confirm trade | "Barter" tab in ShopMainScreen or barter button on item cards |
| `StorageLinkScreen` | Configure which storage (chest/barrel/modded) is linked to the shop block | Owner right-clicks ShopBlock with sneak |
| `ConfirmationModal` | Confirm purchase/sale/barter overlay (NOT a full screen) | Checkout/Buy Now/Trade confirm |
| `PromoEditorModal` | Shop owner sets discounts, promos, sale banners (overlay) | Owner-mode button in ShopMainScreen |

**Navigation Flow:**
```
ShopMainScreen ──→ ItemDetailScreen (click item)
       │──→ CartScreen (header button)
       │──→ TransactionHistoryScreen (header button)
       │──→ BarterScreen (barter tab)
       │──→ PromoEditorModal (owner-only button)
       
StorageLinkScreen ──→ Opened separately (sneak + right-click on ShopBlock)

Escape from sub-screen → ShopMainScreen
Escape from ShopMainScreen → Close GUI entirely
Escape from modal → Dismiss modal (keep underlying screen)
```

---

## 5. ShopMainScreen

### 5.1 Layout Structure

| Zone | Dimensions | Position | Contents |
|---|---|---|---|
| Container | 340 × 230 px | Centered on screen | Master container. Semi-transparent `BG_PRIMARY` fill |
| Header Bar | 340 × 22 px | Top of container | Title, Search, Mode Toggle (Buy/Barter), Cart, History, Close |
| Category Sidebar | 60 × 194 px | Left, below header | Vertical list of category tabs, scrollable |
| Item Grid | 254 × 170 px | Right of sidebar, below header | Scrollable grid of item cards. 4 columns, dynamic rows |
| Inventory Strip | 254 × 18 px | Below item grid | Shows player's currency balance (left), physical coins in inventory (center), page info (right) |
| Footer Bar | 340 × 12 px | Bottom of container | Sort button (left), mode indicator (center), owner-promo button if owner (right) |

> **DEV NOTE:** All pixel sizes are in Minecraft GUI-scaled pixels, not screen pixels. Design at GUI scale 2 baseline.

### 5.2 Header Bar — Component Breakdown

| Element | Type | Size | Behavior & Rendering |
|---|---|---|---|
| Title Label | Scaled Text | 1.5× scale | Text: "Server Shop" (or shop's configured `display_name`). `drawString()` inside `PoseStack.scale(1.5)`. Color: `TEXT_PRIMARY`. Shadow: on. Position: 6px from left edge, vertically centered. |
| Search Field | EditBox | 90 × 14 px | Vanilla `EditBox`, restyled: background `BG_CARD` + 1px `BORDER_DEFAULT`. On focus: border → `BORDER_ACCENT`. Placeholder: "Search items..." in `TEXT_SECONDARY` (0.85×). Filter grid in real-time (debounced 150ms). Centered horizontally. |
| Mode Toggle | Rect Button | 40 × 14 px | Two-state toggle: "Buy" / "Barter". Active state has `BORDER_ACCENT` bottom bar (2px). Inactive: `BTN_SECONDARY`. Switches grid between monetary items and barter-available items. |
| Cart Button | Rect Button | 30 × 14 px | Background: `BTN_SECONDARY`, hover: `BTN_PRIMARY`. Label: "Cart (N)" where N = item count. If non-empty: 5×5 filled circle badge in `ACCENT_GOLD`. Click: opens `CartScreen`. |
| History Button | Rect Button | 16 × 14 px | Label: "⧖" (hourglass) or "Log". Click: opens `TransactionHistoryScreen`. |
| Close Button | Rect Button | 14 × 14 px | Label: "×". Background: transparent. Hover: fill with `ERROR` at 50% alpha. Click: `this.onClose()`. |

### 5.3 Category Sidebar

| Property | Specification |
|---|---|
| Tab Size | 56 × 20 px each, 2px vertical gap between tabs |
| Tab Background | Inactive: `BG_CARD`. Hovered: `BG_CARD_HOVER`. Active/selected: left 2px border line with `BORDER_ACCENT` + `BG_CARD_HOVER` fill. |
| Tab Content | Left: `renderItem()` showing representative item (16×16). Right: Category name at 0.85× scale, `TEXT_PRIMARY`. Example: [Diamond Sword icon] "Weapons" |
| Scroll Behavior | If > 8 categories, enable mouse wheel scrolling. Render 2px-wide scroll indicator bar on right edge using `fill()`. |
| "All" Tab | First tab, always present. No item icon — just centered text. Shows all items. |
| "Barter" Tab | Special tab at bottom (if barter items exist). Icon: two arrows crossing. Shows only items accepting barter trades. Always visible regardless of mode toggle. |

### 5.4 Item Card (Grid Cell)

Implement as reusable `ItemCardWidget` class.

| Property | Specification |
|---|---|
| Dimensions | 58 × 56 px per card. Grid: 4 columns, 4px gap. |
| Card Background | **Default:** `BG_CARD` + 1px inset border `BORDER_DEFAULT`. **Hover:** `BG_CARD_HOVER` + `BORDER_ACCENT` + card shifts up 1px. **Disabled (out of stock):** `BG_CARD` at 60% alpha, no hover. **Barter-mode cards:** left 2px border strip in `BTN_BARTER` to indicate barter availability. |
| Item Icon Zone | Top 28px of card. Center 16×16 `renderItem()`. Stack count via `renderItemDecorations()`. |
| Item Name | Below icon: `drawString()` at 0.85× scale. `TEXT_PRIMARY`. Truncate with "..." if overflow (`Font.plainSubstrByWidth()`). Max 54px. |
| Price Row | Bottom 14px. **Money price:** [4×4 `ACCENT_GOLD` rect] + price text at 0.85× `TEXT_PRICE`. **Barter price:** [small `renderItem()` 8×8 of required item] + "×N" in `TEXT_BARTER`. If both money and barter available: show money price, with small ⇄ icon indicating barter option. |
| Inventory Count Overlay | If player has this item in inventory: render a small rounded rect at bottom-left of card showing "×N" in `TEXT_SECONDARY` at 0.7× on a `INVENTORY_HIGHLIGHT` background. |
| Sale Badge | If on sale: rectangle at top-right corner. Auto-width + 4px padding, 8px tall. Background: `PROMO_BANNER`. Text: "-30%" at 0.65×, `PROMO_TEXT`. |
| Promo Strip | If item has active promo: thin 3px strip at very bottom of card in `PROMO_BANNER`. No text — just a color indicator. Tooltip on hover shows promo details. |
| Click Actions | Left-click: open `ItemDetailScreen`. Right-click: quick-add 1× to cart (brief `SUCCESS` flash 300ms). Shift+click: quick-add max stack to cart. |

### 5.5 Inventory Strip

Displayed between the item grid and footer:

| Element | Specification |
|---|---|
| Balance Display | Left: [4×4 `ACCENT_GOLD` rect] + balance text in `TEXT_PRICE` 1.0× with shadow. Example: "■ 1,250.00" |
| Physical Coins | Center: "Coins in inventory: X" in `TEXT_SECONDARY` 0.85×. Shows count of `CoinItem` in player's inventory. Clicking this text opens a tooltip: "Use /withdraw <amount> to convert balance to coins, /deposit to convert coins to balance." |
| Page Info | Right: "Showing X-Y of Z" in `TEXT_SECONDARY` 0.85×. Only if items exceed one page. |

### 5.6 Item Grid — Scrolling

| Behavior | Details |
|---|---|
| Scroll Type | Mouse wheel, 1 row (60px) per notch |
| Scroll Indicator | Right edge: 3px wide track (`BORDER_DEFAULT` 30% alpha). Thumb: `TEXT_SECONDARY`, proportional height (min 10px). Draggable. |
| Scissor Clipping | `enableScissor()` around entire grid zone |
| Empty State | Center: "No items found" (`TEXT_SECONDARY`, 1.0×). Below: "Try a different search or category" (0.85×). |
| Loading Skeleton | 8 placeholder cards with alpha pulse (0.3–0.7 over 1s sine wave). |

---

## 6. ItemDetailScreen

### 6.1 Layout

| Zone | Size | Contents |
|---|---|---|
| Container | 270 × 200 px | Centered. `BG_PANEL` + 1px `BORDER_DEFAULT` border. |
| Back Button | 14 × 14 px | Top-left. Label: "←". Click: return to ShopMainScreen. |
| Item Preview | 80 × 100 px | Left third. 3.0× `renderItem()` centered. Below: item display name 1.0×, centered, `TEXT_PRIMARY`. Below name: "You own: X" in `TEXT_SECONDARY` 0.85× showing player's current inventory count. |
| Info Panel | 180 × 170 px | Right two-thirds. Description, stock, price breakdown, barter option, promo info, action area. |

### 6.2 Info Panel Contents (Top to Bottom)

| Element | Rendering |
|---|---|
| Item Name | `drawString()` 1.25× scale, `TEXT_PRIMARY` with shadow. Word-wrap 2 lines max. |
| Lore / Description | Below name, 4px gap. `TEXT_SECONDARY` 0.85×, no shadow. Max 3 lines, word-wrap via `Font.split()`. Truncate with "..." if exceeded. If no lore: "No description available." in lighter color. |
| Promo Banner (if active) | If the item has an active promo: full-width strip, 14px tall, `PROMO_BANNER` background. Text: promo description (e.g., "🔥 30% OFF — Ends Dec 31" or "Buy 2 Get 1 Free"). `PROMO_TEXT` at 0.85× centered. |
| Divider Line | 1px `fill()` spanning panel width, `BORDER_DEFAULT`. 6px vertical margin above/below. |
| Price Info Block | **Buy Price:** "Buy Price:" (`TEXT_SECONDARY`) + [4×4 gold rect] + value (`TEXT_PRICE`) — right-aligned. If sale active: show original price with strikethrough (draw thin line over it) + new price in `ACCENT_PROMO`. **Sell Price:** same layout. If buy-only or sell-only: show other as "—". **Stock:** "Stock: X remaining" or "Stock: Unlimited" in `TEXT_SECONDARY` 0.85×. |
| Barter Cost Block (if applicable) | Below prices, separated by 4px. Header: "Or trade for:" in `TEXT_BARTER` 1.0×. Then for each required item: `renderItem()` 16×16 + "Item Name ×N" in `TEXT_BARTER` 0.85×. Player's owned count shown inline: "(have: X)" in `TEXT_SECONDARY` — green if sufficient, red if insufficient. |
| Quantity Selector | `[-]` `[amount]` `[+]` `[Max]` horizontal strip. [-] button: 14×14, `BTN_SECONDARY`, label "-". [+] same, label "+". Amount: 30×14, `BG_CARD`, centered text, `TEXT_PRIMARY`. Click amount: converts to `EditBox` for direct input. [Max] button: 24×14, `BTN_SECONDARY`, sets qty = min(stock, 64). Hold-to-repeat: 500ms delay then 5/tick. |
| Total Display | "Total: " (`TEXT_SECONDARY`) + [4×4 gold rect] + calculated total (`TEXT_PRICE`, 1.25×, bold shadow). Updates real-time. If barter mode: shows required items × quantity instead. |
| Action Buttons | Three buttons side by side at bottom: **"Buy"** (60×16, `BTN_PRIMARY`): opens ConfirmationModal. **"Sell"** (60×16, `BTN_SECONDARY`): only enabled if player has item. **"Barter"** (60×16, `BTN_BARTER`): only visible if barter option exists, only enabled if player has required items. Disabled state: 40% alpha, no hover. |

---

## 7. CartScreen

Container: 280 × 200 px centered, `BG_PANEL`, 1px `BORDER_DEFAULT`.

| Element | Specification |
|---|---|
| Header | "Your Cart" at 1.25× scale. Back button "←" left. "Clear All" text-button right (`TEXT_SECONDARY`, hover: `ERROR`). |
| Cart Item Row | Each entry: 270 × 24 px. Left: `renderItem()` 16×16. Next: item name (`TEXT_PRIMARY`, 0.85×, truncate 80px). Center: `[-][qty][+]` mini-buttons (10×10 each). Right: line total [gold rect] + price `TEXT_PRICE`. Far right: "×" remove button (10×10, hover → `ERROR`). Alternating `BG_CARD` / `BG_PANEL` rows. 1px dividers. |
| Barter Cart Rows | If cart contains barter items: show with purple left-border strip (2px, `BTN_BARTER`). Instead of price, show required items as mini `renderItem()` icons (8×8 each) with counts. |
| Scrollable Area | Scissor-clipped to 270 × 140 px. Mouse wheel scroll. 3px scroll indicator. |
| Cart Summary | Fixed bottom 30px. `BORDER_ACCENT` top divider. "Items: N" (left). "Total: ■ XXXX" (`TEXT_PRICE`, 1.25×, right). "Balance After: ■ XXXX" (`TEXT_SECONDARY`). Negative balance: `ERROR` color + warning. Barter items: listed separately as "Barter trades: N" with purple text. |
| Checkout Button | Bottom-right: 80×18, `BTN_PRIMARY`. "Checkout (■ Total)". Disabled if empty or insufficient funds/items. Click: `ConfirmationModal`. |
| Empty State | "Your cart is empty" centered. "Browse the shop to add items" below. "Go to Shop" button. |

---

## 8. ConfirmationModal

This is an **overlay widget**, NOT a separate Screen. Rendered on top of current screen with dimming.

| Property | Specification |
|---|---|
| Dimming Layer | `fill()` entire screen with `0x88000000`. Clicking dim area = cancel. |
| Modal Panel | 200 × 140 px centered. `BG_PANEL`, 2px `BORDER_ACCENT` border. |
| Title | "Confirm Purchase" / "Confirm Sale" / "Confirm Trade" at 1.25×, `TEXT_PRIMARY`, centered. |
| Item Summary | **Single item:** centered 2.0× `renderItem()` + name + "Qty: X". **Cart checkout:** scrollable mini-list (4 visible rows). **Barter:** show "You Give:" section with items being traded away + "You Receive:" section. |
| Total Line | 1px divider, then money total or barter summary. |
| Buttons | "Cancel" (60×16, `BTN_SECONDARY`) — closes modal. "Confirm" (60×16, `BTN_PRIMARY`) — sends packet. On click: text → "Processing..." + disabled (prevent double-submit). |
| Result Feedback | **Success:** content fades to "✓ Purchase Complete!" / "✓ Trade Complete!" in `SUCCESS`, 1.25×. Auto-closes 1.5s. **Failure:** "✗ Transaction Failed" in `ERROR` + reason text. "OK" button to dismiss. |

---

## 9. TransactionHistoryScreen

Container: 260 × 200 px centered.

| Element | Specification |
|---|---|
| Header | "Transaction History" 1.25×. Back button left. Filter dropdown right: "All" / "Purchases" / "Sales" / "Barters". |
| Transaction Row | 250 × 18 px. Left: 8px type indicator bar (`SUCCESS` = buy, `ACCENT_GOLD` = sell, `BTN_BARTER` = barter). Icon: 12×12 `renderItem()`. Name + qty. Price or barter summary. Timestamp right. Alternating row colors. |
| Scrolling | Scissor + scroll indicator. Max 200 entries client-side. |
| Empty State | "No transactions yet" / "Make your first purchase!" |

---

## 10. BarterScreen *(NEW)*

Dedicated barter/trading screen for item-for-item exchanges without currency.

### 10.1 Layout

| Zone | Size | Contents |
|---|---|---|
| Container | 300 × 220 px | Centered. `BG_PANEL` + 2px `BTN_BARTER` border to distinguish from shop screen. |
| Header | 300 × 22 px | "Barter Exchange" at 1.5×. Back button. Mode indicator: "Item Trading" in `TEXT_BARTER`. |
| Available Trades Panel | 180 × 180 px | Left side. Scrollable list of available barter trades defined by the shop. |
| Trade Detail Panel | 110 × 180 px | Right side. Shows selected trade details, player's inventory status, confirm button. |

### 10.2 Available Trades List

Each trade entry is a row: 170 × 28 px.

| Element | Rendering |
|---|---|
| "You Give" | Left half: `renderItem()` icons (max 3 shown, "+N more" if exceeded) of required items. Each 12×12 with ×count. |
| Arrow Indicator | Center: "→" rendered in `TEXT_BARTER` 1.0× |
| "You Get" | Right half: `renderItem()` icons of reward items. Same layout. |
| Availability Indicator | Right edge: small circle — green (`SUCCESS`) if player has all required items, red (`ERROR`) if not. |
| Selection | Click: selects this trade, populates Trade Detail Panel. Selected row: `BORDER_ACCENT` outline. |

### 10.3 Trade Detail Panel

| Element | Rendering |
|---|---|
| "You Give:" header | `TEXT_BARTER` 1.0× with shadow |
| Required Items List | For each item: `renderItem()` 16×16 + "Item Name ×N" + "(have: X)" in green/red. |
| Divider | 1px line |
| "You Receive:" header | `TEXT_PRIMARY` 1.0× |
| Reward Items List | Same layout as required but in `SUCCESS` color. |
| Quantity Multiplier | If trade is repeatable: quantity selector `[-][×1][+]` to trade in multiples. |
| Confirm Trade Button | Full-width, 16px tall, `BTN_BARTER` fill. "Confirm Trade". Only enabled when player has all required items × multiplier. |

---

## 11. StorageLink GUI *(NEW)*

Opened when the shop owner sneak+right-clicks their placed ShopBlock.

### 11.1 Layout

| Zone | Size | Contents |
|---|---|---|
| Container | 220 × 160 px | Centered. `BG_PANEL` + 1px `STORAGE_LINKED` border. |
| Header | "Storage Configuration" at 1.25× |
| Linked Storage Display | 220 × 60 px | Shows the currently linked block (if any): block name, position (x,y,z), distance, status indicator (green = connected, red = broken/removed). If no link: "No storage linked" + instruction text. |
| Link Mode Button | "Link Storage" button (80×16, `BTN_PRIMARY`). Click: enters link mode — player's next right-click on a container (chest, barrel, shulker, modded storage) within 16 blocks links it. |
| Unlink Button | "Unlink" (60×16, `BTN_SECONDARY`). Removes current link. |
| Storage Preview | If linked: show the linked container's contents as a read-only item grid (scrollable). Items shown with counts. |
| Auto-Restock Toggle | Checkbox-style toggle: "Auto-pull from storage" — when shop stock runs low, automatically pull from linked storage. |
| Export Toggle | Checkbox-style toggle: "Auto-push to storage" — when players sell items to shop, push sold items into linked storage. |

---

## 12. Promo & Discount Display System *(NEW)*

### 12.1 Visual Components on Cards

Promos affect how items appear in the grid and detail views:

| Promo Type | Card Visual | Detail View Visual |
|---|---|---|
| Percentage Discount (e.g., 30% off) | Top-right badge: "-30%" on `PROMO_BANNER` bg. Original price shown with strikethrough, new price below. | Full-width promo strip with "🔥 30% OFF — Ends Dec 31". Price breakdown shows original crossed out + discounted. |
| Buy X Get Y Free | Small "B2G1" badge at top-right in `ACCENT_GOLD` bg. | Strip shows "Buy 2 Get 1 Free". Quantity selector auto-calculates free items. |
| Bundle Deal | Small "BUNDLE" badge. | Shows bundle contents and savings vs buying individually. |
| Limited Time | Pulsing 1px border animation cycling `PROMO_BANNER` → transparent over 2 seconds. | Countdown timer: "Ends in: 2d 14h 32m" rendered at 0.85× in `ACCENT_PROMO`. |
| Flash Sale | Entire card has very subtle `PROMO_BANNER` background tint at 10% alpha. Badge: "⚡ FLASH". | Large promo strip with countdown. |
| Barter Discount | Badge: "TRADE" in `BTN_BARTER` bg. | Shows reduced barter cost: "Was 5 diamonds → Now 3 diamonds" |

### 12.2 PromoEditorModal (Owner/Seller Only)

Overlay for shop owners to create/edit promotions:

| Element | Specification |
|---|---|
| Item Selector | Click on any item in the grid to select it for promo editing |
| Promo Type Dropdown | Dropdown menu: "None", "% Discount", "Buy X Get Y", "Flat Discount", "Flash Sale", "Barter Discount" |
| Value Input | EditBox for discount %. Or qty fields for Buy-X-Get-Y. |
| Duration | "Start" + "End" fields (auto-populated with current time + duration). Or "Permanent" checkbox. |
| Preview | Live preview of how the card will look with this promo |
| Apply Button | "Apply Promo" — sends promo config to server |

---

## 13. Reusable Widget Component Library

| Widget Class | Constructor Params | Behavior |
|---|---|---|
| `ShopButton` | x, y, w, h, label, color, hoverColor, onClick | Filled rect + centered text. Hover color swap. Disabled state. Optional icon slot. |
| `QuantitySelector` | x, y, min, max, initial, onChange | `[-]` `[display]` `[+]` `[Max]`. Hold-to-repeat. Click display → `EditBox`. |
| `ScrollPanel` | x, y, w, h, contentHeight | Scissor region, scroll offset, scroll indicator, mouse wheel. |
| `ItemCard` | x, y, shopItem, onClick, onRightClick | Full card rendering (Section 5.4). Encapsulates hover/badge/price/barter. |
| `SearchField` | x, y, w, placeholder, onTextChange | Styled `EditBox` wrapper with debounced callback. |
| `ModalOverlay` | w, h, title, onCancel | Dim layer + centered panel. Click-outside = cancel. |
| `ToastNotification` | message, type, duration | Slides from top-right. Color-coded left border. Auto-dismiss with alpha fade. |
| `BarterSlotWidget` | x, y, item, requiredCount, ownedCount | Renders item + count with green/red indicator. |
| `PromoStripWidget` | x, y, w, promoData | Renders the appropriate promo visual based on promo type. |
| `InventoryCountBadge` | x, y, count | Small count badge showing owned quantity. |
| `StorageStatusWidget` | x, y, linked, blockName, distance | Shows storage link status with icon. |
| `ToggleSwitch` | x, y, label, state, onToggle | Checkbox-style switch with label text. Two rect states. |

---

## 14. Animation & Transition Specifications

All animations use `partialTick` (`float pPartialTick`) and `Mth.lerp`. No external libraries.

| Animation | Duration | Implementation |
|---|---|---|
| Screen Open | 200ms | Container scales 0.95→1.0 + alpha 0→1. Ease-out. |
| Card Hover Lift | 100ms | y-offset 0→-1px. BG color lerp. Border color lerp. |
| Button Press | 50ms down, 100ms up | Scale 0.97→1.0. |
| Tab Switch | 150ms | Active tab accent border 0→2px. BG alpha lerp. |
| Toast Slide-In | 300ms in, 300ms out | x-position lerp from offscreen. Alpha fade on dismiss. |
| Quick-Add Flash | 300ms | Border flashes `SUCCESS` (alpha 0→1→0). |
| Loading Skeleton | 1000ms loop | Alpha pulse 0.3–0.7 via `0.5 + 0.2 * sin(tickCount * 0.1)`. |
| Modal Appear | 200ms | Dim 0→0x88. Panel scale 0.9→1.0 + alpha. |
| Scroll Momentum | Variable | Velocity decays 0.9×/tick until < 0.1px/tick. |
| Promo Pulse | 2000ms loop | Border alpha cycles for limited-time promos. |
| Barter Arrow Bounce | 800ms loop | Trade arrow "→" gently bounces right 1px via sine. |

---

## 15. Input Handling & Keyboard Shortcuts

| Input | Context | Action |
|---|---|---|
| Escape | Any sub-screen | Return to ShopMainScreen |
| Escape | ShopMainScreen | Close shop GUI |
| Escape | Modal open | Cancel/dismiss modal (NOT close underlying screen) |
| Tab | ShopMainScreen | Cycle focus: Search → Category Sidebar → Item Grid |
| / (slash) | ShopMainScreen | Focus search field |
| Enter | Search focused | Unfocus search, focus first grid item |
| Arrow Keys | Grid focused | Navigate between item cards (wrap at edges) |
| Enter / Space | Card focused | Open ItemDetailScreen |
| E (inventory key) | Any screen | Close shop GUI (vanilla behavior) |
| Mouse Wheel | Grid / Cart / History | Scroll within bounds |
| Shift + Click | Item card | Quick-add 1 stack to cart |
| B | ShopMainScreen | Toggle Buy ↔ Barter mode |
| Right Click | Item card | Quick-add 1× to cart |

---

# PART II — SERVER-SIDE ARCHITECTURE

---

## 16. Architecture Overview & Authority Model

The server is the **single source of truth** for all shop data, player balances, item inventories, barter trades, storage links, and transactions. The client is a display-only layer.

| Domain | Server | Client |
|---|---|---|
| Shop Catalog | Stores and serves items, prices, barter recipes, categories. Reloads from config. | Receives via `S2C_ShopData`. Displays only. |
| Player Balance | All additions/deductions server-side. Broadcasts after every transaction. | Displays received balance. Read-only. |
| Physical Currency (CoinItem) | Tracks `CoinItem` counts in inventory. Handles `/withdraw` and `/deposit`. **CoinItems have NBT tracking to prevent duplication.** | Shows coin count in inventory strip. |
| Barter Trades | Validates both sides of every trade. Checks player has required items. Atomic swap. | Sends `C2S_BarterRequest`. Shows result. |
| Storage Links | Stores link data (BlockPos, dimension). Validates container exists and is within range. | Displays storage contents read-only. |
| Promos/Discounts | Owner sets via command or PromoEditor. Server applies to prices. | Receives promo data in shop catalog packet. Displays badges/strips. |
| Permissions | Checks every action: open, buy, sell, barter, admin, owner. | Sends request. If denied, receives error toast. |

> **CRITICAL:** Every packet handler re-validates everything. Assume the client is hostile. Check: player online? Shop open? Item valid? Quantity > 0 and ≤ stock? Balance sufficient? Inventory space? Permission? Cooldown? CoinItem NBT valid? Storage link intact?

---

## 17. Economy System

### 17.1 Currency Model

| Property | Config Key | Default | Description |
|---|---|---|---|
| Currency Name | `economy.currency_name` | "Coins" | Display name. Used in all text. |
| Currency Symbol | `economy.currency_symbol` | "■" | Rendered as 4×4 gold rect on client. |
| Decimal Precision | `economy.currency_decimals` | 2 | Internal: long (minor units). 1250L = 12.50 displayed. |
| Starting Balance | `economy.starting_balance` | 1000.00 | Granted on first join. |
| Max Balance | `economy.max_balance` | 999,999,999.99 | Prevents overflow. |
| Negative Balance | `economy.allow_negative` | false | If false: reject transactions going below 0. |

> **IMPLEMENTATION:** Store all monetary values as `long` (64-bit) in minor units. With 2 decimal places, $12.50 = 1250L. This eliminates **all** floating-point rounding issues. Only convert to decimal for display.

### 17.2 BalanceManager API

| Method | Parameters | Behavior |
|---|---|---|
| `getBalance()` | UUID | Returns balance as long. Creates entry with starting_balance if new player. |
| `withdraw()` | UUID, long amount | Returns `TransactionResult`. Checks `allow_negative`. Atomic decrement. |
| `deposit()` | UUID, long amount | Returns `TransactionResult`. Checks `max_balance`. Atomic increment. |
| `setBalance()` | UUID, long amount | Admin-only. Direct set. Bypasses max. Logs. |
| `transfer()` | UUID from, UUID to, long amount | Atomic withdraw + deposit. If either fails, neither executes. |

### 17.3 External Economy Integration

```java
public interface EconomyProvider {
    long getBalance(UUID playerUUID);
    TransactionResult withdraw(UUID playerUUID, long amount);
    TransactionResult deposit(UUID playerUUID, long amount);
    String getCurrencyName();
    String getCurrencySymbol();
    int getDecimalPlaces();
}
```

Config `economy.provider`: `"internal"` (default), `"vault"`, `"custom"` (loads class from `economy.provider_class`).

---

## 18. Physical Currency Item (CoinItem) *(NEW)*

### 18.1 Item Registration

```java
// Register with DeferredRegister<Item>
public static final RegistryObject<Item> COIN_ITEM = ITEMS.register("coin",
    () -> new CoinItem(new Item.Properties().stacksTo(64)));
```

- **No texture file** is provided. The item will show the default purple-black missing-texture until art is created.
- The item name is set via `en_us.json` lang: `"item.shopmod.coin": "Coin"`
- The item tooltip shows the denomination value.

### 18.2 CoinItem NBT Structure (Anti-Dupe Critical)

Every CoinItem stack has mandatory NBT:

```
{
    "shopmod:coin_data": {
        "denomination": 100,           // Value in minor units (100 = 1.00)
        "mint_id": "a7f3b2c1-...",     // UUID generated at mint time
        "mint_timestamp": 1700000000,  // Unix epoch seconds when minted
        "mint_player": "uuid-...",     // Who withdrew the coins
        "mint_server": "server_id",    // Which server instance minted
        "checksum": "sha256hash..."    // SHA-256 of (mint_id + denomination + timestamp + player + server + secret_salt)
    }
}
```

### 18.3 Anti-Dupe Validation

**Every time a CoinItem is interacted with** (deposited, used in barter, placed in storage, picked up), the server validates:

1. **Checksum verification:** Recompute SHA-256 from the NBT fields + server's secret salt (stored in config, never sent to client). If checksum doesn't match → **destroy the item stack** and log the attempt.
2. **Mint ID uniqueness:** Maintain a `Set<UUID>` in memory (backed by DB table `coin_mints`) of all currently-valid mint IDs. If a mint_id appears more than once in any player's inventory or in the world → the duplicate is destroyed.
3. **Denomination bounds:** Reject any CoinItem where denomination ≤ 0 or denomination > max_balance.
4. **Timestamp sanity:** Reject items with mint_timestamp in the future or more than 1 year old (configurable `coin_max_age_days`).

### 18.4 /withdraw and /deposit Commands

**`/withdraw <amount>`:**
1. Validate player has ≥ amount in digital balance.
2. Determine optimal coin stacks (e.g., 1000 = 10 stacks of 100-denomination, or configurable denominations: 1, 10, 100, 1000).
3. Check player has inventory space.
4. Atomic: deduct balance → create CoinItem stacks with fresh mint NBT → give to player → log transaction as type `WITHDRAW`.

**`/deposit` (no args, deposits ALL coins in inventory):**
1. Scan player inventory for all valid CoinItem stacks.
2. Validate every stack's checksum and mint_id uniqueness.
3. Atomic: calculate total value → remove all valid CoinItems → add to digital balance → mark mint_ids as consumed in DB → log as `DEPOSIT`.
4. Invalid coins are destroyed silently and logged.

**`/deposit <amount>`:**
1. Same as above but only consumes coins up to the specified amount (starting from smallest denomination).

### 18.5 CoinItem Behavior Restrictions

| Interaction | Behavior |
|---|---|
| Dropping on ground | Allowed. Item entity has 5-minute despawn (same as vanilla). |
| Player death | Coins drop like normal items (obey keepInventory gamerule). |
| Crafting | Cannot be used in any crafting recipe. Register as `Item` not in any recipe tag. |
| Renaming (anvil) | Server rejects — anvil handler checks for shopmod:coin_data NBT and cancels rename. |
| Container placement | Allowed (chests, barrels, shulker boxes). Validated on extract. |
| Hopper extraction | **Blocked by default.** CoinItem overrides `canBeHopperedIn/Out` checks. Configurable. |
| Mod pipe interaction | Blocked via `IItemHandler.extractItem()` returning `ItemStack.EMPTY` for CoinItems in the shop block. See Section 22. |

---

## 19. Barter / Item-Exchange System *(NEW)*

### 19.1 Barter Trade Definition (in shop YAML)

Each item in the shop config can optionally have a `barter` block:

```yaml
- item_id: "minecraft:diamond_sword"
  buy_price: 5000.00
  sell_price: 2500.00
  barter:
    enabled: true
    recipes:
      - id: "diamond_trade"
        require:
          - item: "minecraft:diamond"
            count: 10
          - item: "minecraft:netherite_scrap"
            count: 2
        give_count: 1  # how many of the parent item are given
      - id: "emerald_trade"
        require:
          - item: "minecraft:emerald_block"
            count: 5
        give_count: 1
    permission: "shopmod.barter.diamond_sword"  # optional per-barter permission
```

### 19.2 Barter Trade Execution (Server)

Step-by-step for `C2S_BarterRequest`:

1. Validate player has active shop session.
2. Validate item_id and barter recipe_id exist in catalog.
3. Validate multiplier > 0 and ≤ max_quantity_per_item.
4. Check transaction cooldown.
5. Check player has **exact items** required × multiplier in inventory. Match by item ID + NBT tag comparison (using `ItemStack.matches()` for NBT items, or just registry ID for vanilla items). **Do not accept damaged items unless config allows.**
6. Check player has inventory space for reward items.
7. **LOCK** (per-player lock, same as buy flow).
8. **ATOMIC BLOCK:**
   - Remove required items from inventory (iterate slots, match, decrement).
   - If the shop has stock tracking for the reward item, decrement stock.
   - Give reward items to player.
   - If shop has stock for the consumed items and `barter_restocks_shop` is true, increment those stocks.
9. Log transaction with type `BARTER`. Record both sides of the trade.
10. Send `S2C_BarterResponse` with result.
11. Broadcast stock updates if changed.

### 19.3 Anti-Dupe for Barter

- **Item removal happens BEFORE item giving.** Never give first.
- After removal, re-count inventory to verify the items were actually removed (catches desyncs).
- Items are compared using `ItemStack.isSameItemSameTags()` — prevents trading renamed/enchanted fakes for real items.
- All barter operations run on the **main server thread** (`server.execute()`).

---

## 20. Shop Block & Block Entity *(NEW)*

### 20.1 Block Registration

```java
public static final RegistryObject<Block> SHOP_BLOCK = BLOCKS.register("shop_block",
    () -> new ShopBlock(BlockBehaviour.Properties.of()
        .strength(5.0f, 1200.0f)     // Hard to break, blast resistant
        .requiresCorrectToolForDrops()
        .noOcclusion()));             // Not a full cube (for future model)

public static final RegistryObject<Item> SHOP_BLOCK_ITEM = ITEMS.register("shop_block",
    () -> new BlockItem(SHOP_BLOCK.get(), new Item.Properties()));
```

- **No texture/model file** — shows missing texture until art is provided.
- Block entity type: `ShopBlockEntity`.

### 20.2 ShopBlockEntity Data

```java
public class ShopBlockEntity extends BlockEntity {
    private UUID ownerUUID;              // Player who placed it
    private String shopId;               // Which shop config it uses
    private BlockPos linkedStorage;      // Linked container position (nullable)
    private String linkedStorageDim;     // Dimension of linked storage
    private boolean autoRestock;         // Pull from linked storage
    private boolean autoExport;          // Push sold items to linked storage
    private List<PromoData> activePromos; // Owner-set promotions
    // ...saved/loaded via saveAdditional() / load()
}
```

### 20.3 Block Interactions

| Action | Result |
|---|---|
| Right-click (any player) | Opens ShopMainScreen if player has `shopmod.open` permission. Sends `C2S_OpenShop` with this block's `shopId`. |
| Sneak + Right-click (owner only) | Opens StorageLinkScreen. Non-owners get "You don't own this shop." toast. |
| Break (owner or admin) | Drops the block item. Persists NBT (shopId, ownerUUID). Linked storage is severed. |
| Break (non-owner, non-admin) | Blocked — "You can't break this shop." message. |
| Redstone signal | No effect (not redstone-powered). |
| Piston push | Blocked — `PushReaction.BLOCK`. Prevents piston-based exploits. |
| Explosion | Immune (blast resistance 1200, same as bedrock-level). |

### 20.4 Block Placement

- On place: set `ownerUUID` to placing player's UUID. Set `shopId` to `"default"` or a configurable default.
- Owner can configure shopId via `/shopblock setshop <shopId>` while looking at their block.

---

## 21. Storage Linking System *(NEW)*

### 21.1 Link Requirements

- Storage must be within **16 blocks** (configurable: `storage.max_link_distance`) of the ShopBlock.
- Storage must be in the **same dimension**.
- Storage must expose `IItemHandler` capability (Forge capability system). This covers: vanilla chests, barrels, shulker boxes, trapped chests, hoppers, dispensers, and any modded block that exposes `IItemHandler` (Mekanism bins, Create depots, AE2 interfaces, RS interfaces, etc.).
- One ShopBlock can link to **one** storage. One storage can be linked to by **multiple** ShopBlocks.

### 21.2 Link Validation (Every Operation)

Before any auto-restock or auto-export operation:

1. Check linked block still exists at stored `BlockPos`.
2. Check it's still in the same dimension.
3. Check it still has `IItemHandler` capability.
4. Check distance ≤ `max_link_distance` (blocks could be moved by pistons in theory — but ShopBlock is piston-immune, so only storage could move).
5. If any check fails → sever the link, notify owner via chat message or toast next time they open the shop.

### 21.3 Auto-Restock Logic

Runs on a timer (every 60 ticks / 3 seconds, configurable):

1. For each ShopBlock with `autoRestock = true` and valid storage link:
2. For each shop item with finite stock where `current_stock < restock_threshold` (default: 50% of max_stock):
3. Check linked storage for matching items (via `IItemHandler.getStackInSlot()` iteration).
4. Extract items from storage (using `IItemHandler.extractItem()` with **simulate first, then extract**).
5. Increment shop stock by extracted count.
6. Broadcast stock update to active viewers.

### 21.4 Auto-Export Logic (Sold Items)

When a player sells items to the shop:

1. After the sell transaction completes:
2. If the ShopBlock has `autoExport = true` and valid storage link:
3. Attempt to insert the sold items into linked storage (using `IItemHandler.insertItem()` with **simulate first, then insert**).
4. If storage is full: items remain in shop's virtual stock (not lost).

### 21.5 Storage Link Anti-Dupe

- **Never create items from nothing.** Auto-restock moves items from storage → shop stock counter. Auto-export moves items from "sold pool" → storage.
- **Simulate before execute.** Always call `extractItem(slot, amount, true)` or `insertItem(slot, stack, true)` to check feasibility before the real operation.
- **Single-threaded execution.** All storage link operations run on the main server thread within `ServerTickEvent`.
- **Link integrity check before every operation.** Stale BlockPos references are invalidated immediately.

---

## 22. Hopper, Pipe & Mod Compatibility *(NEW)*

### 22.1 ShopBlock IItemHandler Capability

The ShopBlock exposes `IItemHandler` capability to allow interaction with hoppers and mod pipes, but with **strict control**:

```java
@Override
public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
    if (cap == ForgeCapabilities.ITEM_HANDLER) {
        return shopItemHandler.cast();
    }
    return super.getCapability(cap, side);
}
```

The `ShopBlockItemHandler` is a **custom `IItemHandler` implementation**:

### 22.2 Item Handler Rules

| Method | Behavior |
|---|---|
| `getSlots()` | Returns number of item types the shop sells/buys. Each "slot" represents a shop listing. |
| `getStackInSlot(slot)` | Returns a **representative** `ItemStack` showing what the shop has in stock. Stack size = min(current_stock, 64). If unlimited stock: stack size = 64. |
| `insertItem(slot, stack, simulate)` | **Only accepts items the shop is configured to buy** (has a `sell_price`). If the item matches a shop listing: if simulate → return remainder. If real → executes a sell transaction at sell_price, depositing into the **shop owner's** balance (not any player's). Items go to linked storage or virtual stock. Returns remainder if couldn't accept all. **Rejects CoinItems** (returns the input unchanged). |
| `extractItem(slot, amount, simulate)` | **Blocked entirely by default.** Returns `ItemStack.EMPTY`. Shops don't dispense items via hoppers — purchases require a player in the GUI. Configurable: `shop.allow_hopper_extract: false`. |
| `isItemValid(slot, stack)` | Returns `true` only if the item matches a buyable listing and is not a CoinItem. |

### 22.3 Mod Compatibility Matrix

| Mod | Interaction | Behavior |
|---|---|---|
| **Vanilla Hopper** | Hopper above shop → inserts items into shop (sell) | Works via `IItemHandler.insertItem()`. Sells at configured price to shop owner's balance. |
| **Vanilla Hopper** | Hopper below shop → extracts items | Blocked by default (`extractItem()` → EMPTY). |
| **Mekanism Logistical Sorter/Pipes** | Insert into shop | Works — Mekanism uses `IItemHandler` capability. Same sell logic. |
| **Mekanism** | Extract from shop | Blocked — same as hopper. |
| **Create Mechanical Arm / Chutes / Funnels** | Insert into shop | Works — Create uses `IItemHandler`. Same sell logic. |
| **Create** | Extract from shop | Blocked. |
| **AE2 / RS** | Insert via Interface/Exporter | Works — uses `IItemHandler`. |
| **AE2 / RS** | Extract via Importer | Blocked. |

### 22.4 CoinItem Protection in All Handlers

In **every** `IItemHandler` implementation across the mod:

```java
@Override
public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
    // CRITICAL: Never accept CoinItems via automation
    if (stack.getItem() instanceof CoinItem) {
        return stack; // Reject entirely
    }
    // ... normal insert logic
}
```

This prevents duplication via: hopper chains cycling coins, pipe loops creating items, Mekanism digital miner exploits, etc.

---

## 23. Seller / Owner Promo System *(NEW)*

### 23.1 Promo Data Model

```java
public class PromoData {
    String promoId;              // Unique identifier
    String promoType;            // PERCENTAGE, BUY_X_GET_Y, FLAT_DISCOUNT, FLASH_SALE, BARTER_DISCOUNT
    double value;                // e.g., 30.0 for 30% off, or flat amount
    int buyQuantity;             // For B2G1: buy this many...
    int freeQuantity;            // ...get this many free
    long startTimestamp;         // Epoch millis
    long endTimestamp;           // Epoch millis (0 = permanent)
    String permission;           // Optional: only applies to players with this perm
    boolean active;              // Can be toggled off without deleting
}
```

### 23.2 Promo Application Order

When calculating the final price for a buy transaction:

1. Start with base `buy_price`.
2. Apply **sale discount** (from item config `sale.discount_percent`).
3. Apply **owner promo** (from ShopBlockEntity's `activePromos`).
4. Apply **dynamic pricing** adjustment (if enabled).
5. Apply **permission-based discount** (e.g., VIP players get 10% off everything).
6. **Clamp:** final price ≥ 1 (minimum 1 minor unit). Never free unless explicitly configured.
7. Discounts do **not stack multiplicatively** — they stack **additively** (30% + 10% = 40% total, not 37%).

### 23.3 Buy-X-Get-Y Logic

When a B2G1 promo is active:
- If player buys quantity 3: charge for 2, give 3.
- If player buys quantity 6: charge for 4, give 6.
- Formula: `chargedQty = qty - floor(qty / (buyQty + freeQty)) * freeQty`
- Cart and detail view show: "3 items, pay for 2" with savings displayed.

---

## 24. Shop Configuration System

### 24.1 File Structure

```
config/shopmod/
  shop-config.yml          ← Global settings (economy, permissions, behavior)
  secret.key               ← CoinItem checksum salt (auto-generated, never distribute)
  shops/
    default-shop.yml       ← Default shop definition
    vip-shop.yml           ← Example secondary shop
    seasonal-event.yml     ← Example time-limited shop
  lang/
    en_us.yml              ← All user-facing strings
  data/
    shopmod.db             ← SQLite database (if using SQLite)
```

### 24.2 Global Config (shop-config.yml) — Complete

| Key | Type | Default | Description |
|---|---|---|---|
| `economy.provider` | String | `"internal"` | `internal`, `vault`, `custom` |
| `economy.currency_name` | String | `"Coins"` | Display name |
| `economy.currency_symbol` | String | `"■"` | Symbol beside amounts |
| `economy.currency_decimals` | int | 2 | Decimal places |
| `economy.starting_balance` | double | 1000.00 | New player balance |
| `economy.max_balance` | double | 999999999.99 | Balance cap |
| `economy.allow_negative` | boolean | false | Allow negative balance |
| `economy.denominations` | List\<int\> | [1, 10, 100, 1000] | CoinItem denominations in minor units |
| `economy.coin_max_age_days` | int | 365 | Max age for valid CoinItems |
| `shop.open_command` | String | `"/shop"` | Command to open shop |
| `shop.open_keybind` | String | `"key.keyboard.b"` | Default keybind |
| `shop.max_distance` | double | 0 | Max blocks from shop block (0 = unlimited) |
| `shop.close_on_damage` | boolean | true | Close on damage |
| `shop.close_on_move` | double | 10.0 | Blocks moved before auto-close |
| `shop.transaction_cooldown_ms` | long | 500 | Min ms between transactions |
| `shop.max_cart_items` | int | 64 | Max unique items in cart |
| `shop.max_quantity_per_item` | int | 64 | Max qty per transaction |
| `shop.allow_hopper_extract` | boolean | false | Allow hopper to pull from shop |
| `shop.allow_hopper_insert` | boolean | true | Allow hopper to push into shop (sell) |
| `barter.enabled` | boolean | true | Master toggle for barter system |
| `barter.restocks_shop` | boolean | true | Bartered items restock shop inventory |
| `barter.allow_damaged_items` | boolean | false | Accept damaged items in barter |
| `storage.max_link_distance` | int | 16 | Max blocks between shop and storage |
| `storage.restock_interval_ticks` | int | 60 | Ticks between auto-restock checks |
| `storage.restock_threshold_pct` | double | 50.0 | Restock when stock below this % of max |
| `coins.hopper_interaction` | boolean | false | Allow coins in hopper chains |
| `database.type` | String | `"sqlite"` | `sqlite`, `mysql`, `postgresql` |
| `database.host` | String | `"localhost"` | DB host |
| `database.port` | int | 3306 | DB port |
| `database.name` | String | `"shopmod"` | Database name |
| `database.username` | String | `"root"` | DB user |
| `database.password` | String | `""` | DB password |
| `database.pool_size` | int | 10 | HikariCP pool |
| `logging.log_transactions` | boolean | true | Log to DB |
| `logging.log_to_console` | boolean | false | Print to console |
| `logging.log_admin_actions` | boolean | true | Log admin commands |
| `logging.log_coin_operations` | boolean | true | Log all withdraw/deposit/invalid coin events |

### 24.3 Shop Item Schema (in shop YAML)

| Key | Type | Required? | Description |
|---|---|---|---|
| `item_id` | String | Yes | Minecraft registry ID |
| `nbt` | String\|null | No | SNBT for custom items |
| `display_name` | String\|null | No | Override name (supports §) |
| `lore` | List\<String\> | No | Description lines |
| `buy_price` | double\|null | No* | Price to buy (*at least one of buy/sell/barter required) |
| `sell_price` | double\|null | No* | Price shop pays |
| `stock` | int | No | -1 = unlimited (default) |
| `max_stock` | int | No | Max for sell-restocking |
| `stock_refresh_seconds` | int | No | Auto-refresh interval |
| `permission` | String\|null | No | Per-item permission |
| `buy_limit` | Object\|null | No | `{amount, period: "daily"/"weekly"/"total"}` |
| `sell_limit` | Object\|null | No | Same structure |
| `sale` | Object\|null | No | `{discount_percent, from, to}` |
| `barter` | Object\|null | No | See Section 19.1 |
| `commands_on_buy` | List\<String\> | No | Server commands on purchase `{player}` placeholder |

---

## 25. Database Schema & Persistence

### 25.1 Table: `player_balances`

| Column | Type | Key | Description |
|---|---|---|---|
| `uuid` | VARCHAR(36) | PK | Player UUID |
| `username` | VARCHAR(16) | | Cached name, updated on login |
| `balance` | BIGINT | | Balance in minor units |
| `first_seen` | TIMESTAMP | | First login |
| `last_seen` | TIMESTAMP | | Updated on every login |

### 25.2 Table: `transactions`

| Column | Type | Key | Description |
|---|---|---|---|
| `id` | BIGINT AUTO_INC | PK | Transaction ID |
| `player_uuid` | VARCHAR(36) | FK | Player |
| `type` | ENUM | | BUY, SELL, BARTER, WITHDRAW, DEPOSIT, ADMIN_SET, ADMIN_GIVE, ADMIN_TAKE, PAY_SEND, PAY_RECEIVE |
| `shop_id` | VARCHAR(64) | | Which shop |
| `item_id` | VARCHAR(128) | | Item registry ID |
| `item_nbt` | TEXT | nullable | SNBT (for NBT items) |
| `quantity` | INT | | Count |
| `price_per_unit` | BIGINT | | Price at time of transaction |
| `total_price` | BIGINT | | Total cost/earnings |
| `balance_before` | BIGINT | | Pre-transaction balance |
| `balance_after` | BIGINT | | Post-transaction balance |
| `barter_items_given` | TEXT | nullable | JSON array of items traded away (barter only) |
| `promo_applied` | VARCHAR(64) | nullable | Promo ID if discount was applied |
| `discount_amount` | BIGINT | | Total discount in minor units |
| `timestamp` | TIMESTAMP | indexed | Server time |
| `server_id` | VARCHAR(64) | nullable | Multi-server ID |

Index: `CREATE INDEX idx_tx_player_time ON transactions (player_uuid, timestamp DESC);`

### 25.3 Table: `stock_levels`

| Column | Type | Key | Description |
|---|---|---|---|
| `shop_id` | VARCHAR(64) | PK (composite) | |
| `item_id` | VARCHAR(128) | PK (composite) | |
| `current_stock` | INT | | Current available |
| `last_refresh` | TIMESTAMP | | Last auto-refresh |

### 25.4 Table: `purchase_limits`

| Column | Type | Key | Description |
|---|---|---|---|
| `player_uuid` | VARCHAR(36) | PK (composite) | |
| `shop_id` | VARCHAR(64) | PK (composite) | |
| `item_id` | VARCHAR(128) | PK (composite) | |
| `type` | ENUM(BUY,SELL) | PK (composite) | |
| `count` | INT | | Current period count |
| `period_start` | TIMESTAMP | | Reset when period expires |

### 25.5 Table: `coin_mints`

| Column | Type | Key | Description |
|---|---|---|---|
| `mint_id` | VARCHAR(36) | PK | UUID of the minted coin stack |
| `player_uuid` | VARCHAR(36) | | Who withdrew |
| `denomination` | BIGINT | | Value in minor units |
| `count` | INT | | Number of coins in original stack |
| `minted_at` | TIMESTAMP | | When minted |
| `consumed_at` | TIMESTAMP | nullable | When deposited/destroyed (null = still valid) |
| `server_id` | VARCHAR(64) | | Which server minted |

### 25.6 Table: `storage_links`

| Column | Type | Key | Description |
|---|---|---|---|
| `shop_block_pos` | VARCHAR(64) | PK | "x,y,z" of ShopBlock |
| `shop_block_dim` | VARCHAR(128) | PK | Dimension key |
| `owner_uuid` | VARCHAR(36) | | Shop owner |
| `linked_pos` | VARCHAR(64) | nullable | "x,y,z" of linked container |
| `linked_dim` | VARCHAR(128) | nullable | Dimension of linked container |
| `auto_restock` | BOOLEAN | | |
| `auto_export` | BOOLEAN | | |

### 25.7 Table: `promos`

| Column | Type | Key | Description |
|---|---|---|---|
| `promo_id` | VARCHAR(64) | PK | |
| `shop_id` | VARCHAR(64) | | |
| `item_id` | VARCHAR(128) | nullable | null = applies to entire shop |
| `promo_type` | VARCHAR(32) | | PERCENTAGE, BUY_X_GET_Y, FLAT, FLASH, BARTER_DISCOUNT |
| `value` | DOUBLE | | Discount value |
| `buy_qty` | INT | | For B2G1 |
| `free_qty` | INT | | For B2G1 |
| `start_time` | TIMESTAMP | | |
| `end_time` | TIMESTAMP | nullable | null = permanent |
| `permission` | VARCHAR(128) | nullable | |
| `active` | BOOLEAN | | |
| `created_by` | VARCHAR(36) | | Owner/admin UUID |

### 25.8 Table: `admin_log`

| Column | Type | Key | Description |
|---|---|---|---|
| `id` | BIGINT AUTO_INC | PK | |
| `admin_uuid` | VARCHAR(36) | | Who |
| `action` | VARCHAR(64) | | SET_BALANCE, RELOAD, SET_PRICE, etc. |
| `target_uuid` | VARCHAR(36) | nullable | Affected player |
| `details` | TEXT | | JSON with before/after values |
| `timestamp` | TIMESTAMP | | |

---

## 26. Networking Protocol — Packet Specifications

Channel: `"shopmod:main"`. Protocol version: `"2"`. Use `FriendlyByteBuf` for serialization.

### 26.1 Full Packet List

| Packet | Direction | Purpose |
|---|---|---|
| `C2S_OpenShop` | Client → Server | Request to open shop |
| `S2C_ShopData` | Server → Client | Full catalog + balance + barter recipes + promos |
| `C2S_BuyRequest` | Client → Server | Purchase item |
| `S2C_BuyResponse` | Server → Client | Result + new balance + stock |
| `C2S_SellRequest` | Client → Server | Sell item |
| `S2C_SellResponse` | Server → Client | Result + new balance |
| `C2S_BarterRequest` | Client → Server | Execute barter trade |
| `S2C_BarterResponse` | Server → Client | Result + inventory update |
| `S2C_PriceUpdate` | Server → Client | Broadcast price/stock/promo changes |
| `C2S_FetchHistory` | Client → Server | Request transaction history page |
| `S2C_HistoryResponse` | Server → Client | Paginated history |
| `S2C_ForceClose` | Server → Client | Force close shop (damage/distance/admin) |
| `C2S_StorageLink` | Client → Server | Link/unlink storage |
| `S2C_StorageLinkStatus` | Server → Client | Storage link result |
| `C2S_StorageContents` | Client → Server | Request linked storage preview |
| `S2C_StorageContents` | Server → Client | Linked storage item list |
| `C2S_PromoUpdate` | Client → Server | Owner sets/edits promo |
| `S2C_PromoUpdate` | Server → Client | Broadcast promo changes |
| `S2C_InventorySync` | Server → Client | Push player's current inventory item counts (for owned-count badges) |

### 26.2 Error Codes (used in all response packets)

```
INSUFFICIENT_FUNDS, OUT_OF_STOCK, INVENTORY_FULL, LIMIT_REACHED,
NO_PERMISSION, INVALID_ITEM, COOLDOWN, SERVER_ERROR,
BARTER_MISSING_ITEMS, BARTER_INVALID_RECIPE, INVALID_COIN,
STORAGE_NOT_LINKED, STORAGE_FULL, STORAGE_BROKEN,
MAX_BALANCE_EXCEEDED, PROMO_EXPIRED, SHOP_CLOSED
```

---

## 27. Transaction Processing Engine

### 27.1 Buy Transaction Flow

| Step | Phase | Detail |
|---|---|---|
| 1 | VALIDATE | Permission, session, item exists, qty bounds, cooldown, balance ≥ total (after promo discount), stock available, buy limit, inventory space. |
| 2 | LOCK | Per-player `ReentrantLock`. Timeout 2s. Fail → COOLDOWN error. |
| 3 | RE-VALIDATE | Re-check balance + stock under lock. |
| 4 | WITHDRAW | `balanceManager.withdraw()`. Fail → release lock, error. |
| 5 | DECREMENT STOCK | Atomic DB update `WHERE stock >= qty`. Fail → rollback balance, release lock. |
| 6 | GIVE ITEMS | `player.getInventory().add()` on main thread. Fail → rollback balance + stock, release lock. |
| 7 | RECORD LIMIT | Update `purchase_limits`. |
| 8 | LOG | Insert `transactions` row. |
| 9 | COMMANDS | Execute `commands_on_buy` via `server.execute()`. |
| 10 | RESPOND | Send response packet. Release lock. |
| 11 | BROADCAST | Stock update to other viewers. |

### 27.2 Sell Transaction Flow

Same pattern but reversed: take items → deposit balance → increment stock → (auto-export to linked storage if configured).

### 27.3 Barter Transaction Flow

Same locking pattern. Remove required items → give reward items → update stocks → log. No balance changes.

### 27.4 Withdraw/Deposit Transaction Flow

**Withdraw:** validate balance → lock → deduct balance → mint CoinItems (fresh NBT with checksum) → give to player → insert mint record into `coin_mints` → log → respond → release lock.

**Deposit:** scan inventory for CoinItems → validate each stack's checksum → lock → calculate total → remove coins → add balance → mark mint_ids as consumed → log → release lock.

### 27.5 Thread Safety

| Concern | Solution |
|---|---|
| Concurrent buy, same player | Per-player `ReentrantLock` |
| Two players buy last stock | Atomic `UPDATE WHERE stock >= qty` |
| Main thread access | Inventory ops MUST run on main thread (`server.execute()`) |
| DB contention | HikariCP pool (10 connections). Monetary writes in DB transactions. |
| CoinItem race condition | Mint ID uniqueness check under lock. DB constraint `UNIQUE(mint_id)`. |

---

## 28. Session Management

### 28.1 ShopSessionManager

`ConcurrentHashMap<UUID, ShopSession>`. ShopSession record: `{playerUUID, shopId, blockPos, openTimestamp, cartDirty}`.

### 28.2 Lifecycle Events

| Event | Handler |
|---|---|
| Player Disconnect | `PlayerLoggedOutEvent` → close session |
| Player Death | `LivingDeathEvent` → close + force-close packet |
| Player Damage | `LivingHurtEvent` → close if `close_on_damage` |
| Player Movement | Tick event (every 20 ticks) → distance check |
| Server Stopping | Close all sessions, force-close packets |
| Config Reload | Re-send `S2C_ShopData` to active viewers. Force-close if shop removed. |
| ShopBlock Broken | Close all sessions using that block's shopId at that position. |

---

## 29. Commands & Permissions (Full List)

### 29.1 Player Commands

| Command | Permission | Description |
|---|---|---|
| `/shop` | `shopmod.open` | Open default shop GUI |
| `/shop <id>` | `shopmod.open` | Open specific shop |
| `/balance` or `/bal` | `shopmod.balance` | Show own balance |
| `/balance <player>` or `/bal <player>` | `shopmod.balance.others` | Show other's balance |
| `/baltop [page]` | `shopmod.baltop` | Show richest players (paginated top 10) |
| `/pay <player> <amount>` | `shopmod.pay` | Transfer funds |
| `/withdraw <amount>` | `shopmod.withdraw` | Convert balance → CoinItems |
| `/deposit` | `shopmod.deposit` | Deposit ALL CoinItems → balance |
| `/deposit <amount>` | `shopmod.deposit` | Deposit specific amount of coins |
| `/sellhand [qty]` | `shopmod.sell` | Quick-sell held item to default shop |
| `/sellall` | `shopmod.sell` | Sell all sellable items in inventory |
| `/worth` | `shopmod.worth` | Show buy/sell price of held item |
| `/shopblock setshop <shopId>` | `shopmod.owner` | Configure shop block you're looking at |

### 29.2 Admin Commands

| Command | Permission | Description |
|---|---|---|
| `/shopadmin setbalance <player> <amount>` | `shopmod.admin.balance` | Set balance |
| `/shopadmin give <player> <amount>` | `shopmod.admin.balance` | Add funds |
| `/shopadmin take <player> <amount>` | `shopmod.admin.balance` | Remove funds |
| `/shopadmin reload` | `shopmod.admin.reload` | Reload all YAML configs |
| `/shopadmin setprice <shop> <item> <buy> <sell>` | `shopmod.admin.price` | Live price change |
| `/shopadmin setstock <shop> <item> <amount>` | `shopmod.admin.stock` | Set stock (-1 = unlimited) |
| `/shopadmin history <player> [page]` | `shopmod.admin.history` | View any player's history |
| `/shopadmin closeall` | `shopmod.admin.manage` | Force-close all shops |
| `/shopadmin reset <player>` | `shopmod.admin.manage` | Reset purchase limits |
| `/shopadmin promo add <shop> <item> <type> <value> [duration]` | `shopmod.admin.promo` | Add promo |
| `/shopadmin promo remove <promoId>` | `shopmod.admin.promo` | Remove promo |
| `/shopadmin promo list [shop]` | `shopmod.admin.promo` | List active promos |
| `/shopadmin coinaudit <player>` | `shopmod.admin.coins` | List all valid coin mint_ids for a player |
| `/shopadmin wipeinvalidcoins` | `shopmod.admin.coins` | Destroy all invalid CoinItems server-wide |

### 29.3 Permission Nodes

| Node | Default | Grants |
|---|---|---|
| `shopmod.open` | ALL | Open shop GUI |
| `shopmod.buy` | ALL | Purchase items |
| `shopmod.sell` | ALL | Sell items |
| `shopmod.barter` | ALL | Use barter system |
| `shopmod.balance` | ALL | Check own balance |
| `shopmod.balance.others` | OP | Check others' balance |
| `shopmod.baltop` | ALL | View balance leaderboard |
| `shopmod.pay` | ALL | Transfer funds |
| `shopmod.withdraw` | ALL | Withdraw to CoinItems |
| `shopmod.deposit` | ALL | Deposit CoinItems |
| `shopmod.worth` | ALL | Check item value |
| `shopmod.owner` | ALL | Configure owned shop blocks |
| `shopmod.shop.<shopId>` | ALL | Per-shop access |
| `shopmod.category.<categoryId>` | ALL | Per-category access |
| `shopmod.item.<itemId>` | ALL | Per-item access |
| `shopmod.discount.<group>` | NONE | Permission-based discounts |
| `shopmod.admin.*` | OP | All admin (wildcard) |
| `shopmod.admin.balance` | OP | Balance management |
| `shopmod.admin.reload` | OP | Config reload |
| `shopmod.admin.price` | OP | Live price changes |
| `shopmod.admin.stock` | OP | Live stock changes |
| `shopmod.admin.history` | OP | View any history |
| `shopmod.admin.manage` | OP | Close/reset management |
| `shopmod.admin.promo` | OP | Promo management |
| `shopmod.admin.coins` | OP | Coin auditing |

---

## 30. Dynamic Pricing System

Optional — toggled per-shop or per-item.

| Config Key | Default | Description |
|---|---|---|
| `dynamic_pricing.enabled` | false | Master toggle |
| `dynamic_pricing.recalc_interval_sec` | 300 | Recalculation frequency |
| `dynamic_pricing.max_increase_pct` | 50.0 | Max increase from base |
| `dynamic_pricing.max_decrease_pct` | 30.0 | Max decrease from base |
| `dynamic_pricing.demand_weight` | 0.6 | Buy activity weight |
| `dynamic_pricing.supply_weight` | 0.4 | Sell activity weight |
| `dynamic_pricing.decay_rate` | 0.95 | Return-to-base multiplier |

**Formula:**
```
demandPressure = buysSinceLastCalc * demand_weight
supplyPressure = sellsSinceLastCalc * supply_weight
priceDelta = (demandPressure - supplyPressure) * basePrice * 0.01
newPrice = (currentPrice + priceDelta) * decay_rate
newPrice = clamp(newPrice, base * (1 - max_decrease/100), base * (1 + max_increase/100))
```

---

## 31. Stock Refresh Scheduler

Runs every 60 seconds (1200 ticks). For each item with `stock_refresh_seconds > 0`: if elapsed ≥ interval, reset `current_stock` to config value. Batch broadcast. Persists `last_refresh` in DB across restarts.

---

## 32. Localization

All strings in `lang/en_us.yml`. Server substitutes placeholders before sending. Key examples:

| Key | Default |
|---|---|
| `error.insufficient_funds` | "Not enough {currency}. Need {amount} more." |
| `error.out_of_stock` | "This item is out of stock." |
| `error.barter_missing` | "You don't have the required items for this trade." |
| `error.invalid_coin` | "Invalid coin detected and destroyed." |
| `error.too_far` | "You moved too far from the shop." |
| `success.purchase` | "Purchased {quantity}× {item} for {price} {currency}." |
| `success.barter` | "Traded successfully! Received {quantity}× {item}." |
| `success.withdraw` | "Withdrew {amount} {currency} as physical coins." |
| `success.deposit` | "Deposited {amount} {currency} from physical coins." |
| `baltop.header` | "=== Top Balances ===" |
| `baltop.entry` | "#{rank} {player}: {balance} {currency}" |

---

## 33. Event Hooks & Developer API

### Custom Forge Events

| Event | Cancellable? | Purpose |
|---|---|---|
| `ShopOpenEvent` | Yes | Before opening. Cancel to block. |
| `ShopTransactionEvent.Pre` | Yes | Before buy/sell/barter. Cancel to block. Modify price. |
| `ShopTransactionEvent.Post` | No | After transaction. For logging, achievements, webhooks. |
| `ShopCloseEvent` | No | Informational: player, shop, reason. |
| `BalanceChangeEvent.Pre/Post` | Pre: Yes | Balance mutations. |
| `CoinMintEvent` | No | When coins are created via /withdraw. |
| `CoinDepositEvent` | No | When coins are deposited. |
| `BarterTradeEvent.Pre/Post` | Pre: Yes | Barter-specific events. |
| `ShopReloadEvent` | No | After config reload. |

### API (ShopModAPI)

```java
public final class ShopModAPI {
    public static EconomyProvider getEconomy();
    public static ShopCatalog getShopCatalog(String shopId);
    public static TransactionResult executeBuy(UUID player, String shop, String item, int qty);
    public static TransactionResult executeSell(UUID player, String shop, String item, int qty);
    public static TransactionResult executeBarter(UUID player, String shop, String item, String recipeId, int mult);
    public static List<TransactionRecord> getHistory(UUID player, int limit);
    public static void openShopForPlayer(ServerPlayer player, String shopId);
    public static void forceCloseShop(UUID player, String reason);
    public static boolean validateCoinItem(ItemStack stack);
    public static long getPhysicalCoinValue(ServerPlayer player);
}
```

---

## 34. Anti-Dupe & Security Hardening *(CRITICAL)*

### 34.1 Comprehensive Threat Model

| Threat | Countermeasure |
|---|---|
| **Packet spoofing** | Derive player from `NetworkEvent.Context.getSender()`. Never trust client UUIDs. |
| **Invalid item IDs** | Lookup every itemId in active catalog. Reject unknown. |
| **Quantity overflow** | Clamp to [1, `max_quantity_per_item`]. Reject 0, negative, > max. Use `int`. |
| **Race conditions** | Per-player `ReentrantLock`. Atomic DB stock updates. Re-validate under lock. |
| **Rapid-fire packets** | Cooldown 500ms. Rate limit: max 10 packets/sec/player. Exceed → 30s ban from shop. |
| **Inventory desync dupe** | All inventory ops on main thread via `server.execute()`. Verify before AND after. |
| **CoinItem duplication** | SHA-256 checksum with server-secret salt. Mint ID uniqueness (DB + in-memory set). Destroy invalid coins. |
| **Hopper loop dupe** | CoinItems rejected by all `IItemHandler.insertItem()`. Shop `extractItem()` blocked by default. |
| **Pipe/automation dupe** | Same `IItemHandler` restrictions apply to Mekanism, Create, AE2, RS, any mod using capabilities. |
| **Piston exploit** | ShopBlock has `PushReaction.BLOCK`. Cannot be piston-moved. |
| **Container swap dupe** | Storage link validated every operation. Stale links severed. |
| **NBT manipulation** | CoinItem checksum covers all fields. Barter uses `ItemStack.isSameItemSameTags()`. |
| **Balance overflow** | Check `Long.MAX_VALUE - balance < amount` before every deposit. |
| **SQL injection** | All queries use prepared statements. Never concatenate user input. |
| **Config injection** | YAML safe-load mode. Validate all types and ranges. |
| **Anvil rename exploit** | Server-side anvil event handler rejects CoinItems. |
| **Creative mode exploit** | Check `player.isCreative()` — optionally allow or deny shop use in creative. Log separately. |
| **Barter item swap** | Items matched by `ItemStack.isSameItemSameTags()`. Remove before give. Re-count after removal. |
| **Session hijacking** | Session stores UUID from authenticated connection. Verify sender matches session on every request. |
| **Disconnect during transaction** | Transaction lock released in `finally` block. Incomplete DB transactions rolled back. |
| **Chunk unload losing storage** | Storage link checks include `level.isLoaded(linkedPos)`. If chunk unloaded, skip restock (don't break link). |

### 34.2 CoinItem Validation Flow

Called on: `/deposit`, barter with coins, container extract, pickup, inventory tick scan.

```
1. Is item instanceof CoinItem? If no → skip.
2. Does it have "shopmod:coin_data" CompoundTag? If no → DESTROY, log "missing_nbt".
3. Extract mint_id, denomination, timestamp, player, server, checksum.
4. Are all fields present and non-null? If no → DESTROY, log "incomplete_nbt".
5. Is denomination > 0 and ≤ max_balance? If no → DESTROY, log "invalid_denomination".
6. Is timestamp ≤ now and ≥ (now - coin_max_age_days)? If no → DESTROY, log "expired_coin".
7. Recompute SHA-256(mint_id + denomination + timestamp + player + server + SECRET_SALT).
   Does it match the stored checksum? If no → DESTROY, log "checksum_mismatch".
8. Is mint_id in the coin_mints table with consumed_at = NULL? If no → DESTROY, log "consumed_or_unknown_mint".
9. Count all CoinItems across ALL online players' inventories with this mint_id.
   Is count > original mint count? If yes → DESTROY all extras, log "duplicate_detected".
10. VALID — proceed with operation.
```

---

## 35. Performance Considerations

| Area | Guidance |
|---|---|
| Catalog caching | In-memory after config load. Never re-parse YAML per `/shop` open. |
| DB queries | HikariCP pool. Balance reads cached (per-player `ConcurrentHashMap`, invalidated on write). History paginated (max 50/page). |
| Packet size | Compress: omit null fields, short enum IDs. If > 500KB, chunk. |
| Tick handlers | Session distance: every 20 ticks. Use `distanceToSqr()` (no sqrt). |
| Stock refresh | Batch into one DB transaction per cycle. Single broadcast. |
| CoinItem scanning | Full inventory scan for dupes: only on `/deposit` and periodic audit (every 5 minutes), NOT every tick. |
| Storage link checks | Every 60 ticks per link. Short-circuit if chunk not loaded. |
| /baltop | Cache top-10 results for 60 seconds. Don't query DB on every call. |

---

## 36. Complete Class Structure

```
com.yourmod.shopmod/
  ShopMod.java                              ← @Mod entry, bus registration
  
  client/
    screen/
      ShopMainScreen.java
      ItemDetailScreen.java
      CartScreen.java
      TransactionHistoryScreen.java
      BarterScreen.java                     ← NEW
      StorageLinkScreen.java                ← NEW
    widget/
      ShopButton.java
      ItemCard.java
      QuantitySelector.java
      ScrollPanel.java
      SearchField.java
      ModalOverlay.java
      ToastNotification.java
      CategoryTab.java
      BarterSlotWidget.java                 ← NEW
      PromoStripWidget.java                 ← NEW
      InventoryCountBadge.java              ← NEW
      StorageStatusWidget.java              ← NEW
      ToggleSwitch.java                     ← NEW
    render/
      ShopColors.java
      ShopRenderHelper.java
      AnimationHelper.java
  
  server/
    config/
      ShopConfig.java
      ShopDefinitionLoader.java
      ConfigValidator.java
      LangManager.java
    economy/
      EconomyProvider.java
      InternalEconomyProvider.java
      VaultEconomyProvider.java
      BalanceManager.java
      TransactionResult.java
    transaction/
      TransactionProcessor.java
      TransactionRecord.java
      PurchaseLimitChecker.java
    session/
      ShopSessionManager.java
      ShopSession.java
      SessionEventHandler.java
    stock/
      StockManager.java
      StockRefreshScheduler.java
    pricing/
      DynamicPricingEngine.java
      PriceCalculator.java
      PromoEngine.java                      ← NEW
    barter/                                 ← NEW
      BarterManager.java
      BarterRecipe.java
      BarterValidator.java
    storage/                                ← NEW
      StorageLinkManager.java
      StorageRestockScheduler.java
      StorageExportHandler.java
      ShopBlockItemHandler.java
    coin/                                   ← NEW
      CoinItem.java
      CoinValidator.java
      CoinMintService.java
      CoinAnvilHandler.java
    block/                                  ← NEW
      ShopBlock.java
      ShopBlockEntity.java
  
  network/
    ShopPackets.java
    packets/
      C2SOpenShopPacket.java
      S2CShopDataPacket.java
      C2SBuyRequestPacket.java
      S2CBuyResponsePacket.java
      C2SSellRequestPacket.java
      S2CSellResponsePacket.java
      C2SBarterRequestPacket.java           ← NEW
      S2CBarterResponsePacket.java          ← NEW
      S2CPriceUpdatePacket.java
      C2SFetchHistoryPacket.java
      S2CHistoryResponsePacket.java
      S2CForceClosePacket.java
      C2SStorageLinkPacket.java             ← NEW
      S2CStorageLinkStatusPacket.java       ← NEW
      S2CStorageContentsPacket.java         ← NEW
      C2SPromoUpdatePacket.java             ← NEW
      S2CPromoUpdatePacket.java             ← NEW
      S2CInventorySyncPacket.java           ← NEW
    handlers/
      OpenShopHandler.java
      BuyRequestHandler.java
      SellRequestHandler.java
      BarterRequestHandler.java             ← NEW
      FetchHistoryHandler.java
      StorageLinkHandler.java               ← NEW
      PromoUpdateHandler.java               ← NEW
  
  command/
    ShopCommand.java
    BalanceCommand.java                     ← NEW (bal, baltop, pay)
    WithdrawCommand.java                    ← NEW
    DepositCommand.java                     ← NEW
    SellHandCommand.java                    ← NEW
    WorthCommand.java                       ← NEW
    ShopBlockCommand.java                   ← NEW
    ShopAdminCommand.java
  
  database/
    DatabaseManager.java
    PlayerBalanceDAO.java
    TransactionDAO.java
    StockDAO.java
    PurchaseLimitDAO.java
    CoinMintDAO.java                        ← NEW
    StorageLinkDAO.java                     ← NEW
    PromoDAO.java                           ← NEW
    AdminLogDAO.java
    SchemaManager.java
  
  data/
    ShopCatalog.java
    ShopCategory.java
    ShopItem.java
    BarterRecipeData.java                   ← NEW
    PromoData.java                          ← NEW
  
  event/
    ShopOpenEvent.java
    ShopTransactionEvent.java
    ShopCloseEvent.java
    BalanceChangeEvent.java
    CoinMintEvent.java                      ← NEW
    CoinDepositEvent.java                   ← NEW
    BarterTradeEvent.java                   ← NEW
    ShopReloadEvent.java
  
  api/
    ShopModAPI.java
  
  init/
    ModItems.java                           ← Item registration (CoinItem)
    ModBlocks.java                          ← Block registration (ShopBlock)
    ModBlockEntities.java                   ← BlockEntity type registration
```

---

## 37. Server Startup & Lifecycle

| Order | Phase | Actions |
|---|---|---|
| 1 | `FMLCommonSetupEvent` | Register packets, capabilities |
| 2 | `RegisterEvent` | Register items (CoinItem), blocks (ShopBlock), block entity types |
| 3 | `RegisterCommandsEvent` | Register all Brigadier commands |
| 4 | `ServerStartingEvent` | Load config, init DB (pool + migrations + tables), load shop YAMLs, init BalanceManager, generate `secret.key` if missing |
| 5 | `ServerStartedEvent` | Start StockRefreshScheduler, start DynamicPricingEngine, register tick events, log startup summary |
| 6 | RUNNING | Handle packets, transactions, restocking, price recalc, coin validation |
| 7 | `ServerStoppingEvent` | Force-close all sessions, stop schedulers, flush DB, close pool |

---

## 38. Testing Checklist

### Economy
- [ ] New player gets starting_balance on first join
- [ ] Buy → balance decreases exactly
- [ ] Sell → balance increases exactly
- [ ] Insufficient funds → rejected, balance unchanged
- [ ] Sell exceeding max_balance → rejected
- [ ] `/pay` transfers exact amount
- [ ] `/baltop` shows correct top 10, paginates
- [ ] Admin setbalance/give/take work + log to admin_log

### Physical Currency (CoinItems)
- [ ] `/withdraw 100` creates correct CoinItem stacks with valid NBT
- [ ] `/deposit` collects all valid coins, adds to balance, marks consumed
- [ ] Coins with modified NBT are destroyed on deposit
- [ ] Coins with duplicate mint_ids: only original kept, copies destroyed
- [ ] Coins work after server restart (DB persistence)
- [ ] Cannot rename coins in anvil
- [ ] Cannot use coins in crafting
- [ ] Hoppers cannot move CoinItems (if configured)
- [ ] Coins in shulker boxes remain valid after unboxing
- [ ] `/deposit <amount>` deposits only up to specified amount

### Barter
- [ ] Barter trade works with exact items
- [ ] Barter with insufficient items → rejected
- [ ] Barter with wrong NBT items → rejected
- [ ] Barter trade logged correctly (both sides)
- [ ] Multiple barter recipes per item work
- [ ] Barter multiplier (×2, ×3) takes/gives correct amounts
- [ ] Damaged items rejected in barter (if configured)

### Stock & Limits
- [ ] Buy last stock → stock = 0, shows out of stock
- [ ] Two simultaneous buys when stock = 1 → only one succeeds
- [ ] Stock refresh timer restores stock
- [ ] Daily buy limit blocks at count, resets next day
- [ ] Sell at max_stock → rejected or clamped

### Storage Linking
- [ ] Link to chest within 16 blocks works
- [ ] Link to chest > 16 blocks → rejected
- [ ] Breaking linked chest → link severed, owner notified
- [ ] Auto-restock pulls items from linked chest
- [ ] Auto-export pushes sold items to linked chest
- [ ] Linked chest full → export fails gracefully (no item loss)
- [ ] Link to Mekanism bin works (IItemHandler)
- [ ] Link to Create depot works
- [ ] Chunk unload doesn't break link (pauses restock)

### Hopper & Pipe Interaction
- [ ] Hopper above shop → items sell at configured price to owner
- [ ] Hopper below shop → nothing extracted (default config)
- [ ] Mekanism pipe insert → works same as hopper
- [ ] Create funnel insert → works same as hopper
- [ ] CoinItems always rejected by automation
- [ ] No items created from nothing via any pipe configuration

### Promos & Discounts
- [ ] % discount applies correctly to price
- [ ] Buy-2-Get-1 charges for 2 when buying 3
- [ ] Promo with end time auto-expires
- [ ] Promo badge displays on card
- [ ] Multiple promos stack additively, not multiplicatively
- [ ] Permission-based discounts apply to correct groups

### Session Management
- [ ] Disconnect → session cleaned up
- [ ] Damage → shop closes (if configured)
- [ ] Move 11 blocks → shop closes (if threshold = 10)
- [ ] Server stop → all sessions closed
- [ ] Config reload → active viewers get updated catalog
- [ ] ShopBlock broken → sessions using that block close

### Security / Anti-Dupe
- [ ] Buy request for item not in shop → rejected
- [ ] Quantity = 0 or -1 → rejected
- [ ] Buy without open session → rejected
- [ ] 100 packets in 1 second → rate-limited
- [ ] Sell item player doesn't have → rejected
- [ ] Sell item with modified NBT → rejected
- [ ] Creative mode interactions handled correctly
- [ ] Disconnect mid-transaction → no dupe, lock released
- [ ] No items created via hopper loops
- [ ] No coins duplicated via any method

### UI
- [ ] All screens render without external textures
- [ ] Inventory count badges show correct owned amounts
- [ ] Promo strips render correctly on cards
- [ ] Barter cards show required items with have/need counts
- [ ] Search filters correctly in real-time
- [ ] Category tabs filter correctly
- [ ] Scroll works in all scrollable areas
- [ ] Empty states display appropriately
- [ ] Toast notifications appear and auto-dismiss
- [ ] Animations play smoothly at 60fps

---

> **END OF SPECIFICATION**
> This document is the single source of truth for the Shop Mod. Both client and server implementations must conform to it. Any packet, data model, or behavior change requires updating this spec first.

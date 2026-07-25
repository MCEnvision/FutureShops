# FutureShops 3.1 trade offer configuration

FutureShops 3.1 compiles legacy Server Shop entries and schema version 2 entries into one immutable offer model. Schema version 2 is required for explicit free offers, alternative payments, compound payments, multiple item bundles, Sell to Shop input bundles, limits, schedules, permissions, and verified bundle comparisons.

Money values are integer minor units. With two currency decimals, `1000` means `10.00`.

## File location and reload safety

Server Shop catalogs live under `config/futureshops/shops/`. The administrator editor writes `admin.json`.

The loader validates the complete candidate catalog before it becomes live. Administrator writes use a temporary sibling file, a bounded backup, and an atomic replacement when the operating system supports it. A parse, validation, write, or reload failure preserves the last valid live catalog.

Do not edit a catalog while an in game administrator save is pending. Back up the complete `config/futureshops/shops/` directory with the world and escrow state.

## In game editor workflow

Open the Server Shop as an administrator and choose the offer editor for a listing. Player Shop owners open Advanced Offer from the listing inspector. Both entry points use the same persistent draft and validation model.

The quickest common workflow is:

1. Choose what the shop should do from the visitor perspective.
2. Pick the held item, an inventory item, or a registry item.
3. Choose Free, Money, Items, Money and Items, or alternative options.
4. Review the visitor preview and save.

Use the outline for outputs, acquire options, Sell to Shop options, stock, limits, schedule, permissions, bundle value, and advanced data. Adding several components to one option means every component is required. Adding several options creates alternatives.

Every administrator field and icon control has contextual help by hover or keyboard focus. Help mode keeps descriptions visible. Invalid controls receive an outline and short message. Section badges count unresolved issues, and selecting a validation summary entry returns focus to the affected control. Save actions remain disabled while settlement would be ambiguous or unsafe.

Apply waits for the matching server acknowledgement and keeps the editor open with the acknowledged snapshot as its new baseline. Save and Close returns only after that acknowledgement. Revert restores the acknowledged baseline. Duplicate creates a new stable listing identifier. Remove Option and Remove Listing use separate confirmations. A stale revision opens review choices and never overwrites the newer server listing silently.

Returning from category, held item, inventory, or registry pickers preserves raw field text, selected section, scroll position, focus target, and every other draft value.

## Minimal schema version 2 catalog

```json
{
  "schemaVersion": 2,
  "shopId": "default",
  "displayName": "Server Shop",
  "categories": [
    {
      "id": "all",
      "displayName": "All",
      "sortOrder": 0
    }
  ],
  "listings": [
    {
      "id": "free_apples",
      "displayName": "Free Apples",
      "description": "A limited welcome gift.",
      "categoryId": "all",
      "active": true,
      "outputs": [
        {
          "id": "apple",
          "itemId": "minecraft:apple",
          "count": 4
        }
      ],
      "acquireOptions": [
        {
          "id": "claim",
          "label": "Welcome Gift",
          "paymentType": "FREE",
          "limits": {
            "maximumPerRequest": 1,
            "lifetime": 1
          }
        }
      ],
      "stock": {
        "type": "LIMITED",
        "quantity": 100,
        "refreshSeconds": 0
      }
    }
  ]
}
```

Free is always explicit. A zero legacy buy price remains disabled and never becomes a free offer.

## Canonical schema reference

The parser accepts omitted optional objects and supplies the defaults shown below. The in game editor writes the canonical form, including explicit stock, limit, and schedule objects. `revision` is content derived by the server and is not a JSON field.

Root fields:

| Field | Required | Meaning |
| --- | --- | --- |
| `schemaVersion` | Yes | Must be `2` for normalized offers. |
| `shopId` | No | Stable shop identifier. Defaults to `default`. |
| `displayName` | No | Shop title. Defaults to `shopId`. |
| `categories` | No | Existing category records with `id`, `displayName`, and `sortOrder`. |
| `listings` | Yes | Zero to 512 normalized listing objects. |

Listing fields:

| Field | Required | Meaning and default |
| --- | --- | --- |
| `id` | Yes | Stable listing identifier. |
| `displayName` | No | Visitor label. Defaults to `id`. |
| `description` | No | Visitor description. Defaults to empty. |
| `categoryId` | No | Category identifier. Defaults to `all`. |
| `icon` | No | `itemId` and optional exact `nbt`. Defaults to the first output component. |
| `active` | No | Whether new requests are allowed. Defaults to `true`. |
| `expiresAtEpoch` | No | Listing expiry in Unix epoch seconds. Zero means no expiry. |
| `permission` | No | Listing permission node. Empty means no listing override. |
| `outputs` | Yes | Output components. It may be empty only when the listing has no acquire option. |
| `acquireOptions` | No | Alternative ways to receive the outputs. Defaults to empty. |
| `sellOptions` | No | Alternative Sell to Shop exchanges. Defaults to empty. |
| `stock` | No | Listing stock policy. Defaults to unlimited. |
| `limits` | No | Listing usage limits. Defaults to the limit defaults below. |
| `schedule` | No | Listing availability window. Defaults to always available. |
| `bundleComparisons` | No | Output component references used only for validated savings. |

Every listing must contain at least one acquire or Sell to Shop option. Acquire and sell option identifiers share one namespace inside the listing.

Acquire option fields:

| Field | Required | Meaning and default |
| --- | --- | --- |
| `id` | Yes | Stable option identifier. |
| `label` | No | Visitor label. Defaults to the payment type. |
| `paymentType` | Yes | `FREE`, `MONEY`, `ITEMS`, or `MONEY_AND_ITEMS`. |
| `moneyCost` | Conditional | Positive minor units for `MONEY` and `MONEY_AND_ITEMS`. Omitted otherwise. |
| `itemCosts` | Conditional | One or more cumulative components for `ITEMS` and `MONEY_AND_ITEMS`. |
| `outputMultiplier` | No | Repeats every listing output per requested option unit. Defaults to `1`. |
| `limits` | No | Option usage limits. Defaults to the limit defaults below. |
| `schedule` | No | Option availability window. Defaults to always available. |
| `permission` | No | Option permission override. Empty means no option override. |

Sell option fields:

| Field | Required | Meaning and default |
| --- | --- | --- |
| `id` | Yes | Stable option identifier. |
| `label` | No | Visitor label. Defaults to `Sell to Shop`. |
| `inputs` | Yes | One or more cumulative input components. |
| `moneyPayout` | Yes | Positive payout in minor units. |
| `capacity` | No | Maximum accepted option units. Zero means no separate capacity limit. |
| `limits` | No | Option usage limits. Defaults to the limit defaults below. |
| `schedule` | No | Option availability window. Defaults to always available. |
| `permission` | No | Option permission override. Empty means no option override. |

Component fields:

| Field | Required | Meaning |
| --- | --- | --- |
| `id` | Yes | Stable component identifier inside its component list. |
| `itemId` | Yes | Registry item identifier. |
| `count` | No | Positive units per option unit. Defaults to `1`. |
| `nbt` | No | Exact SNBT identity and stack template. Empty means item identity only. |

Policy objects:

```json
{
  "stock": {
    "type": "LIMITED",
    "quantity": 100,
    "refreshSeconds": 3600
  },
  "limits": {
    "maximumPerRequest": 16,
    "lifetime": 128,
    "periodQuantity": 32,
    "periodSeconds": 86400,
    "cooldownSeconds": 5
  },
  "schedule": {
    "startsAtEpoch": 1767225600,
    "endsAtEpoch": 1769904000
  },
  "bundleComparisons": [
    {
      "componentId": "pickaxe",
      "listingId": "iron_pickaxe",
      "optionId": "coins"
    }
  ]
}
```

Omitting `limits` uses `maximumPerRequest: 2304` with every other limit disabled. Identifiers are at most 160 characters. Display text is at most 512 characters. A listing has at most 32 acquire options, 32 sell options, and 36 components in each component list. Component counts, output multipliers, and `maximumPerRequest` are from 1 through 2304. Money values are capped at `9000000000000000` minor units, and every multiplied total must remain in range.

## Money, barter, alternatives, and compound payments

Each acquire option is one complete choice. Options are alternatives. Item components inside one option are cumulative.

```json
{
  "id": "iron_pickaxe",
  "displayName": "Iron Pickaxe",
  "categoryId": "tools",
  "outputs": [
    {
      "id": "pickaxe",
      "itemId": "minecraft:iron_pickaxe",
      "count": 1
    }
  ],
  "acquireOptions": [
    {
      "id": "coins",
      "label": "Pay Coins",
      "paymentType": "MONEY",
      "moneyCost": 400
    },
    {
      "id": "materials",
      "label": "Trade Materials",
      "paymentType": "ITEMS",
      "itemCosts": [
        {
          "id": "iron",
          "itemId": "minecraft:iron_ingot",
          "count": 4
        },
        {
          "id": "stick",
          "itemId": "minecraft:stick",
          "count": 1
        }
      ]
    },
    {
      "id": "mixed",
      "label": "Coins and Emeralds",
      "paymentType": "MONEY_AND_ITEMS",
      "moneyCost": 200,
      "itemCosts": [
        {
          "id": "emerald",
          "itemId": "minecraft:emerald",
          "count": 2
        }
      ]
    }
  ],
  "stock": {
    "type": "UNLIMITED"
  }
}
```

The visitor chooses one option. Choosing `mixed` requires both the money and every listed item component in one atomic transaction.

## Sell only and multiple Sell to Shop options

A listing with sell options and no acquire options is Sell only. It exposes no Get, Buy, or Cart action.

```json
{
  "id": "ore_exchange",
  "displayName": "Ore Exchange",
  "description": "Sell raw ore or a complete mining bundle.",
  "categoryId": "materials",
  "outputs": [
    {
      "id": "icon",
      "itemId": "minecraft:raw_iron",
      "count": 1
    }
  ],
  "sellOptions": [
    {
      "id": "raw_iron",
      "label": "Sell Raw Iron",
      "inputs": [
        {
          "id": "iron",
          "itemId": "minecraft:raw_iron",
          "count": 8
        }
      ],
      "moneyPayout": 300,
      "capacity": 1000
    },
    {
      "id": "mixed_ore",
      "label": "Sell Mining Bundle",
      "inputs": [
        {
          "id": "iron",
          "itemId": "minecraft:raw_iron",
          "count": 8
        },
        {
          "id": "gold",
          "itemId": "minecraft:raw_gold",
          "count": 4
        }
      ],
      "moneyPayout": 650,
      "capacity": 250
    }
  ],
  "stock": {
    "type": "UNLIMITED"
  }
}
```

Capacity zero means no separate capacity limit. A positive value limits accepted option units. Per player limits may also be placed in the listing or option `limits` object.

## Output bundle and verified savings

One requested quantity produces every output component. `outputMultiplier` repeats the complete output bundle for the selected option.

```json
{
  "id": "iron_tool_bundle",
  "displayName": "Iron Tool Bundle",
  "description": "Pickaxe, sword, and shovel.",
  "categoryId": "tools",
  "outputs": [
    {
      "id": "pickaxe",
      "itemId": "minecraft:iron_pickaxe",
      "count": 1
    },
    {
      "id": "sword",
      "itemId": "minecraft:iron_sword",
      "count": 1
    },
    {
      "id": "shovel",
      "itemId": "minecraft:iron_shovel",
      "count": 1
    }
  ],
  "acquireOptions": [
    {
      "id": "bundle_price",
      "label": "Bundle Price",
      "paymentType": "MONEY",
      "moneyCost": 1000
    }
  ],
  "bundleComparisons": [
    {
      "componentId": "pickaxe",
      "listingId": "iron_pickaxe",
      "optionId": "coins"
    },
    {
      "componentId": "sword",
      "listingId": "iron_sword",
      "optionId": "coins"
    },
    {
      "componentId": "shovel",
      "listingId": "iron_shovel",
      "optionId": "coins"
    }
  ],
  "stock": {
    "type": "LIMITED",
    "quantity": 40
  }
}
```

Savings appear only when every comparison resolves to an active, permitted, compatible standalone listing and money option. FutureShops calculates the individual total from current trusted prices and revisions. Missing, expired, incompatible, or cheaper comparisons omit the savings message.

## Exact NBT

Add an `nbt` string to an item component to require or deliver the exact saved stack template.

```json
{
  "id": "named_sword",
  "itemId": "minecraft:diamond_sword",
  "count": 1,
  "nbt": "{display:{Name:'{\"text\":\"Founders Blade\"}'}}"
}
```

Exact NBT affects identity. Damage, names, enchantments, and other saved data must match. Invalid or oversized NBT is rejected.

## Limits, schedules, and permissions

Listings and options may each define limits, a schedule, and a permission. Both levels must allow the request.

```json
{
  "permission": "futureshops.offer.vip",
  "limits": {
    "maximumPerRequest": 16,
    "lifetime": 128,
    "periodQuantity": 32,
    "periodSeconds": 86400,
    "cooldownSeconds": 5
  },
  "schedule": {
    "startsAtEpoch": 1767225600,
    "endsAtEpoch": 1769904000
  }
}
```

`periodQuantity` and `periodSeconds` must be configured together. Zero disables lifetime, period, or cooldown limits. Schedule values are Unix epoch seconds. Zero means no boundary.

## Stock policy

Supported Server Shop stock policies are:

* `UNLIMITED`, with no quantity or refresh values.
* `LIMITED`, also accepted as `LIMITED_INDEPENDENT`, with a nonnegative listing quantity and optional refresh interval.

`LINKED` is reserved by the schema but is rejected in 3.1 because atomic linked component reservation is not implemented.

## Transaction events

When `events.transaction_events` is enabled, normalized Server Shop and Player Shop trades preserve the public `ShopTransactionEvent` and `BarterTradeEvent` hooks.

* `Pre` is fired only while a new request is being prepared. Cancellation prevents preparation.
* A listener may change a positive money amount only to another positive amount. It cannot turn a paid option into a free option.
* An explicit free option remains exactly zero. An item only option has no money leg.
* Item and compound acquire options also fire the barter event with every required component multiplied by the requested quantity.
* `Post` is fired after the first durable commit. Replaying the same request does not fire it again.

The event API exposes the first output or input item for compatibility with existing integrations. Exact bundle components, selected option identity, and request identity remain available in durable transaction history and escrow evidence.

## Migration behavior

Legacy catalogs remain readable.

* A positive legacy buy price becomes one money acquire option.
* A positive legacy sell price becomes one Sell to Shop option.
* A legacy barter recipe becomes one item acquire option.
* Every ingredient in a legacy barter recipe remains required.
* A zero legacy price remains disabled.
* Legacy listing identifiers, categories, exact NBT, expiry, and stock keys remain stable.

Player Shop blocks use versioned NBT persistence. Existing money, barter, alternatives, compound trades, directions, and output bundles are migrated into normalized offers while preserving physical storage as the authoritative source and sink.

## Validation and troubleshooting

The catalog is rejected when it contains an unknown schema, unknown item, invalid exact NBT, duplicate identifier, empty required bundle, unnormalized duplicate component, unsupported linked stock, negative money, zero paid option, arithmetic overflow, invalid schedule, recursive bundle comparison, or a collection above its configured bound.

Read the first catalog validation error in the server log. Correct the rejected file and reload it. Do not delete escrow state, stock state, or claims to repair a catalog.

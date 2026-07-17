# Bazaar product definitions

FutureShops reads Bazaar products from `config/futureshops/bazaar/products/*.json`. The directory is loaded as one atomic catalog. If any file is malformed or unsafe, the complete reload is rejected and the last valid catalog remains active.

The editor schema is available at [schemas/futureshops-bazaar-product.schema.json](schemas/futureshops-bazaar-product.schema.json).

Product identity and trading rules are versioned. Increase `version` whenever the item identity, category, lot size, price tick, price limits, or quantity limit changes. Status changes between `active`, `halted`, and `retired` do not require a version increase. A version already recorded in escrow can never be redefined.

Each file may contain one product.

```json
{
  "schema": 1,
  "id": "iron",
  "version": 1,
  "item": "minecraft:iron_ingot",
  "category": "metals",
  "displayName": "Iron Ingot",
  "iconItem": "minecraft:iron_ingot",
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

A file may instead contain a collection.

```json
{
  "schema": 1,
  "products": [
    {
      "id": "iron",
      "version": 1,
      "item": "minecraft:iron_ingot"
    },
    {
      "id": "gold",
      "version": 1,
      "item": "minecraft:gold_ingot"
    }
  ]
}
```

## Identity policies

`commodity` is the safe default. It represents ordinary tagless and undamaged stacks. Named, enchanted, damaged, container backed, and capability backed variants are rejected unless their restriction is explicitly enabled.

`exact` requires `exactNbt`. The SNBT is parsed and canonicalized during reload, and only that exact variant can enter custody.

```json
{
  "id": "market_gem",
  "version": 1,
  "item": "minecraft:diamond",
  "identityPolicy": "exact",
  "exactNbt": "{display:{Name:'{\"text\":\"Market Gem\"}'}}",
  "category": "gems"
}
```

An empty `allowedDimensions` list permits all dimensions. A nonempty list is an allowlist of dimension identifiers such as `minecraft:overworld`.

## Defaults and bounds

The Bazaar TOML supplies default `lotSize`, `priceTickMinor`, and maximum order quantity when a JSON field is omitted. Minimum price defaults to the effective price tick. Maximum price defaults to the largest supported minor unit value.

The loader bounds file count, product count, individual file size, total directory size, strings, SNBT, and dimension lists. Symbolic link product files, duplicate JSON fields, duplicate product versions, unknown fields, unregistered item identifiers, conflicting active identities, invalid UTF8, fractional integer fields, and nonretired historical versions are rejected.

Removing a current product from the catalog retires it through the escrow journal. It does not erase historical orders, fills, custody, or claims. Replacing a product requires a higher version. Invalid or interrupted reconciliation can be retried safely because every lifecycle mutation has a deterministic request identity.

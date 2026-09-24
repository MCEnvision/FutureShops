# Dynamic pricing

FutureShops 2.5.0 connects server shop pricing to the existing supply and demand engine. This applies to admin catalogs opened through `/shop`, including detail purchases, cart checkout, and sales. Player owned shop blocks retain their owner configured prices. The selected economy provider handles the final amount but does not calculate catalog prices.

## Configuration and calculation

Enable `dynamic_pricing.enabled` in the server's loaded FutureShops common configuration. No additional item flag is required in `config/futureshops/shops/admin.json`. Prices are recalculated after `recalc_interval_sec * 20` server ticks, not after every transaction. Server lag can make that take longer than the configured number of real seconds.

Successful trades contribute their item quantities to counters for the exact shop and listing ID. Distinct listings for the same registry item have independent state. Browsing does not create activity. A failed transaction contributes nothing.

The existing calculation is retained:

```text
demand = bought quantity * demand_weight
supply = sold quantity * supply_weight
delta = (demand - supply) * reference base price * 0.01
next reference price = (current reference price + delta) * decay_rate
```

The result is clamped to the configured increase and decrease percentages around the reference base price and rounded to whole minor units. The reference is the configured buy price, or the sell price when buying is disabled. The existing engine retains the previous valid price if a calculation cannot produce a positive representable amount.

Sell prices use the same percentage adjustment relative to their configured base:

```text
adjusted sell price = base sell price * adjusted reference price / reference base price
```

The final sell price is rounded half up to whole minor units, with a minimum of one minor unit for a positive sell price. Overflow makes the sale unavailable instead of saturating the payout. Disabled buy or sell directions remain disabled. This preserves the configured buy and sell relationship, subject to rounding. Buy promotions apply after the dynamic adjustment; they do not discount sell payouts. Existing quantity promotion rules remain in effect.

For example, with base buy `100`, base sell `50`, demand weight `0.6`, supply weight `0.4`, decay `0.98`, maximum increase `200`, and maximum decrease `40`, an initial purchase of 64 items results in a recalculated buy price of `136` and a sell price of `68`. With two currency decimals these are displayed as `1.36` and `0.68`.

The legacy `decay_rate` implementation multiplies the entire calculated price. Despite the configuration comment describing a return to base, it does not decay only the difference from base. This patch deliberately preserves that formula. Use `decay_rate = 1.0` when testing demand and supply without idle downward drift. Idle tracked prices can otherwise move toward the configured floor.

## Synchronization and upgrades

The server supplies adjusted buy, sell, and promotional unit prices in catalog snapshots. Cart verification uses the same effective buy price as checkout. A recalculation that changes a price silently refreshes sessions viewing the affected shop and advances their snapshot revisions. An open confirmation retains the revision of the price it displayed. If that revision has expired, the server rejects the request before money or items move and sends a fresh catalog. Review the refreshed price and confirm again.

Install the same 2.5.0 build on both sides. The packet layout remains protocol 26. Existing pricing data in `world/data/futureshops_dynamic_pricing.dat` is preserved without a schema migration. Previously accumulated 2.4.1 activity or adjusted prices can therefore affect the first quote after upgrading. Back up the world and configuration before testing; do not delete pricing or economy data to work around this behavior. Disabling dynamic pricing restores configured prices without deleting saved state.

## Support evidence

1. Record both jar versions and hashes, Minecraft and NeoForge versions, provider, the relevant listing from `admin.json`, and the dynamic pricing configuration. Do not send checksum salts or unrelated private configuration.
2. Run `/futureshops debug on surface` or `/futureshops debug on all` immediately before a short reproduction. Captures expire after 60 seconds, so use a short recalculation interval in a disposable test server or time the capture around a recalculation.
3. Complete one buy or sell and wait for a recalculation. Find `surface=dynamic_pricing operation=recalculate` in the server log. Its detail includes `shop`, `listing`, `base_minor`, `previous_minor`, `current_minor`, `buys`, and `sells`.
4. Compare the refreshed screen price, transaction history amount, and actual balance difference. Capture client logs or a screenshot only when investigating presentation or synchronization.
5. Run `/futureshops debug off`. Send only the relevant sanitized log excerpt and listing.

See the [testing procedure](../test/dynamic-pricing-2.5.0.md), [economy debugging guide](../economy-debugging.md), and [maintainer documentation](../general/documentation.md).

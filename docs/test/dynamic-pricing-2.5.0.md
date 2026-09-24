# Dynamic pricing verification for 2.5.0

## Automated checks

Use Java 21, the checked in Gradle wrapper, Minecraft 1.21.1, NeoForge 21.1.248, and GeckoLib 4.8.4. The new tests use the internal economy, without optional economy mods. Do not run these fixtures against a production world or configuration.

```sh
./gradlew --no-daemon test build
./gradlew --no-daemon runGameTestServer --dry-run
./gradlew --no-daemon runGameTestServer -PverificationGameDirectory=run/pricing-test
```

Before launch, prepare that exact disposable runtime with `eula=true` and a `server.properties` bound to `127.0.0.1` with an unused private port. The configured GameTest task starts a dedicated `GameTestServer`, not a graphical client. Use a fresh runtime for independent runs. Stop owned processes afterward, preserve only the sanitized result summary and candidate artifact, then remove the disposable runtime and build output after their final consumer.

`DynamicPricingGameTests` covers actual buy and sell service calls, cart debit totals, delivered and removed items, adjusted client catalog values, the configured recalculation interval, activity counters, changed snapshot revisions, stale confirmation rejection, static and runtime promotions, cart verification, independent listing variants, sell only listings, and disabling dynamic pricing. Mock connections are configured through NeoForge's test support. Each test restores its configuration, closes its session, and removes its temporary catalog. Unit tests cover scaling, minor unit rounding, amount overflow, bounds, nonmutating queries, and persistence.

These checks prove server behavior and packet contents. They do not prove rendering, mouse input, or external provider interoperability.

## Required local acceptance before merge

Keep the pull request in draft until the same candidate is tested on a client and dedicated server. Use an isolated laptop client with audio disabled and an authorized private test endpoint. Do not change a personal instance or publish the server port.

1. Back up the test world and configuration. Install the identical 2.5.0 jar on client and server. Record both hashes and the loader versions.
2. Use a fresh test listing with base buy `100`, base sell `50`, unlimited stock, no promotion, and two currency decimals. Enable dynamic pricing with interval `20`, demand `0.6`, supply `0.4`, decay `0.98`, increase `200`, and decrease `40`. Seed enough test currency through the normal administrative command.
3. Enable `/futureshops debug on all`. Open `/shop`, buy 64 items, and keep the shop open through the next recalculation. For a fresh listing with no intervening trade or prior calculation, expect buy `1.36` and sell `0.68`. Correlate against the logged counters if timing differs.
4. Buy one more item and verify the balance debit matches the displayed adjusted price. Add an item to the cart and verify cart totals and checkout agree for listings without quantity promotions.
5. Sell a known quantity and verify the exact credit matches the displayed sell price. Wait for recalculation and verify the next sale uses the lower price.
6. Leave a confirmation dialog open across a recalculation. Confirm it and verify that a stale quote moves neither money nor items. Open a new confirmation and verify the refreshed quote works. Repeat for buy, sell, and cart.
7. Reconnect and reopen `/shop`. Verify current prices remain consistent. Restart the disposable server and verify stored pricing state survives. Check a supported external provider separately if that compatibility claim is required; this patch does not retest Pixelmon or hybrid backends.
8. Disable dynamic pricing through the normal configuration workflow and verify original buy and sell prices return. Stop debug capture, retain sanitized evidence, and stop and clean the owned test runtimes.

Record any failure with the exact candidate hash, listing, operation, initial balance, expected delta, actual delta, and relevant server/client logs. Do not merge on screenshots alone or on a passing build alone.

See [dynamic pricing behavior](../features/dynamic-pricing.md).

# phase 002 pixelmon refusal evidence

## Unit proof

`PixelmonEconomyProviderTest` runs against a test classpath fixture with the exact Pixelmon API names. It verifies exact integer balance conversion, funds prechecks, fractional balance rejection, missing account handling, unavailable implementation handling, exact version comparison, capability reporting, and mutation refusal.

For one withdrawal request, `withdraw`, `deposit`, `retry`, and `lookup` all return `CAPABILITY_MISSING`. The fixture records zero calls to `BankAccount.take` and `BankAccount.add`. The adapter never creates a receipt and never converts a failed query into zero.

The coordinator already rejects a mutation when a required capability is false before journal append and custody creation. The existing coordinator capability regression asserts an empty journal and zero provider mutation calls. Pixelmon uses the same descriptor with both mutation capabilities false.

## Standard runtime and packaging proof

The standard dedicated server smoke reached `FutureShops common setup complete` and `FutureShops server starting` before its bounded timeout in `/tmp/futureshops-phase002-server-20260903-v3.log`. The standard Xvfb client smoke reached `FutureShops common setup complete` before its bounded timeout in `/tmp/futureshops-phase002-client-20260903-v3.log`. Neither environment has Pixelmon installed. The real NeoForge GameTest server passed all eleven existing economy tests in `/tmp/futureshops-phase002-gametest-20260903-v2.log`.

The rebuilt `build/libs/futureshops-2.3.0.jar` passed `unzip -tq`. Its SHA 256 is `30b02e65ecd2dce47accdfa0bfbbb4e1464c43f25d7774eaab8b0bcecec2b6ba`. Its SHA 512 is `1ab37a9ed664ce57d547a699d9d9a16d50d833296e0a3d18725d29232034723546e4e9e878cbb23f967cef334b53287869734f843030df7abcd472c8b03d9896`. The archive contains only FutureShops adapter classes under `com/enviouse/futureshopsp/compat/pixelmon/`; it contains no `com/pixelmonmod`, Bukkit, Spigot, Vault, or test fixture classes. `jdeps` reports only Java, FutureShops, and NeoForge references for the adapter. No Pixelmon, Bukkit, Spigot, or bridge dependency is declared.

## Failure matrix

| Scenario | Result |
| --- | --- |
| Pixelmon absent | Adapter not registered, internal provider remains selectable |
| Pixelmon version not exactly `9.4.0` | Adapter not registered, selected identifier remains unavailable |
| Pixelmon implementation unavailable | `MISSING`, typed `NOT_READY` query result |
| Account missing or identity mismatch | typed `UNAVAILABLE`, no balance fallback |
| Fractional or overflowing balance | typed `UNAVAILABLE`, no lossy conversion |
| Debit without funds | typed `REJECTED`, `INSUFFICIENT_FUNDS` |
| Any mutation, receipt lookup, or retry | typed `REJECTED`, `CAPABILITY_MISSING` |
| Standard client or server without Pixelmon | no optional class linkage or embedded Pixelmon bytes |

## Remaining gates

The exact external artifact legal terms and full mutation recovery runtime remain unavailable under the current authorization. The product therefore makes no direct Pixelmon mutation claim. Phase 003 owns final artifact validation and the later issue 66 update.

# phase 002 pixelmon refusal evidence

## Unit proof

`PixelmonEconomyProviderTest` runs against a test classpath fixture with the exact Pixelmon API names. It verifies exact integer balance conversion, funds prechecks, fractional balance rejection, missing account handling, unavailable implementation handling, exact version comparison, capability reporting, and mutation refusal.

For one withdrawal request, `withdraw`, `deposit`, `retry`, and `lookup` all return `CAPABILITY_MISSING`. The fixture records zero calls to `BankAccount.take` and `BankAccount.add`. The adapter never creates a receipt and never converts a failed query into zero.

The coordinator already rejects a mutation when a required capability is false before journal append and custody creation. The existing coordinator capability regression asserts an empty journal and zero provider mutation calls. Pixelmon uses the same descriptor with both mutation capabilities false.

## Standard runtime and packaging proof

The standard dedicated server smoke reached `FutureShops common setup complete` and `FutureShops server starting` before its bounded timeout in `/tmp/futureshops-phase002-server-20260903-v3.log`. The standard Xvfb client smoke reached `FutureShops common setup complete` before its bounded timeout in `/tmp/futureshops-phase002-client-20260903-v3.log`. The real NeoForge GameTest server passed all twelve tests with Pixelmon selected in `/tmp/futureshops-pixelmon-gametest-20260903.log`. The same twelve tests passed with the internal provider selected while Pixelmon was present in `/tmp/futureshops-internal-gametest-with-pixelmon-20260903.log`.

The exact disposable profile `/tmp/futureshops-pixelmon-exact.xIDOL4` was assembled with the reviewed FutureShops, Pixelmon 9.4.0, GeckoLib 4.8.4, and NeoForge 21.1.248 bytes. Its bounded preacceptance launch discovered every expected mod and stopped at `eula=false` with exit zero in `/tmp/futureshops-pixelmon-exact-eula-false-20260903.log`. This proves exact mod discovery and classpath loading only. It does not claim Pixelmon economy behavior because the terms gate prevented full launch.

The exact hybrid profile `/tmp/futureshops-youer-pixelmon-248.zhVzs4` adds Youer `1.21.1-d4a204a0`, Vault 1.7.3, FinalEconomy 1.0.9, PixelmonEconomyBridge 1.1.6, and EverNifeCore 2.0.4.4. Its Java 21 bounded preacceptance launch reached NeoForge 21.1.248 and stopped at `eula=false` with exit zero in `/tmp/futureshops-youer-pixelmon-248-java21-eula-false-20260903.log`. This proves exact hybrid assembly and loader discovery only. It does not claim plugin loading or economy behavior because the terms gate prevented full launch.

### Authorized full startup rerun

The owner authorized `eula=true` for disposable validation. The exact hybrid profile was rerun on port `25566` with `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 timeout 120 /usr/lib/jvm/java-21-openjdk-amd64/bin/java -Xms2G -Xmx4G -jar youer.jar nogui`. The log reached `Done (27.779s)`, `FutureShops server starting.`, and bounded shutdown after loading Pixelmon `9.4.0`, Vault `1.7.3`, FinalEconomy `1.0.9`, EverNifeCore `2.0.4.4`, and PixelmonEconomyBridge `1.1.6`. The bridge logged Pixelmon integration and FinalEconomy discovery. The complete log is `/tmp/futureshops-youer-pixelmon-248.zhVzs4/logs/latest.log`.

The exact Pixelmon only profile was rerun on port `25567` with `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 timeout 120 bash ./run.sh nogui`. It reached `Done (5.574s)` and `FutureShops server starting.` with the exact FutureShops `2.3.0`, Pixelmon `9.4.0`, GeckoLib `4.8.4`, Minecraft `1.21.1`, and NeoForge `21.1.248` stack. The run log is `/tmp/futureshops-pixelmon-exact-eula-true-20260903.log`.

The reruns prove full startup and optional class loading under owner authorized terms. They do not invoke a real player account or a money mutation. The disposable probe below exercises one isolated account query and still makes no money mutation or recovery claim. The Pixelmon provider therefore remains query and precheck capable with deterministic mutation refusal.

### Disposable live provider probe

The exact Pixelmon only profile also loaded a disposable runtime probe. The probe queried UUID `00000000-0000-0000-0000-000000000001`, then submitted a one minor unit preflight and withdrawal through the public `EconomyTransactionCoordinator`. The probe jar SHA 256 is `02c7799a6907b57ad9c4b9ffbb57c04d8641fa9ab9dac316476a90c5f03763d1`.

The bounded command was `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 timeout 120 bash ./run.sh nogui` from `/tmp/futureshops-pixelmon-exact.xIDOL4`. The complete log is `/tmp/futureshops-pixelmon-exact-runtime-probe-preflight-20260903.log`. It reached `Done (0.922s)` and `FutureShops server starting.` and emitted:

```text
FUTURESHOPS_PROBE lifecycle=EconomyLifecycleSnapshot[providerId=pixelmon, lifecycle=READY, diagnostic=, acceptsQueries=true, acceptsMutations=true] resultStatus=CONFIRMED resultError=NONE resultDiagnostic= resultValue=Optional[BalanceSnapshot[playerId=00000000-0000-0000-0000-000000000001, balanceMinorUnits=0]] preflightStatus=UNAVAILABLE preflightError=CAPABILITY_MISSING preflightDiagnostic=provider lacks the capabilities required by this mutation mutationStatus=UNAVAILABLE mutationError=CAPABILITY_MISSING mutationDiagnostic=provider lacks the capabilities required by this mutation
```

The lifecycle snapshot reports server readiness and query availability. The coordinator capability gate remains authoritative for mutation eligibility. The query returned the disposable account at zero minor units, and the withdrawal stopped with typed `CAPABILITY_MISSING` before journal, custody, or Pixelmon `take` or `add` calls. This is live provider query and refusal evidence only. It is not a successful external debit or a recovery claim.

The rebuilt `build/libs/futureshops-2.3.0.jar` passed `unzip -tq`. Its SHA 256 is `a486cfe0bacb531e51afef5e0b13399ea40a05e21668982f39cade19361bf968`. Its SHA 512 is `5cb345c111bb6e831817a4ed213a618fb4cf2d7516ae10efae9909e1570adbfe5e92d85ae68d1dc8633384b0df3f65a621f71fb8e7fa43962f2746fa115038a7`. The archive contains only FutureShops adapter classes under `com/enviouse/futureshopsp/compat/pixelmon/`; it contains no `com/pixelmonmod`, Bukkit, Spigot, Vault, or test fixture classes. `jdeps` reports only Java, FutureShops, and NeoForge references for the adapter. No Pixelmon, Bukkit, Spigot, or bridge dependency is declared.

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

Bridge legal provenance, real player workflow coverage, and full mutation recovery runtime remain unavailable. The product therefore makes no direct Pixelmon mutation claim. Phase 003 owns final artifact validation and the later issue 66 update.

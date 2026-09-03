# phase 002 pixelmon refusal evidence

## Unit proof

`PixelmonEconomyProviderTest` runs against a test classpath fixture with the exact Pixelmon API names. It verifies exact integer balance conversion, funds prechecks, fractional balance rejection, missing account handling, unavailable implementation handling, exact version comparison, capability reporting, and mutation refusal.

For one withdrawal request, `withdraw`, `deposit`, `retry`, and `lookup` all return `CAPABILITY_MISSING`. The fixture records zero calls to `BankAccount.take` and `BankAccount.add`. The adapter never creates a receipt and never converts a failed query into zero.

The coordinator already rejects a mutation when a required capability is false before journal append and custody creation. The existing coordinator capability regression asserts an empty journal and zero provider mutation calls. Pixelmon uses the same descriptor with both mutation capabilities false.

## Standard runtime and packaging proof

The standard dedicated server smoke reached `FutureShops common setup complete` and `FutureShops server starting` before its bounded timeout in `/tmp/futureshops-standard-server-no-pixelmon-public-routes-20260903.log` with the rebuilt artifact and Pixelmon absent. The real NeoForge GameTest server passed all fourteen required tests with Pixelmon selected in `/tmp/futureshops-pixelmon-gametest-shop-buy-20260903.log`. `pixelmonServerShopSellRefusalBeforeItemRemoval` opened the loaded default shop, submitted a valid diamond sell request, and verified that the item count and custody store were unchanged after the capability refusal. `pixelmonPlayerShopBuyRefusalBeforeSaleEscrow` configured a valid money admin shop buy and verified that the item, sale escrow, and custody state were unchanged after refusal. The same fourteen tests ran with the internal provider selected while Pixelmon was present, with Pixelmon-only tests skipping their external branch, in `/tmp/futureshops-internal-gametest-public-routes-20260903-current.log`.

The clean NeoForge client smoke temporarily removed Pixelmon from `run/mods` and launched the same source and rebuilt dependency set with `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 xvfb-run -a timeout 120 bash ./gradlew runClient --no-daemon`. The client loaded FutureShops `2.3.0`, Minecraft `1.21.1`, and NeoForge `21.1.248`, reached `FutureShops common setup complete`, and started the sound engine without an error or exception. The complete log is `/tmp/futureshops-neoforge-client-no-pixelmon-20260903.log`. The exact Pixelmon jar was restored afterward with SHA 256 `9020393f98382ae8794ef2694e7bec1984c1a0eca735ea3eea06e0cb151c61f2`.

The incompatible runtime profile `/tmp/futureshops-pixelmon-incompatible.MapAQG` used the same FutureShops `2.3.0` jar with Pixelmon `9.3.1`, NeoForge `21.1.248`, Java 21, and `eula=true`. Its bounded command was `PATH=/usr/lib/jvm/java-21-openjdk-amd64/bin:$PATH JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 timeout 120 bash ./run.sh nogui`. The server reached `Done (5.698s)` and `FutureShops server starting.` while logging `Pixelmon economy adapter unavailable, pixelmon version is unsupported`. The complete log is `/tmp/futureshops-pixelmon-incompatible-9-3-1-20260903.log`. This proves that an incompatible Pixelmon version does not register the adapter while the rest of the server remains available.

The exact disposable profile `/tmp/futureshops-pixelmon-exact.xIDOL4` was assembled with the reviewed FutureShops, Pixelmon 9.4.0, GeckoLib 4.8.4, and NeoForge 21.1.248 bytes. Its bounded preacceptance launch discovered every expected mod and stopped at `eula=false` with exit zero in `/tmp/futureshops-pixelmon-exact-eula-false-20260903.log`. This proves exact mod discovery and classpath loading only. It does not claim Pixelmon economy behavior because the terms gate prevented full launch.

The exact hybrid profile `/tmp/futureshops-youer-pixelmon-248.zhVzs4` adds Youer `1.21.1-d4a204a0`, Vault 1.7.3, FinalEconomy 1.0.9, PixelmonEconomyBridge 1.1.6, and EverNifeCore 2.0.4.4. Its Java 21 bounded preacceptance launch reached NeoForge 21.1.248 and stopped at `eula=false` with exit zero in `/tmp/futureshops-youer-pixelmon-248-java21-eula-false-20260903.log`. This proves exact hybrid assembly and loader discovery only. It does not claim plugin loading or economy behavior because the terms gate prevented full launch.

### Authorized full startup rerun

The owner authorized `eula=true` for disposable validation. The exact hybrid profile was rerun on port `25566` with `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 timeout 120 /usr/lib/jvm/java-21-openjdk-amd64/bin/java -Xms2G -Xmx4G -jar youer.jar nogui`. The log reached `Done (27.779s)`, `FutureShops server starting.`, and bounded shutdown after loading Pixelmon `9.4.0`, Vault `1.7.3`, FinalEconomy `1.0.9`, EverNifeCore `2.0.4.4`, and PixelmonEconomyBridge `1.1.6`. The bridge logged Pixelmon integration and FinalEconomy discovery. The complete log is `/tmp/futureshops-youer-pixelmon-248.zhVzs4/logs/latest.log`.

The exact Pixelmon only profile was rerun with the corrected Java 21 disposable probe and current rebuilt jar using `PATH=/usr/lib/jvm/java-21-openjdk-amd64/bin:$PATH JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 timeout 50 /usr/lib/jvm/java-21-openjdk-amd64/bin/java @user_jvm_args.txt @libraries/net/neoforged/neoforge/21.1.248/unix_args.txt nogui`. It reached `Done (1.015s)` and `FutureShops server starting.` with the exact FutureShops `2.3.0`, Pixelmon `9.4.0`, GeckoLib `4.8.4`, Minecraft `1.21.1`, and NeoForge `21.1.248` stack. The run log is `/tmp/futureshops-pixelmon-packaged-java21-current-20260903.log`.

The reruns prove full startup and optional class loading under owner authorized terms. The packaged startup runs do not invoke a production player account or a money mutation. The disposable probe below exercises one isolated account query, and the GameTest below exercises disposable server player routes. The Pixelmon provider therefore remains query and precheck capable with deterministic mutation refusal.

### Disposable live provider probe

The exact Pixelmon only profile also loaded a disposable runtime probe compiled for Java 21. The probe queried UUID `00000000-0000-0000-0000-000000000001`, then submitted a one minor unit preflight and withdrawal through the public `EconomyTransactionCoordinator`. The probe jar SHA 256 is `1756394e915f718e7b41d962fec62925408fca38955ddb749692698803dab476`.

The bounded command was the direct Java 21 NeoForge launch from `/tmp/futureshops-pixelmon-exact.xIDOL4`. The complete log is `/tmp/futureshops-pixelmon-packaged-java21-current-20260903.log`. It reached `Done (1.015s)` and `FutureShops server starting.` and emitted:

```text
FUTURESHOPS_PROBE lifecycle=EconomyLifecycleSnapshot[providerId=pixelmon, lifecycle=READY, diagnostic=, acceptsQueries=true, acceptsMutations=true] resultStatus=CONFIRMED resultError=NONE resultDiagnostic= resultValue=Optional[BalanceSnapshot[playerId=00000000-0000-0000-0000-000000000001, balanceMinorUnits=0]] preflightStatus=UNAVAILABLE preflightError=CAPABILITY_MISSING preflightDiagnostic=provider lacks the capabilities required by this mutation mutationStatus=UNAVAILABLE mutationError=CAPABILITY_MISSING mutationDiagnostic=provider lacks the capabilities required by this mutation
```

The lifecycle snapshot reports server readiness and query availability. The coordinator capability gate remains authoritative for mutation eligibility. The query returned the disposable account at zero minor units, and the withdrawal stopped with typed `CAPABILITY_MISSING` before journal, custody, or Pixelmon `take` or `add` calls. This is live provider query and refusal evidence only. It is not a successful external debit or a recovery claim.

The rebuilt GameTest also created live server player fixtures and routed withdrawal and deposit through `BalanceManager` plus transfer through `ShopModAPI`. Each public route refused the Pixelmon mutation, and the tests confirmed that no custody record was created. The valid server shop sell and player shop buy cases additionally confirm that inventory and sale escrow stay unchanged. `ExternalEconomyMutationSurfaceSourceTest` additionally checks the server shop buy and sell services, player shop service, money item, deposit and withdrawal commands, pay command, admin command, and public API delegation. Every monetary entry point is bound to `BalanceManager` or the coordinator capability gate, and direct coordinator mutation methods retain their internal preflight. This covers the public player entry points without calling the unsafe Pixelmon boolean mutation methods.

The exact hybrid profile was then loaded with the same Java 21 disposable probe and current rebuilt FutureShops jar. The bounded command was `PATH=/usr/lib/jvm/java-21-openjdk-amd64/bin:$PATH JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 timeout 50 /usr/lib/jvm/java-21-openjdk-amd64/bin/java -Xms2G -Xmx4G -jar youer.jar nogui` from `/tmp/futureshops-youer-pixelmon-248.zhVzs4`. The complete log is `/tmp/futureshops-hybrid-packaged-java21-current-20260903.log`. It reached `Done (24.619s)`, loaded Vault `1.7.3`, FinalEconomy `1.0.9`, EverNifeCore `2.0.4.4`, and PixelmonEconomyBridge `1.1.6`, and emitted the same confirmed query followed by typed `CAPABILITY_MISSING` preflight and withdrawal refusal. This proves that the loaded Vault and bridge stack does not bypass the Pixelmon mutation capability gate. The probe made no external mutation.

### Unclean restart evidence

The exact Pixelmon only profile was started with the Java 21 probe, waited until `FutureShops server starting.`, and then terminated with `kill -9`. The termination returned exit `137` and produced `/tmp/futureshops-pixelmon-unclean-kill-20260903-c.log` without a shutdown marker. A subsequent Java 21 restart reached `Done (1.032s)` and `FutureShops server starting.` in `/tmp/futureshops-pixelmon-recovery-restart-20260903-c.log`. The probe again confirmed the live Pixelmon query and refused both preflight and withdrawal with `CAPABILITY_MISSING`. No mutation had been admitted before termination, so this evidence proves restart to ready and deterministic refusal, not external money recovery.

The rebuilt `build/libs/futureshops-2.3.0.jar` from the current phase branch passed `unzip -tq`. Its SHA 256 is `af55ca241f7774aba61181d29370245e1faa5750d0fe347c2f78bbee110b69c7`. Its SHA 512 is `2c00e7deabfa3e4216ac6084b34ff35004415e972b83387d377f40631e1968bc8d6a828170cfb3034780397951fb69e9634bc55c594064ed7d44d8f01e07c0eb`. The archive contains FutureShops adapter and GameTest classes, but no `com/pixelmonmod`, Bukkit, Spigot, Vault, bridge, or external test fixture classes. `jdeps` reports only Java, FutureShops, and NeoForge references for the adapter. No Pixelmon, Bukkit, Spigot, or bridge dependency is declared.

The current artifact source revision is `bf32b0a` on `envy/phase-002-pixelmon-vault`.

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

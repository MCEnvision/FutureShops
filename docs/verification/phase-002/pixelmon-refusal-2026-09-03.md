# phase 002 pixelmon refusal evidence

## Unit proof

`PixelmonEconomyProviderTest` runs against a test classpath fixture with the exact Pixelmon API names. It verifies exact integer balance conversion, funds prechecks, fractional balance rejection, missing account handling, unavailable implementation handling, exact version comparison, capability reporting, and mutation refusal.

For one withdrawal request, `withdraw`, `deposit`, `retry`, and `lookup` all return `CAPABILITY_MISSING`. The fixture records zero calls to `BankAccount.take` and `BankAccount.add`. The adapter never creates a receipt and never converts a failed query into zero.

The coordinator already rejects a mutation when a required capability is false before journal append and custody creation. The existing coordinator capability regression asserts an empty journal and zero provider mutation calls. Pixelmon uses the same descriptor with both mutation capabilities false.

## Standard runtime and packaging proof

The standard dedicated server smoke reached `FutureShops common setup complete`, `FutureShops server starting`, and a clean stop in `/tmp/futureshops-standard-server-packaged-clean-6f7274-v2.log` with the rebuilt artifact, Pixelmon absent, and a clean standard world. Its SHA 256 is `de7c7a49c997a69050c8510e30bb96dad390d40458a4c595e81b1d4def4bcf80`. The real NeoForge GameTest server passed all sixteen required tests with Pixelmon selected in `/tmp/futureshops-pixelmon-gametest-cart-commands-20260903.log`. `pixelmonServerShopSellRefusalBeforeItemRemoval` opened the loaded default shop, submitted a valid diamond sell request, and verified that the item count and custody store were unchanged after the capability refusal. `pixelmonPlayerShopBuyRefusalBeforeSaleEscrow` configured a valid money admin shop buy and verified that the item, sale escrow, and custody state were unchanged after refusal. `pixelmonMoneyItemRefusalBeforeConsumption` used a money item through the live server path and verified that the item stack and custody store were unchanged. `pixelmonCartAndPhysicalCommandRefusal` submitted a valid cart buy and invoked the physical withdraw and deposit commands, verifying no stock, inventory, custody, or money item change. The same sixteen tests ran with the internal provider selected while Pixelmon was present, with Pixelmon-only tests skipping their external branch, in `/tmp/futureshops-internal-gametest-cart-commands-20260903-eaebabf.log`.

The current artifact `a3b3a1bdc1014efcbadf492b20599acb7f7dd35f41b53489de857a104447ac48` was rerun in a fresh copied profile with Pixelmon absent and `provider = "internal"`. It reached `FutureShops common setup complete`, `FutureShops server starting`, and `Done`, then stopped cleanly. The sanitized log is `/tmp/futureshops-standard-packaged-final-log.qjoDkl` with SHA 256 `bc03cf906ff40665b1710cc9b878207fa6a8fe5e323633d26f46fc8e2dc94e63`. The copied profile verified `eula=true` and was removed after the log was hashed.

The current artifact was also rerun in a fresh copied profile with the incompatible Pixelmon `9.3.1` jar and `provider = "pixelmon"`. The adapter logged `Pixelmon economy adapter unavailable, pixelmon version is unsupported`, the server reached `Done`, and it stopped cleanly. The sanitized log is `/tmp/futureshops-incompatible-packaged-final-log.8Yuou7` with SHA 256 `5e35b3ed8f3e520194bf16e30827501cf323221d39a290024cda7a8a606386a0`. The profile verified `eula=true` and was removed after the log was hashed. The external Pixelmon jar bundles GeckoLib, so no separate GeckoLib jar was installed for this incompatibility check.

The clean NeoForge client smoke temporarily removed Pixelmon from `run/mods` and launched the same source and rebuilt dependency set with `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 xvfb-run -a timeout 120 bash ./gradlew runClient --no-daemon`. The client loaded FutureShops `2.3.0`, Minecraft `1.21.1`, and NeoForge `21.1.248`, reached `FutureShops common setup complete`, and started the sound engine without an error or exception. The complete log is `/tmp/futureshops-neoforge-client-no-pixelmon-20260903.log`. The exact Pixelmon jar was restored afterward with SHA 256 `9020393f98382ae8794ef2694e7bec1984c1a0eca735ea3eea06e0cb151c61f2`.

The incompatible runtime profile `/tmp/futureshops-pixelmon-incompatible.MapAQG` used the same FutureShops `2.3.0` jar with Pixelmon `9.3.1`, NeoForge `21.1.248`, Java 21, and `eula=true`. Its bounded command was `PATH=/usr/lib/jvm/java-21-openjdk-amd64/bin:$PATH JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 timeout 120 bash ./run.sh nogui`. The server reached `Done (5.698s)` and `FutureShops server starting.` while logging `Pixelmon economy adapter unavailable, pixelmon version is unsupported`. The complete log is `/tmp/futureshops-pixelmon-incompatible-9-3-1-20260903.log`. This proves that an incompatible Pixelmon version does not register the adapter while the rest of the server remains available.

An earlier artifact was also rerun with the exact external Pixelmon `9.3.1` jar, NeoForge `21.1.248`, Java 21, and `provider = "pixelmon"` in a fresh copied profile. The server reached `FutureShops common setup complete`, logged `Pixelmon economy adapter unavailable, pixelmon version is unsupported`, reached `Done`, and stopped cleanly. The sanitized log is `/tmp/futureshops-current-incompatible-log.upMyui` with SHA 256 `b57b25128654bb084f6d1228a449442cb726f6b7bd66bc183a95da1eb6c82df5`. Pixelmon emitted unrelated legacy tag and world data errors while loading its old artifact; no FutureShops error or exception occurred. The copied profile verified `eula=true` and was removed after the log was hashed.

The exact disposable profile `/tmp/futureshops-pixelmon-exact.xIDOL4` was assembled with the reviewed FutureShops, Pixelmon 9.4.0, GeckoLib 4.8.4, and NeoForge 21.1.248 bytes. Its bounded preacceptance launch discovered every expected mod and stopped at `eula=false` with exit zero in `/tmp/futureshops-pixelmon-exact-eula-false-20260903.log`. This proves exact mod discovery and classpath loading only. It does not claim Pixelmon economy behavior because the terms gate prevented full launch.

The exact hybrid profile `/tmp/futureshops-youer-pixelmon-248.zhVzs4` adds Youer `1.21.1-d4a204a0`, Vault 1.7.3, FinalEconomy 1.0.9, PixelmonEconomyBridge 1.1.6, and EverNifeCore 2.0.4.4. Its Java 21 bounded preacceptance launch reached NeoForge 21.1.248 and stopped at `eula=false` with exit zero in `/tmp/futureshops-youer-pixelmon-248-java21-eula-false-20260903.log`. This proves exact hybrid assembly and loader discovery only. It does not claim plugin loading or economy behavior because the terms gate prevented full launch.

### Authorized full startup rerun

The owner authorized `eula=true` for disposable validation. The exact hybrid profile was rerun on port `25566` with `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 timeout 120 /usr/lib/jvm/java-21-openjdk-amd64/bin/java -Xms2G -Xmx4G -jar youer.jar nogui`. The log reached `Done (27.779s)`, `FutureShops server starting.`, and bounded shutdown after loading Pixelmon `9.4.0`, Vault `1.7.3`, FinalEconomy `1.0.9`, EverNifeCore `2.0.4.4`, and PixelmonEconomyBridge `1.1.6`. The bridge logged Pixelmon integration and FinalEconomy discovery. The complete log is `/tmp/futureshops-youer-pixelmon-248.zhVzs4/logs/latest.log`.

The exact Pixelmon only profile was rerun with the corrected Java 21 disposable probe and current commit bound jar using `PATH=/usr/lib/jvm/java-21-openjdk-amd64/bin:$PATH JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 timeout 70 /usr/lib/jvm/java-21-openjdk-amd64/bin/java @user_jvm_args.txt @libraries/net/neoforged/neoforge/21.1.248/unix_args.txt nogui`. It reached `Done (0.887s)` and `FutureShops server starting.` with the exact FutureShops `2.3.0`, Pixelmon `9.4.0`, GeckoLib `4.8.4`, Minecraft `1.21.1`, and NeoForge `21.1.248` stack. The probe confirmed the live query and typed `CAPABILITY_MISSING` preflight and withdrawal refusal. The run log is `/tmp/futureshops-pixelmon-packaged-java21-6f7274.log` with SHA 256 `a46bd02cc4960d8084c292fd7c952ae7f37b31b553e97cba184c5dac360dc72f`.

The reruns prove full startup and optional class loading under owner authorized terms. The packaged startup runs do not invoke a production player account or a money mutation. The disposable probe below exercises one isolated account query, and the GameTest below exercises disposable server player routes. The Pixelmon provider therefore remains query and precheck capable with deterministic mutation refusal.

### Disposable live provider probe

The exact Pixelmon only profile also loaded a disposable runtime probe compiled for Java 21. The probe queried UUID `00000000-0000-0000-0000-000000000001`, then submitted a one minor unit preflight and withdrawal through the public `EconomyTransactionCoordinator`. The probe jar SHA 256 is `1756394e915f718e7b41d962fec62925408fca38955ddb749692698803dab476`.

The bounded command was the direct Java 21 NeoForge launch from `/tmp/futureshops-pixelmon-exact.xIDOL4`. The complete log is `/tmp/futureshops-pixelmon-packaged-java21-6f7274.log`. It reached `Done (0.887s)` and `FutureShops server starting.` and emitted:

```text
FUTURESHOPS_PROBE lifecycle=EconomyLifecycleSnapshot[providerId=pixelmon, lifecycle=READY, diagnostic=, acceptsQueries=true, acceptsMutations=true] resultStatus=CONFIRMED resultError=NONE resultDiagnostic= resultValue=Optional[BalanceSnapshot[playerId=00000000-0000-0000-0000-000000000001, balanceMinorUnits=0]] preflightStatus=UNAVAILABLE preflightError=CAPABILITY_MISSING preflightDiagnostic=provider lacks the capabilities required by this mutation mutationStatus=UNAVAILABLE mutationError=CAPABILITY_MISSING mutationDiagnostic=provider lacks the capabilities required by this mutation
```

The lifecycle snapshot reports server readiness and query availability. The coordinator capability gate remains authoritative for mutation eligibility. The query returned the disposable account at zero minor units, and the withdrawal stopped with typed `CAPABILITY_MISSING` before journal, custody, or Pixelmon `take` or `add` calls. This is live provider query and refusal evidence only. It is not a successful external debit or a recovery claim.

The rebuilt GameTest also created live server player fixtures and routed withdrawal and deposit through `BalanceManager` plus transfer through `ShopModAPI`. Each public route refused the Pixelmon mutation, and the tests confirmed that no custody record was created. The valid server shop sell and player shop buy cases additionally confirm that inventory and sale escrow stay unchanged. `ExternalEconomyMutationSurfaceSourceTest` additionally checks the server shop buy and sell services, player shop service, money item, deposit and withdrawal commands, pay command, admin command, and public API delegation. Every monetary entry point is bound to `BalanceManager` or the coordinator capability gate, and direct coordinator mutation methods retain their internal preflight. This covers the public player entry points without calling the unsafe Pixelmon boolean mutation methods.

The exact hybrid profile was then loaded with the same Java 21 disposable probe and current commit bound FutureShops jar. The bounded command was `PATH=/usr/lib/jvm/java-21-openjdk-amd64/bin:$PATH JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 timeout 90 /usr/lib/jvm/java-21-openjdk-amd64/bin/java -Xms2G -Xmx4G -jar youer.jar nogui` from `/tmp/futureshops-youer-pixelmon-248.zhVzs4`. The complete log is `/tmp/futureshops-hybrid-packaged-java21-6f7274.log` with SHA 256 `955903d069416e187b2aedcb3ba5f416dd68ec05324c8b55e829cd595bb35569`. It loaded Vault `1.7.3`, FinalEconomy `1.0.9`, EverNifeCore `2.0.4.4`, and PixelmonEconomyBridge `1.1.6`, reached the FutureShops server startup, and emitted the same confirmed query followed by typed `CAPABILITY_MISSING` preflight and withdrawal refusal. This proves that the loaded Vault and bridge stack does not bypass the Pixelmon mutation capability gate. The probe made no external mutation.

### Unclean restart evidence

The exact Pixelmon only profile was started with the Java 21 probe, waited until `FutureShops server starting.`, and then terminated with `kill -9`. The termination returned exit `137` and produced `/tmp/futureshops-pixelmon-unclean-kill-20260903-6f7274-a.log` without a shutdown marker. Its SHA 256 is `f8051511c958c6ec2234f621b7b4931d17c13869402e192fe6e88593b62e2b68`. A subsequent Java 21 restart reached `Done (0.880s)` and `FutureShops server starting.` in `/tmp/futureshops-pixelmon-recovery-restart-20260903-6f7274-c.log`. Its SHA 256 is `c959c61ad487fb5682124625401875fb5837ee949905d35fb5fe25435a8aace7`. The probe again confirmed the live Pixelmon query and refused both preflight and withdrawal with `CAPABILITY_MISSING`. No mutation had been admitted before termination, so this evidence proves restart to ready and deterministic refusal, not external money recovery.

The same exact profile was then stopped through the server console after a clean startup and probe. `/tmp/futureshops-pixelmon-clean-restart-20260903-6f7274.log` records `Done (0.858s)`, `FutureShops server starting.`, the confirmed query with `CAPABILITY_MISSING` mutation refusal, `FutureShops server stopping.`, and all dimensions saved. This run started after the previous clean stop and proves an orderly restart to ready with the same refusal behavior. The log SHA 256 is `d24bab66d84d231161146f8288ec7025d3689ebc18001b699ad67ce99a78087e`.

The rebuilt `build/libs/futureshops-2.3.0.jar` from commit `eaebabf` passed `unzip -tq`. Its SHA 256 is `6f727436c68887bd21a016503cac7d06dbd24e382924a8937ddafc86ef3b3925`. Its SHA 512 is `4a5b2eb9014a38c1492ad8f69e628d60f6e3a00ce5ee37a082fb5b548cd193ed3bcfa8059ff213c9f72d89dd960a2b1faa71510010b3a0ac5eed2cdff6b9bdce`. The installed FutureShops bytes in both exact disposable profiles match this SHA 256. The archive contains FutureShops adapter and GameTest classes, but no `com/pixelmonmod`, Bukkit, Spigot, Vault, bridge, or external test fixture classes. `jdeps` reports only Java, FutureShops, and NeoForge references for the adapter. No Pixelmon, Bukkit, Spigot, or bridge dependency is declared.

That earlier evidence used source revision `eaebabf` on `envy/phase-002-pixelmon-vault`.

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

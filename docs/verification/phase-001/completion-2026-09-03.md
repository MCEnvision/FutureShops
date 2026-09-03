# phase 001 completion packet

## Scope

This packet records the verified FutureShops 2.3.0 economy core for Minecraft 1.21.1 and NeoForge 21.1.248. The candidate source is commit `d1aa0f2` on `envy/phase-001-2.3.0-economy`. The packet is ready for the required pull request integration into `1.21.1`; the merge commit and signed phase tag are recorded after that integration completes.

## Implemented boundary

The internal provider remains the restart-only default. All balance mutations and monetary surfaces use the server authoritative coordinator. The coordinator validates lifecycle, capabilities, request identity, exact amounts, durable intent, receipts, custody, claims, compensation, and recovery before publishing a success result. New writes are rejected outside `READY`. Unclean state becomes recovery or frozen state, and no unknown external effect is retried or repaired from a local balance mirror.

Player shop sales persist exact item stacks through prepared, removed, delivered, claimed, refunded, and recovery-required states. Player shop barter payments persist the same custody facts through prepared, removed, stored, complete, refunded, and recovery-required states. Offline proceeds remain durable claims. Reconnect and concurrent duplicate requests replay the original request and receipt without another provider mutation.

Physical money remains registered and save compatible. It is inert unless the selected provider is ready internal. No ATM path, external balance mirror, bundled bridge, or optional provider class is present in the candidate jar.

## Verification evidence

The complete unit suite passed with `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 bash ./gradlew test --no-daemon`. The complete build passed with `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 bash ./gradlew build --no-daemon`.

The real NeoForge GameTest server passed all eleven required tests in `/tmp/futureshops-gametest-multiplayer-20260903.log`. Coverage includes registration, public mutation routes, two server players with independent balances, sale escrow lifecycle, sale escrow unclean restart after preparation, removal, and delivery, barter escrow unclean restart after removal and storage, offline claim recovery, and reconnect replay.

The bounded dedicated server reached `FutureShops common setup complete` and `FutureShops server starting` in `/tmp/futureshops-runserver-final-d1aa0f2.log` before the expected timeout exit `124`. The Xvfb client reached `FutureShops common setup complete` in `/tmp/futureshops-runclient-final-d1aa0f2.log` before the expected timeout exit `124`; only the existing GeckoLib and Mixin Java 21 class version warnings remained.

The candidate `build/libs/futureshops-2.3.0.jar` passed `unzip -tq`. Its SHA 256 is `af9552cecc637fdcab7e187085803885486fa9c7dbc5c3c45902bcc825ee019c`. Its SHA 512 is `e594b128d8ed04133104e158a500251260fcb448335c5b507feceba274d1b77b4aa219ede814c474f06eb41981ea079ad9b78d30b3a1afcf821d131d332a25ae`. The archive contains no Pixelmon, Vault, Bukkit, Spigot, bridge, or test fixture classes. `codegraph status .` is current and `git diff --check` passes.

## External integration handoff

The exact Pixelmon 1.21.1 9.4.0 artifact remains an external Phase 002 input. Its `BankAccount` surface supports balance, `hasBalance`, and boolean `add` and `take`, but does not expose durable request identities, receipt lookup, idempotent retry, or an outcome journal. The Phase 002 adapter may therefore implement safe query and precheck, but must refuse every mutation surface until a reviewed bridge proves those capabilities. A separately installed Vault bridge remains isolated from this jar and may register only after the same evidence is verified.

Issue 66 remains the continuation record for the 3.0.0 Forge 1.20.1 implementation and the future 3.0.0 NeoForge 1.21.1 port. It is not modified until Phase 003 artifact validation as required by the plan.

## Integration gate

The remaining phase action is pull request integration into `1.21.1`, required checks and independent review, merge verification on the target branch, and a signed phase tag. Once that gate passes, Phase 002 can begin from the integrated commit with the frozen provider API, coordinator, escrow, recovery, and exact Pixelmon safety boundary recorded above.

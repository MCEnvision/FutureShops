# phase 009 dependency and persistence audit

## dependency graph

The current repository reports four open Dependabot alerts in the Forge build manifest. They concern `org.jline:jline-reader`, `io.netty:netty-handler`, and `org.apache.logging.log4j:log4j-api`. The resolved graph shows `io.netty:netty-handler:4.1.82.Final` arriving through `net.minecraft:client:1.20.1` and `net.minecraftforge:forge:1.20.1-47.4.20`, not through a FutureShops direct dependency. The build script declares Forge, the local GeckoLib artifact, McLib, Mixin, and JUnit only. No direct override was added because doing so would change the Forge toolchain boundary without a compatible 1.20.1 artifact proof.

The alerts remain tracked under GitHub issue 9 as platform inherited dependency findings. The resolved runtime graph selected `org.jline:jline-reader:3.12.1` and `org.apache.logging.log4j:log4j-api:2.19.0` through Forge and Minecraft. They are not treated as resolved by a string scan or by a forced version override. The production archive scan must continue to verify that unapproved provider bridges and development-only files are absent from the jar.

The phase build produced `build/libs/futureshops-3.0.0-beta.2.jar` with sha256 `40367d50eaa7da8c0260355c97c4cb1e6baea8781f52a256a77d24b44d9ffe5c`. The archive contains only compiled FutureShops classes and resources. No Bukkit, Spigot, Paper, Vault, or test runtime archive entry was found.

## persistence review

The reviewed stores and readers include the escrow runtime, WAL generations, receipts, claims, custody and item inventory journals, stock reservations, shop and market state, checkpoints, configuration snapshots, legacy wallet and mint migrations, and player settlement state. Existing readers validate schema, length, type, checksum, duplicate identity, binding and parent linkage before installing a complete state. Writers retain the existing WAL ordering, durable flush, bounded records, temporary replacement, and recovery gate.

The phase 009 static review found no new confirmed persistence defect. Existing repair behavior preserves invalid input for bounded operator inspection and refuses unsafe partial recovery. It does not fabricate receipts, redirect claims, or delete state to make startup succeed.

## verification commands

`./gradlew --no-daemon test --console=plain` completed successfully on Java 17.

`./gradlew --no-daemon runGameTestServer -PverificationGameDirectory=.phase009-gametest-runtime-20260925b --console=plain` completed successfully. The server log reported `all 9 required tests passed` and Forge resolved the game directory to the requested disposable path.

## proof limits

No dependency remediation was applied because the active alerts are inherited from the Forge and Minecraft toolchain and no safe compatible override was proven. No production world, player save, or external provider state was used in this audit.

# CLAUDE.md

## Project snapshot (read this first)
- This repo is a **Minecraft Forge 1.20.1 mod** (`forge_version=47.4.20`) using **official Mojang mappings** (`mapping_channel=official`, `mapping_version=1.20.1`) from `gradle.properties`.
- Current Java code is a Forge template baseline in `src/main/java/com/enviouse/futureshops/` (`Futureshops.java`, `Config.java`), while the intended full system is documented in `Mod Implementation/shop-mod-complete-specification.md`.
- Treat the spec as target architecture guidance; treat current source as the executable truth until features are implemented.

## Architecture and boundaries
- Entry point is `@Mod(Futureshops.MODID)` in `src/main/java/com/enviouse/futureshops/Futureshops.java`.
- Registries use `DeferredRegister` (`BLOCKS`, `ITEMS`, `CREATIVE_MODE_TABS`) and are wired on the mod event bus in the constructor.
- Config follows Forge `ForgeConfigSpec` in `src/main/java/com/enviouse/futureshops/Config.java`, loaded via `ModLoadingContext.registerConfig(ModConfig.Type.COMMON, Config.SPEC)`.
- Resource metadata is templated: `processResources` expands Gradle properties into `src/main/resources/META-INF/mods.toml` and `src/main/resources/pack.mcmeta`.
- Mixin is enabled (`org.spongepowered.mixin` plugin, `futureshops.mixins.json`), but no mixin classes are implemented yet.

## Non-negotiable version/mapping rules
- Keep Minecraft/Forge/mappings aligned exactly with `gradle.properties` unless explicitly asked to migrate:
  - `minecraft_version=1.20.1`
  - `forge_version=47.4.20`
  - `mapping_channel=official`
  - `mapping_version=1.20.1`
- Do not introduce Yarn/Fabric names or MCP-era APIs in new code.
- Use Java 17 features only (`java.toolchain.languageVersion = 17` in `build.gradle`).

## Developer workflows (Windows PowerShell)
- Run client dev instance: `./gradlew.bat runClient`
- Run dedicated server dev instance: `./gradlew.bat runServer`
- Run data generation: `./gradlew.bat runData` (writes to `src/generated/resources/` and `run-data/`)
- Build jar: `./gradlew.bat build` (jar is reobfuscated via `reobfJar` finalize step)
- Run game tests: `./gradlew.bat runGameTestServer`

## Code patterns to follow in this repo
- New content registration should match `RegistryObject` + `DeferredRegister` style from `Futureshops.java`.
- Event wiring should prefer mod event bus listeners for lifecycle and Forge event bus for gameplay/server events.
- Keep runtime IDs/constants centralized (`MODID` in `Futureshops.java`) and consistent with `mods.toml` `${mod_id}` expansion.
- If adding generated assets/datagen outputs, keep them under `src/generated/resources` (already added to main resources in `build.gradle`).

## Spec-driven implementation guidance
- For upcoming systems (shop UI, economy, packets, storage linking, anti-dupe), use `Mod Implementation/shop-mod-complete-specification.md` as the authoritative behavior contract.
- High-risk areas in the spec that must be server-authoritative: packet validation, transaction atomicity, CoinItem anti-dupe checks, and storage-link integrity checks.
- Prefer implementing spec modules incrementally in package domains (client/server/network/data) rather than monolithic classes.

## Agent execution policy
- Do not pause to ask for approval before routine implementation work; complete the requested coding changes first.
- After finishing edits and validation, ask concise follow-up questions about what to do next (scope expansion, refinements, extra checks, or next feature).
- Ask clarifying questions before coding only when requirements are materially ambiguous or blocked by missing information.
- End implementation responses with a short numbered list of concrete next-step options (typically 2-3 choices).

## Before opening a PR or commit
- Verify compile/build after edits (`./gradlew.bat build`).
- If you touched registries/resources, also run `./gradlew.bat runClient` at least once.
- If you touched datagen inputs/providers, run `./gradlew.bat runData` and include generated outputs as needed.

## Audit maintenance policy
- After every feature implementation or modification pass, update `Mod Implementation/mod-status-audit.md` to reflect: implemented scope, remaining gaps, and any changed priorities.
- Treat `mod-status-audit.md` as the live progress ledger tied to `shop-mod-complete-specification.md`.

# Phase 004 path, parser, privacy and dependency review

## Paths and parsers

The Forge configuration root is normalized and checked before use. The FutureShops, shops and recovery directories must be real directories and cannot be symbolic links. Catalog discovery rejects symbolic links, non regular files, more than 256 JSON files and files larger than 8 MiB. Legacy arrays are bounded before allocation. Strict UTF 8 decoding rejects malformed input. String fields and exact SNBT are bounded before parsing. The effective admin listing limit is the configured value clamped to 1 through the shared 10,000 listing parser maximum.

`AdminShopConfigWriter` now validates every existing parent component with no follow checks before reading or writing. It rejects unsafe parents and malformed UTF 8, and writes only below the approved catalog root. Bazaar product loading already applies no follow checks, file and aggregate byte bounds, product count limits and schema validation. Escrow journals, checkpoints and replay receipts use bounded codecs, normalized paths, atomic replacement and durable recovery helpers.

Issue [#40](https://github.com/MCEnvision/FutureShops/issues/40) was created before the catalog repair. Its acceptance is symlink rejection, bounded enumeration, safe fallback and regression coverage on both supported lines.

## Privacy and logging

Logs and public evidence contain only sanitized identifiers, bounded failure context and recovery handles. Credentials, tokens, full inventories, private configuration, raw NBT and private player evidence are excluded. Error responses do not echo arbitrary client payloads. Public issue records contain the risk class and safe reproduction contract, not exploit payloads.

## Dependencies and artifacts

Forge is pinned to Java 17, Forge 47.4.20, ForgeGradle 6.0, MixinGradle 0.7.38, Mixin 0.8.5, GeckoLib Forge 4.8.3 and mclib 20. NeoForge is reviewed independently at its approved pinned revision. The local GeckoLib JAR is inspected by path, checksum and JAR listing. `mods.toml` is checked for declared dependencies and no undeclared classes are bundled.

The GitHub dependency graph is enabled. The inherited open medium Log4j API alert remains a platform dependency disposition and is not silently changed by this phase. Other observed alerts are dismissed with repository records. A local gitleaks executable is not installed, so the repository secret scan uses tracked file inspection, GitHub secret scanning state and the release validation workflow rather than claiming a tool result that was not run.

Required artifact checks are `jar tf`, metadata inspection, dependency report, SHA 256 and SHA 512 checksums, source commit binding, generated resource review and complete diff inspection. Build output and runtime directories remain untracked.

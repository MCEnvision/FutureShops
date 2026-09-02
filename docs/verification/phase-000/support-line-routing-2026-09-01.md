# Phase 000 support line routing

Captured 2026-09-01 for P000-TASK-007. The routing record is read only and separates Forge 1.20.1 from NeoForge 1.21.1.

## Forge route

| field | value |
| --- | --- |
| canonical base | `origin/1.20.1` at `c6709e12ca7084ee068b2497a577b8d47c12f6fd` |
| candidate | `3.0.0-beta.2` |
| loader and game | Forge 47.4.20, Minecraft 1.20.1 |
| toolchain | Java 17, Gradle 8.14.4, official 1.20.1 mappings |
| branch boundary | one sequential `envy/` phase branch from latest merged `1.20.1` |
| pull request base | `1.20.1` |
| prohibited transfer | NeoForge APIs, metadata, persistence, networking, or client source without a separate affected line proof |

## NeoForge route

| field | value |
| --- | --- |
| canonical base | `origin/1.21.1` at `247d8f6842bfa1f586e5b18a9aab67cabd3db89f` |
| candidate | `2.2.1` |
| loader and game | NeoForge 21.1.233, Minecraft 1.21.1 |
| toolchain | Java 21, Gradle 8.8, ModDevGradle 2.0.141 |
| branch boundary | CORE-PHASE-001 branch derived from current `1.21.1` |
| candidate branch | `envy/issue_22_neoforge` at `bfba91f7b0c51b03d07117c4f1851c38a98f6186` |
| pull request base | `1.21.1` |
| prohibited transfer | ForgeGradle, Forge 1.20.1, Java 17, Forge persistence, or Forge only audit changes |

## Ancestry and changed paths

The issue 22 candidate is one commit ahead of the captured `origin/1.21.1` head and has no commits behind it at capture. Its changed path set is limited to `PORTING_NOTES.md`, `README.md`, `gradle.properties`, the NeoForge client screen package, and `ShopScreenBackgroundPolicyTest.java`. This is a candidate inventory only. CORE-PHASE-001 must requery the refs and rerun the exact candidate checks before integration.

The Forge and NeoForge support heads are unrelated object IDs with separate build metadata. No cross line cherry pick, merge, or source transfer was performed.

## Remote boundary

The repository default branch is `1.20.1`. The active main protection ruleset targets only `refs/heads/1.20.1`. The NeoForge line remains independently routed through `1.21.1`; its candidate integration cannot be represented as a Forge change. Current remote governance findings are recorded in the GitHub baseline packet for later reconciliation.

## Status

Support line routing is proven for baseline and candidate selection. Build task inventories and runtime results remain P000-TASK-008 and P000-TASK-009 work.

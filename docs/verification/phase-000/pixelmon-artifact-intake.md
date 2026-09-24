# Pixelmon artifact intake

Observation date: 2026-09-24
Phase: CORE-PHASE-000
Task: P000-TASK-003
Requirement: CORE-REQ-001
External prerequisite: EXT-001

## Exact candidate

The inspected candidate was acquired from the official Modrinth version record and CDN, then removed after descriptor inspection.

| Field | Observed value |
| --- | --- |
| Modrinth version API | `https://api.modrinth.com/v2/version/KjmzoXMR` |
| Project | `59ZceYlU` |
| Version ID | `KjmzoXMR` |
| Version number | `9.2.3` |
| Version type | `beta` |
| Game version | `1.20.1` |
| Loader | `forge` |
| Artifact URL | `https://cdn.modrinth.com/data/59ZceYlU/versions/KjmzoXMR/Pixelmon-1.20.1-9.2.3-universal.jar` |
| Artifact filename | `Pixelmon-1.20.1-9.2.3-universal.jar` |
| Size | `411152237` bytes |
| Computed SHA-256 | `8f2777f5a7cd2b5fc48ac3f5531d434af4120c7d40adc9929d5a3737c404246a` |
| Computed SHA-512 | `3a9c6f375214c6d93c6cce8235e8a206e8f9731be8e168a254e78539087080796d97f0b22cb9a6db09d901c72e2e1ae53b9f2484761fb310479ce9f84ac9b145` |
| Expected prerequisite SHA-512 | Matches the computed SHA-512 exactly |

The current Modrinth API response returned `null` for both file hash fields even though the exact prerequisite SHA-512 is known and the downloaded bytes match it. The API response is retained as provenance, and the hash match is independently verified from the exact CDN bytes. No substitute or repackaged artifact was used.

## Manifest and loader descriptors

The archive contains `META-INF/mods.toml`. Its relevant values are `modLoader=javafml`, `loaderVersion=[47,)`, `modId=pixelmon`, `version=9.2.3`, and `license=Copyright 2014-2023 Ikara Software, All rights reserved.`. The declared Forge dependency is `[47.1.28,)`, and the Minecraft dependency is `[1.20.1,1.21)`. The archive manifest reports `Implementation-Version: 9.2.3-pipe21439` and an implementation timestamp of `2023-10-04T13:57:06+0000`.

## Native storage descriptor intake

`javap -public -s` was run against the exact artifact for the storage boundary candidates.

| Class or interface | Observed descriptors relevant to a future adapter |
| --- | --- |
| `com.pixelmonmod.pixelmon.api.storage.PlayerPartyStorage` | `getPlayerUUID()Ljava/util/UUID;`, `writeToNBT(Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/nbt/CompoundTag;`, `readFromNBT(Lnet/minecraft/nbt/CompoundTag;)Ljava/util/concurrent/CompletableFuture;`, `getShouldSave()Z`, `hasBalance(Ljava/math/BigDecimal;)Z`, `getBalance()Ljava/math/BigDecimal;` |
| `com.pixelmonmod.pixelmon.api.storage.StorageProxy` | `getPartyNow(ServerPlayer)`, `getParty(UUID)`, `getStorageManager()`, `getSaveScheduler()`, `getSaveAdapter()`, and the corresponding setter descriptors. |
| `com.pixelmonmod.pixelmon.api.storage.StorageSaveScheduler` | `getScheduler()Ljava/util/concurrent/Executor;`, `onServerStopping(ServerStoppingEvent)V`, and default `save(PokemonStorage)V`. |
| `com.pixelmonmod.pixelmon.api.storage.StorageSaveAdapter` | `save(PokemonStorage)V` and `load(UUID, Class)Ljava/util/concurrent/CompletableFuture;`. |

The target classes exist and expose a plausible native storage boundary. No writer call graph, save-image ordering, receipt coupling, or crash atomicity is concluded from descriptors. No mixin or transformation is enabled by this intake. The exact class descriptors are an input to `CORE-PHASE-004`, not support proof.

## License, security, and provenance limits

The license string is retained exactly as declared by the artifact. This report does not grant redistribution rights or change the project licensing decision. The artifact was downloaded into a unique temporary directory, hashed, inspected, and deleted. No artifact copy remains in the repository or temporary directory.

The artifact has not yet passed the later dependency, classpath isolation, server startup, native writer, process restart, or security review gates. No bundled third party code conclusion is made here. The later security task must inspect the exact candidate and the final packaged jar under its own evidence rules.

## Proof limit

This intake proves the exact candidate identity, official provenance, Forge and Minecraft metadata, license declaration, and storage descriptor existence. It does not prove functional Pixelmon support, an atomic native writer, a durable provider receipt, a compatible mixin, account mutation, restart lookup, or world persistence. Those claims remain open for `CORE-PHASE-004` and `CORE-PHASE-010`.

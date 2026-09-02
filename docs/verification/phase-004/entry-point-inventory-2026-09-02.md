# Phase 004 entry point inventory

Revision under review: Forge `254a8788aa9a1d2f228abd84665882de5b69c075` on `1.20.1`.
The approved NeoForge comparison revision is `51cc7c1831079c12a6d6070bd16873e9fbcad01b` on `1.21.1`.

## Trust boundary inventory

| Boundary | Inputs | Authority and required controls | Evidence owner |
| --- | --- | --- | --- |
| Brigadier commands | Literals, aliases, arguments, source and targets | Server dispatcher, sender type, operator level or configured permission, ownership, confirmation, readiness, bounded arguments | Command matrix |
| Forge network channel | Direction, bytes, identifiers, quantities, NBT, UUIDs, positions and actions | Strict protocol, bounded decoding, active server player, route and session identity, permission, ownership, replay and main thread handoff | Packet matrix |
| NeoForge network channel | The same classes of client supplied fields on its independent channel | Independent loader registration, bounded decoding, sender binding, permission, state and main thread handoff | Packet matrix |
| Catalog and configuration files | Paths, directory entries, links, JSON, TOML, SNBT and restored files | Normalized approved roots, no follow checks, type and byte limits, strict UTF 8, schema validation and atomic replacement | Path and parser matrix |
| Persistent market state | Journals, checkpoints, ledgers, receipts, claims and saved data | Versioned codecs, bounded records, request identity, conservation and recovery lineage | Persistence handoff |
| Client presentation and responses | Balances, history, inventories, NBT, UUID relationships and errors | Recipient binding, field minimization, bounded payloads and sanitized diagnostics | Privacy matrix |
| Build and packaged artifacts | Gradle plugins, dependencies, local libraries, generated resources and JAR contents | Pinned versions, repository restrictions, metadata review, checksums and final JAR inspection | Dependency matrix |

## Entry point counts

Forge contains 16 command providers, 55 client to server packet classes and 34 server to client packet classes. NeoForge contains 14 command providers, 21 client to server packet classes and 16 server to client packet classes. Every Forge C2S class is checked for an explicit string bound after the Phase 004 repair. The NeoForge comparison is kept separate because its package, loader API and protocol registration are independent.

All C2S handlers bind work to `ServerPlayer`, enqueue mutation on the server thread, and perform domain validation before state changes. S2C payloads are treated as server owned presentation snapshots and are not accepted as authority by subsequent mutation requests.

## Findings at freeze

Two repository owned findings were verified before repair. Issue [#40](https://github.com/MCEnvision/FutureShops/issues/40) covers symlinked catalog roots and files. Issue [#41](https://github.com/MCEnvision/FutureShops/issues/41) covers unbounded incoming text fields. No credential, token, or private player data was included in the evidence.

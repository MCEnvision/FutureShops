# Phase 004 threat model

## Assets

The protected assets are wallet minor units, physical currency, stock, listed items, custody, claims, bids, orders, settlements, player identity, permissions, catalog and configuration integrity, journals and receipts, server availability, private histories, NBT and supply chain inputs.

## Actors and capabilities

The review covers an unauthenticated network peer, a connected player sending arbitrary bytes, a player exceeding ownership or replay boundaries, operators at levels 2 through 4, console and supported command block sources, a malicious local catalog or symlink, a malformed restored world fixture, and a curious reader of logs or public artifacts. Accidental duplicate delivery, disconnect, retry, restart, stale state and partial I/O are treated as equivalent failure paths.

## Controls

The server owns value, stock, listings, permissions, lifecycle state and recovery. Packet direction, decode bounds, sender identity, route nonce, request UUID, ownership, distance, registry membership, quantities, NBT, revisions, readiness, rate and replay state are checked before mutation. Commands apply source and permission predicates and explicit confirmation to destructive operations. Files stay below approved normalized roots and reject links, oversized or malformed content. Journals, claims and receipts preserve exactly once outcomes and no loss on delivery failure. Logs are bounded and sanitized. Dependencies and packaged JAR contents are pinned and inspected.

## Residual risks handed forward

Persistence schema migration, world corruption and full backup and restore closure remain Phase 005 scope. Cross component lifecycle and backend integration remain Phase 006 scope. Final repeated candidate audits and publication preparation remain Phase 007 scope. These are explicit handoffs, not unclassified Phase 004 findings.

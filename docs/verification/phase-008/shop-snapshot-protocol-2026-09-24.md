# phase 008 shop snapshot protocol entry

Captured 2026-09-24 for P008-TASK-001 on the Forge 1.20.1 beta 3 phase branch.

## implementation

The Forge protocol is now version 60. `S2CShopDataPacket` carries an authoritative
whole shop snapshot revision. Buy and sell requests retain the existing request UUID and
payment fields while carrying the observed revision. Their responses carry the current
revision and a typed `stale_snapshot` reason. Active `ShopSession` records advance their
revision when the server sends a catalog refresh. The server checks the session and
revision after replay lookup and before provider, inventory, custody, wallet, or success
effects. A stale response triggers a silent authoritative refresh.

`ShopClientState` rejects older same shop snapshots. Detail and cart submissions use the
revision they displayed, while response handling preserves cart correlation and shows a
localized refresh message. Reopen and shop changes reset the client revision through the
existing reset and session paths. Response reasons are bounded machine readable fields
before they are decoded or projected into client status.

## verification

Focused and complete Forge tests passed with Java 17. The complete suite reported 2,020
tests with zero failures, and `./gradlew test build` passed. The candidate artifact is
`build/libs/futureshops-3.0.0-beta.2.jar` with SHA256
`ea55acd511083e440df584eb586710a38d54e08e4186f2877196ba4125125e9a`. Tests cover buy
and sell codec preservation, stale reason and revision fields, session revision
advancement, older snapshot rejection, wire round trips, and the existing payment and
market regressions. The full suite now reports 2,022 tests with zero failures or errors.
`git diff --check` passed. Dedicated server stale request evidence
and client input evidence remain required by P008-TASK-002 through P008-TASK-005.

## boundary

This entry does not claim client rendering, multiplayer stale input, reconnect, donor
ledger closure, publication, or phase completion. No NeoForge source or unsupported
provider behavior was changed.

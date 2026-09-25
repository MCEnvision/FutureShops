# phase 008 production client stale buy evidence

Observation date: 2026-09-24
Phase: CORE-PHASE-008
Tasks: P008-TASK-001 and P008-TASK-002
Requirement: CORE-REQ-015

## matched runtime

The disposable Forge server ran Minecraft 1.20.1 with Forge 47.4.20 and
Temurin Java 17.0.19 from
`.phase008-production-server-20260924`. The laptop client used the existing
PrismLauncher installation, Minecraft 1.20.1, Forge 47.4.20, and Microsoft
Java 17.0.15 in the isolated instance
`FutureShops-phase008-client-20260924`.

Both sides used the same FutureShops 3.0.0-beta.2 candidate with SHA256
`998b71ae4abe996dabf748c577ed344132aeb31fb7325ca8061cc8e0335cf2f8`.
The server used production GeckoLib 4.8.4 with SHA256
`c04abba4fd354dd8c0e8a732f1f05f4dca893d2e1d962b2ab128bbd411478747`.
The client was signed in as `EnVyOnMyMind`, joined the private server at the
verified test endpoint, and the server log recorded the matching player join.

The laptop rendered with an NVIDIA GeForce RTX 5090 Laptop GPU. Minecraft's
master volume was zero before launch. The exact playback node was correlated
to client process 2369220 and read back as `Volume: 0.00 [MUTED]` before the
test and after the shop reload.

## real workflow

The server catalog was reloaded twice through the server console while the
client session remained open. Each reload logged that one active session was
refreshed. The client then displayed the refreshed Diamond listing at 5.01
coins. The owner selected that listing and pressed Buy once through the actual
shop screen.

The resulting screen showed the shop catalog with the wallet reduced from
1,000.00 to 994.99. The server's actual entity data then reported one
`minecraft:diamond` in inventory, and the server awarded the Diamonds
advancement. This proves the normal packet and settlement path completed once
with the refreshed authoritative price. No stale rejection was expected for
this final current click because the screen had already received the reload.

After the second reload changed the displayed price from 5.01 to 5.02, the
owner pressed Buy once on the refreshed screen. The direct window capture then
showed the wallet at 989.97, exactly 5.02 below 994.99. The server console
reported two `minecraft:diamond` items, increasing the verified inventory from
one to two. No duplicate settlement, disconnect, exception, recovery-required
message, or maintenance transition occurred.

The client log recorded the shop opens without a disconnect, protocol error,
or stale response. The server log recorded the join, both catalog refreshes,
and no exception or recovery-required result for the completed buy.

For the ordering check, the client detail initially displayed 5.01 coins while
the server catalog was reloaded to 5.02. The active screen then updated itself
to 5.02 and continued to show the existing diamond without closing the screen,
disconnecting, or changing the wallet. This is direct visual proof of the
authoritative refresh path after a session revision change.

## boundaries

This is production-artifact multiplayer and rendering evidence for a current
buy after authoritative refresh. The completed click was intentionally made
after the refreshed 5.02 price was visible, so it does not by itself prove a
deliberately stale click, response reordering, reconnect, or cart preservation.
Those negative and ordering cases remain covered by the server GameTest and
focused client-state tests. The remaining client gate requires an explicit
stale input scenario.

## cleanup

The client remained isolated and muted during capture. The production server
and client runtimes, logs, and process identities are registered for teardown
after the remaining phase evidence consumers finish. No personal instance,
default audio sink, unrelated process, or user save was changed.

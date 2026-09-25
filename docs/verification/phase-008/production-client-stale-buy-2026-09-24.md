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
to client process 2369220 and read back as `Volume: 0.00 [MUTED]` before each
capture and after the shop reload. The stream was recreated once during the
session and node 148 was muted again before continuing.

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

After the detail screen was captured at 5.02 coins, a further controlled
reload changed the listing to 5.03 coins. The owner clicked the still-open
Buy control before the reload command completed, so this was another normal
current buy rather than a stale request. The screen then refreshed to 5.04
coins and showed three owned diamonds. The server log contains the reload and
the client remains connected. This timing attempt is retained as negative
evidence and is not counted as stale-click proof.

For the ordering check, the client detail initially displayed 5.01 coins while
the server catalog was reloaded to 5.02. The active screen then updated itself
to 5.02 and continued to show the existing diamond without closing the screen,
disconnecting, or changing the wallet. This is direct visual proof of the
authoritative refresh path after a session revision change.

The deliberate stale-input check then used the real multiplayer workflow. The
client opened the Diamond detail at 5.06 coins and selected Wallet Balance in
the purchase confirmation. The isolated server reloaded the same listing to
5.07 coins before the existing confirmation was submitted. The client rejected
the old request and displayed `The offer changed. Review the refreshed details.`
The refreshed detail then showed 5.07 coins and still showed three owned
diamonds. The server console independently reported exactly three
`minecraft:diamond` items after the rejection, proving that no item or wallet
mutation occurred. The captured stale rejection is
`docs/verification/phase-008/stale-buy-rejection-2026-09-24.png` with SHA256
`0147e65236bf7e625d186337214ad1887f092e0091843c43f34710b69c3014bd`.

The client then opened the real cart with three Diamond entries at 5.07 coins
each and selected Wallet Balance in the checkout confirmation. The isolated
server reloaded the listing to 5.09 coins before the existing checkout was
submitted. The client rejected the stale cart request with the same localized
offer changed message. The cart remained open with all three entries and the
refreshed 15.27 coin total after the modal was dismissed. The rejection capture
is `docs/verification/phase-008/stale-cart-rejection-2026-09-24.png` with
SHA256 `af4d1712ee7e3581e0f218375dbeb1415242bd35e2cb473d8e9e789d6c6fee74`.
The preserved cart capture is
`docs/verification/phase-008/stale-cart-preserved-2026-09-24.png` with SHA256
`ec2e2f02b509418729019c1800cc7e454acca94e8310630814f533573eb47772`.

The same client was then kicked by the disposable server with a controlled
reconnect reason. It returned to the server list, joined the exact private
endpoint again through Direct Connection, and reached the multiplayer world
without a client crash or registry mismatch. The server recorded the matching
player login and independently reported the conserved three Diamond inventory
after reconnect. The rendered reconnect capture is
`docs/verification/phase-008/reconnect-after-kick-2026-09-24.png` with SHA256
`a25262394baea619fc20c453adf4e290c2d07cb82af435de5216c5d6da5a4cc6`.

The response ordering gate also passed the focused Java 17 suite with
`ShopClientStateSnapshotTest` and `CartResponsePolicyTest`. The snapshot test
replayed an older same shop revision after newer state and confirmed that the
older response cannot overwrite current state. The cart policy suite confirmed
stale responses keep the pending checkout and cart lines intact. Gradle
reported `BUILD SUCCESSFUL` for both classes.

## boundaries

This is production-artifact multiplayer and rendering evidence for the
current-buy path, a deliberately stale buy, and a deliberately stale cart.
Both stale requests were rejected before settlement, the client received
refreshed authoritative details, the cart remained intact, and inventory
remained conserved. A controlled disconnect and reconnect also returned the
same signed-in player to the multiplayer world with the same inventory.
Response reordering is covered by the focused client state and cart policy
suite. The remaining phase exit work is the checked sequential integration and
donor ledger closure.

## cleanup

The client remained isolated and muted during capture. The production server
and client runtimes, logs, and process identities are registered for teardown
after the remaining phase evidence consumers finish. No personal instance,
default audio sink, unrelated process, or user save was changed.

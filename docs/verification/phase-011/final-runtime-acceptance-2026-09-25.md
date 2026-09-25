# final beta 3 runtime acceptance

Date: 2026-09-25

This record covers the exact final Forge beta 3 pair required by CORE-REQ-015 and
CORE-REQ-019. The test used a disposable dedicated Forge 1.20.1 server on node-1 and
the isolated authenticated EnVisione laptop client in Hyprland workspace 5. Both sides
used Java 17, Forge 47.4.20, FutureShops beta 3, and production GeckoLib 4.8.4.

| component | identity |
| --- | --- |
| minecraft | 1.20.1 |
| forge | 47.4.20 |
| java | eclipse temurin 17.0.19 |
| futureshops | `futureshops-3.0.0-beta.3.jar` |
| futureshops sha256 | `6e44e5c60bb0233fcae67cee57d5f685630069a5ae0bc02817c61c53d2b2298c` |
| futureshops sha512 | `50a53e8ce8d97b85f2b7f4f6aa122bbc37b6e738d21ce15bc0f998798cc537d0e94319bd799d669f20a820477d1d74f8c5b5d828c04eecb6d6238a2933f48a29` |
| geckolib | `geckolib-forge-1.20.1-4.8.4.jar` |
| geckolib sha256 | `c04abba4fd354dd8c0e8a732f1f05f4dca893d2e1d962b2ab128bbd411478747` |

## multiplayer and audio gate

The production server started with `eula=true`, `online-mode=true`, and the private
test endpoint. The authenticated `EnVyOnMyMind` profile joined the disposable world.
The laptop window was identified by its exact Minecraft Forge 1.20.1 title and client
pid. The matching PipeWire playback node reported `Volume: 0.00 [MUTED]` before and
during every interaction. No default sink, microphone, unrelated client, or personal
instance was changed.

## stale buy acceptance

1. The server loaded the diamond money option at 5.00 coins and the client opened the
   shop and confirmed that the diamond detail and purchase dialog both showed 5.00.
2. With the purchase dialog still open, the server shop file was changed to 5.01 coins.
3. The authorized reload command refreshed the server catalog and the active client
   session. The open dialog intentionally retained its original 5.00 snapshot while
   the underlying detail refreshed to 5.01.
4. Confirming the stale dialog did not charge the wallet or deliver another diamond.
   The dialog closed to the explicit `The offer changed. Review the refreshed details.`
   error, and the refreshed detail remained available for review.

The result proves stale money purchases are rejected on the exact final beta 3 Forge
client and server pair. A prior exploratory purchase was not used as evidence. The
accepted result is the second controlled run after the server price mutation and is
represented by the retained screenshot `final-stale-buy-rejection.png`.

## cleanup

The disposable server runtime, world, logs, client screenshots, command agent, and
temporary audio state were registered for teardown. Only this sanitized record, the
targeted rejection screenshot, and the requested final beta 3 jar and checksums are
retained. Unrelated Minecraft processes, Prism instances, saves, and shared caches
remain untouched.

# Phase 010 server matrix

Date: 2026-09-24

## Candidate

The candidate was rebuilt from the phase 010 branch after pinning the Forge 1.20.1 GeckoLib dependency to the production 4.8.4 artifact.

| Component | Identity |
| --- | --- |
| Minecraft | 1.20.1 |
| Forge | 47.4.20 |
| Java | Eclipse Temurin 17.0.19 |
| FutureShops | `futureshops-3.0.0-beta.2.jar` |
| FutureShops SHA 256 | `dbd975ee51b3701eeb5c1b570e8156099619e87bea2b0dd22e6a495a13fcdcbd` |
| FutureShops SHA 512 | `0566a2c825dded12256403cc83546a0446ea8d39acd62266d7cc83734883e1d4d4a9b86e20a4ec5cbd7c75e742ae585c26ee143f1e0703ea20f8067510cb0344` |
| GeckoLib | `geckolib-forge-1.20.1-4.8.4.jar` |
| GeckoLib SHA 256 | `c04abba4fd354dd8c0e8a732f1f05f4dca893d2e1d962b2ab128bbd411478747` |

## Production dedicated server

An official Forge installer for 1.20.1 and 47.4.20 created the disposable server runtime. The runtime had `eula=true`, `online-mode=true`, and a private test port. The launch path was the generated Forge `run.sh` with the Java 17 runtime explicitly first in `PATH`. The server log reported Java 17.0.19, Forge 47.4.20, FutureShops common setup, and a completed dedicated server startup.

The server loaded the default FutureShops shop and its Bazaar catalog. The authenticated `EnVyOnMyMind` profile joined the world from the isolated laptop client. The server accepted the client only after both sides used the same FutureShops and GeckoLib artifacts listed above.

The earlier Gradle userdev server was not used as production proof. It produced a registry mismatch with the production GeckoLib client, which confirmed that userdev and production server environments must not be mixed. The production Forge server removed that mismatch.

## Limits

This record proves the exact production Forge startup and paired multiplayer join. It does not claim Pixelmon native writer or external bridge compatibility, which remain owned by their upstream phase contracts.

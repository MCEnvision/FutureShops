# Phase 010 client matrix

Date: 2026-09-24

## Laptop runtime

The disposable Prism Launcher instance was created on the EnVisione laptop and contained only the matching Forge 1.20.1 loader, FutureShops candidate, and GeckoLib 4.8.4. The authenticated `EnVyOnMyMind` profile was used. No personal saves or existing instances were copied.

The client launched in Hyprland workspace 5. `hyprctl clients -j` identified the owned window as class `Minecraft* 1.20.1`, title `Minecraft* Forge 1.20.1`, and process 3200918 for the final candidate run. The render log reported the NVIDIA GeForce RTX 5090 Laptop GPU and OpenGL 4.6.

Before acceptance, the instance master audio option was zero. The matching PipeWire stream was correlated to process 3200918 and reported `Volume: 0.00 [MUTED]`. No default sink, microphone, unrelated process, or personal instance was changed.

## Multiplayer receipt

The client connected to the private dedicated Forge server at the verified test endpoint. The server recorded the authenticated profile UUID and `EnVyOnMyMind joined the game`. A rendered screenshot captured the player in the joined world after the final candidate run. The client and server logs contained no GeckoLib registry mismatch for the matching 4.8.4 pair.

The first development server attempt is retained only as a diagnostic observation. It used a userdev classpath and failed because the client and server GeckoLib environments did not expose the same registry data. That failure is not production compatibility evidence.

## Interaction boundary

The client reached the real multiplayer world and rendered it on the discrete GPU. Wayland text injection was available, but this run did not use it as a substitute for a measured FutureShops screen assertion. No screen claim is made beyond the joined world and renderer evidence.

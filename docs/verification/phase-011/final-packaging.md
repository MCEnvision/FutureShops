# final beta 3 packaging and runtime receipt

Date: 2026-09-25

## source and merge

The final phase pull request was merged into `1.20.1/3.0.0-beta.2` with merge commit
`e6964b8e202194fd56cc89ec4f4763519969633a`. The merged commit contains the reviewed phase
head `d83f7ea51eea73ea5495dbf92da855f0d8c18634`. The final build used the merged commit and
the Forge 1.20.1, Forge 47.4.20, Java 17, and production GeckoLib 4.8.4 pins.

## clean builds

Both builds ran from clean worktrees at the resulting merge commit with the Temurin Java 17
runtime. The first command was:

```text
./gradlew --no-daemon clean test jar verifyPackagedDependencyBoundary --console=plain
```

The first production archive had SHA 256
`b57ace2a89b01f3bd41b7bfe1ee2463ddfa223124826b523dbf892c8d5ee75bc` and SHA 512
`f829eabcac566c6983db529e8981af2766f5939db8a2909d1a99999be8a2ac105654b0682102f2c3f865a8f5c4d202ab42a8b68c2be7fdf05deb572d44e6c6e6`.

The second command was:

```text
./gradlew --no-daemon clean jar verifyPackagedDependencyBoundary --console=plain
```

The second production archive had SHA 256
`6e44e5c60bb0233fcae67cee57d5f685630069a5ae0bc02817c61c53d2b2298c` and SHA 512
`50a53e8ce8d97b85f2b7b4f6aa122bbc37b6e738d21ce15bc0f998798cc537d0e94319bd799d669f20a820477d1d74f8c5b5d828c04eecb6d6238a2933f48a29`.

The archives differ only in the generated manifest timestamp. Removing
`META-INF/MANIFEST.MF` from both normalized trees produced identical semantic payloads.
The final retained JAR is `futureshops-3.0.0-beta.3.jar` with the second build hashes above.
Archive integrity and `verifyPackagedDependencyBoundary` both passed.

## metadata and isolation

The archive reports version `3.0.0-beta.3`, mod id `futureshops`, Forge loader range `[47,)`,
Minecraft range `[1.20.1,1.21)`, GeckoLib range `[4.4,)`, and logo `futureshops.png`.
The archive scan found no bundled Netty, Log4j, JLine, Apache Commons, Guava, Plexus, Bukkit,
Spigot, Paper, Vault, SQLite, Pixelmon, DanConomy, or other prohibited provider classes.

## dedicated server and laptop client

The exact retained JAR and GeckoLib 4.8.4 were installed in a disposable Forge 47.4.20
dedicated server runtime. The server started on Java 17, generated the default FutureShops shop,
loaded the server catalog, and accepted the authenticated owner profile. The matching laptop
client used the same Minecraft, Forge, GeckoLib, and FutureShops versions, rendered with the
NVIDIA RTX 5090 Laptop GPU, loaded FutureShops client setup, connected to the private test
server, and received the modded server handshake. The server log recorded the authenticated
profile joining the intended world.

The exact retained client and server pair executed the scripted stale buy scenario from core
requirement 015 after the post merge evidence rerun. The client opened a 5.00 coin diamond
confirmation, the server price changed to 5.01 while that confirmation remained open, and the
server and client catalogs were reloaded. Confirming the stale 5.00 snapshot was refused with
`The offer changed. Review the refreshed details.`. No additional wallet debit or diamond
delivery occurred. The targeted visual receipt is `final-stale-buy-rejection.png` and the full
runtime record is `final-runtime-acceptance-2026-09-25.md`.

The client window was identified through `hyprctl clients -j` by its exact Minecraft class,
title, workspace, and process id. Its PipeWire stream was correlated to that process and read
back as `Volume: 0.00 [MUTED]` before the client assertion. No unrelated sink, stream, instance,
or process was muted or changed.

## issue disposition and publication boundary

Issue 66 remains open because Forge Pixelmon support is proven while DanConomy is a NeoForge only
donor integration and is intentionally refused by this Forge beta. Issue 9 remains open because
the documented platform and build tooling advisories require compatible upstream updates rather
than split packages inside the mod archive. No release was uploaded to CurseForge, Modrinth, or
GitHub, and no Discord publication was made.

## cleanup receipt

The disposable merged build worktree, dedicated server runtime, generated world, client instance,
client logs, screenshots, old candidate copy, audio stream, and server port were removed after
their final evidence consumers completed. The client process and its playback stream were absent
after shutdown. The server process was absent and its test port was free. The preexisting Prism
Launcher process, personal instances, repository worktrees, historical branches, tags, and
shared caches were preserved.

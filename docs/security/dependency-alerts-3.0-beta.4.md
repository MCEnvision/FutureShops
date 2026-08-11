# Dependency alert disposition for 3.0.0 beta 4

This record classifies the 25 Dependabot alerts that were open on July 31, 2026. The alerts come
from the Gradle dependency snapshot submitted for the pinned Minecraft 1.20.1 and Forge 47.4.20
toolchain. They are not libraries declared and packaged by FutureShops.

## Resolved dependency graph

The game runtime classpath resolves the following affected components.

| Component | Resolved version | Owner |
| --- | ---: | --- |
| Netty codec, handler, common, and native epoll | 4.1.82.Final | Minecraft and Forge runtime |
| Commons IO | 2.11.0 | Minecraft and Forge runtime |
| Commons Compress | 1.21 | Minecraft and Forge runtime |
| Commons Lang | 3.12.0 | Minecraft and Forge runtime |
| Guava | 31.1 jre | Minecraft and Forge runtime |
| Log4j Core | 2.19.0 | Minecraft and Forge runtime |
| Plexus Utils | 3.3.0 | Forge runtime tooling |

The build plugin graph also resolves Commons IO 2.11.0, Commons Compress 1.18, Commons Lang 3.9,
Guava 31.1 jre, and Plexus Utils 3.5.1 through ForgeGradle 6.0.54 and MixinGradle 0.7 snapshot.

FutureShops directly uses Netty buffers and `DecoderException` for bounded packet codecs. It does
not directly import Commons IO, Commons Compress, Commons Lang, Guava, Log4j Core, or Plexus Utils.
The absence of a direct import does not remove platform exposure. Minecraft and Forge can still
reach their own runtime libraries.

## Alert classification

Runtime platform means the vulnerable version is supplied on the game or Forge runtime classpath.
Build time means only the ForgeGradle plugin graph is inside that advisory range. None of these
alerts is a false positive in the submitted graph.

| Alert | Advisory | Component | Classification | First patched version | Disposition |
| ---: | --- | --- | --- | --- | --- |
| 25 | GHSA-558v-64gr-wgg4 | Netty Codec | Runtime platform | 4.1.136.Final | Requires a compatible Minecraft or Forge runtime upgrade. FutureShops does not use Bzip2Decoder. |
| 24 | GHSA-c653-97m9-rcg9 | Netty Handler | Runtime platform | 4.1.135.Final | Requires a compatible Minecraft or Forge runtime upgrade. FutureShops does not configure a plain TLS trust manager. |
| 23 | GHSA-w573-9ffj-6ff9 | Netty Native Epoll | Runtime platform | 4.1.135.Final | Requires a compatible Minecraft or Forge runtime upgrade. FutureShops does not receive Unix socket file descriptors. |
| 22 | GHSA-x4gw-5cx5-pgmh | Netty Handler | Runtime platform | 4.1.135.Final | Requires a compatible Minecraft or Forge runtime upgrade. FutureShops does not install an SNI handler. |
| 21 | GHSA-3qp7-7mw8-wx86 | Netty Handler | Runtime platform | 4.1.135.Final | Requires a compatible Minecraft or Forge runtime upgrade. FutureShops does not configure Netty subnet filters. |
| 20 | GHSA-mj4r-2hfc-f8p6 | Netty Codec | Runtime platform | 4.1.133.Final | Requires a compatible Minecraft or Forge runtime upgrade. FutureShops does not install LZ4 frame decoding. |
| 19 | GHSA-6hg6-v5c8-fphq | Log4j Core | Runtime platform | 2.25.4 | Requires a compatible Minecraft or Forge runtime upgrade. Exposure depends on operator TLS appender configuration. |
| 18 | GHSA-3pxv-7cmr-fjr4 | Log4j Core | Runtime platform | 2.25.4 | Requires a compatible Minecraft or Forge runtime upgrade. FutureShops does not select XmlLayout. |
| 17 | GHSA-6fmv-xxpf-w3cw | Plexus Utils | Runtime tooling and build time | 3.6.1 | Requires compatible Forge runtime tooling and ForgeGradle upgrades. FutureShops does not call archive extraction APIs. |
| 16 | GHSA-vc5p-v9hr-52mj | Log4j Core | Runtime platform | 2.25.3 | Superseded by the 2.25.4 platform boundary required for alerts 18 and 19. |
| 15 | GHSA-3p8m-j85q-pgmj | Netty Codec | Runtime platform | 4.1.125.Final | Requires a compatible Minecraft or Forge runtime upgrade. FutureShops does not install the affected compression decoders. |
| 14 | GHSA-j288-q9x7-2f5v | Commons Lang | Runtime platform and build time | 3.18.0 | Requires compatible Minecraft, Forge, and ForgeGradle upgrades. FutureShops does not call the affected class name parser. |
| 13 | GHSA-389x-839f-4rhx | Netty Common | Runtime platform | 4.1.118.Final | Requires a compatible Minecraft or Forge runtime upgrade. The reported Windows environment variable path is platform owned. |
| 12 | GHSA-xq3w-v528-46rv | Netty Common | Runtime platform | 4.1.115.Final | Superseded by the 4.1.118.Final or newer platform boundary required for alert 13. |
| 11 | GHSA-78wr-2p64-hpwj | Commons IO | Runtime platform and build time | 2.14.0 | Requires compatible Minecraft, Forge, and ForgeGradle upgrades. FutureShops does not use XmlStreamReader. |
| 10 | GHSA-4g9r-vxhx-9pgx | Commons Compress | Runtime platform and build time | 1.26.0 | Requires compatible Minecraft, Forge, and ForgeGradle upgrades. FutureShops does not parse DUMP archives. |
| 9 | GHSA-4265-ccf5-phj5 | Commons Compress | Runtime platform and build time | 1.26.0 | Requires compatible Minecraft, Forge, and ForgeGradle upgrades. FutureShops does not parse Pack200 archives. |
| 8 | GHSA-5mg8-w23w-74h3 | Guava | Runtime platform and build time | 32.0.0 Android | Use the corresponding supported 32.0.0 jre or newer line through compatible platform and toolchain upgrades. FutureShops does not use the affected file creation helper. |
| 7 | GHSA-7g45-4rm6-3mm3 | Guava | Runtime platform and build time | 32.0.0 Android | Same platform disposition as alert 8. FutureShops does not create Guava temporary directories. |
| 6 | GHSA-6mjq-h674-j845 | Netty Handler | Runtime platform | 4.1.94.Final | Superseded by the 4.1.135.Final or newer platform boundary required for current Netty handler alerts. |
| 5 | GHSA-mc84-pj99-q6hh | Commons Compress | Build time | 1.21 | The game runtime already resolves 1.21. ForgeGradle still resolves 1.18 and requires a compatible plugin upgrade. |
| 4 | GHSA-xqfj-vm6h-2x34 | Commons Compress | Build time | 1.21 | The game runtime already resolves 1.21. ForgeGradle still resolves 1.18 and requires a compatible plugin upgrade. |
| 3 | GHSA-crv7-7245-f45f | Commons Compress | Build time | 1.21 | The game runtime already resolves 1.21. ForgeGradle still resolves 1.18 and requires a compatible plugin upgrade. |
| 2 | GHSA-7hfm-57qf-j43q | Commons Compress | Build time | 1.21 | The game runtime already resolves 1.21. ForgeGradle still resolves 1.18 and requires a compatible plugin upgrade. |
| 1 | GHSA-53x6-4x5p-rrvv | Commons Compress | Build time | 1.19 | The game runtime resolves 1.21. ForgeGradle 6.0.54 resolves the affected 1.18 build dependency. |

## Constraint decision

No direct Gradle constraint is added in this beta. FutureShops is a normal Forge mod JAR and does
not bundle or select Minecraft launcher libraries. A constraint would only alter the development
classpath, could violate the supported Forge library set, and would leave player installations on
the launcher supplied versions. That would create a false security claim without fixing the
shipped runtime.

The safe remediation boundary is a tested Minecraft and Forge platform upgrade that supplies
compatible patched Netty, Log4j, Commons, Guava, and Plexus versions. ForgeGradle must also move to
a compatible version that removes its affected build dependencies. Those upgrades are outside the
3.0 bug fix release boundary and require their own migration plan and full compatibility matrix.

## Repository monitoring

The beta 4 branch adds Dependabot coverage for both Gradle and GitHub Actions. Minor and patch
updates are grouped. Major updates remain visible for individual review instead of being silently
ignored. This coverage becomes active on the default branch only after the branch is approved and
merged.

The release JAR must be inspected after every build. Acceptance requires no `io/netty`,
`org/apache/commons`, `com/google/common`, `org/apache/logging/log4j`, or
`org/codehaus/plexus` class files inside the FutureShops artifact.

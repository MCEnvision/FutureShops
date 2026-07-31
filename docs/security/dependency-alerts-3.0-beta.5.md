# Dependency alert disposition for 3.0.0 beta 5

This record resolves the audit requirements for the 25 Dependabot alerts reported from the Gradle
dependency snapshot on July 31, 2026. The snapshot describes libraries supplied by the pinned
Minecraft 1.20.1, Forge 47.4.20, ForgeGradle 6.0.54, and MixinGradle 0.7 snapshot platform. None of
the affected libraries is a direct FutureShops dependency or a library packaged in the FutureShops
JAR.

An accepted risk disposition means the alert is understood and tracked at its real remediation
boundary. It does not mean the upstream component is patched. The affected versions remain pinned
until a compatible Minecraft, Forge, or ForgeGradle upgrade is planned and passes the complete
compatibility matrix.

## Resolved dependency graph

The game runtime classpath resolves these affected components.

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

FutureShops directly uses bounded Netty buffers and `DecoderException` in packet codecs. It does
not install the affected compression, TLS, SNI, subnet, native socket, or archive handlers. It does
not directly import Commons IO, Commons Compress, Commons Lang, Guava, Log4j Core, or Plexus Utils.
Minecraft, Forge, server operator logging configuration, and build plugins can still reach their
own platform libraries, so these graph findings are not false positives.

## Alert classification and disposition

Runtime platform means the vulnerable version is supplied on the game or Forge runtime classpath.
Build time means the affected version is present in the ForgeGradle or MixinGradle plugin graph.
FutureShops reachability states whether this mod selects the advisory specific code path. Every
alert uses the GitHub disposition Tolerable risk because the submitted dependency is real but the
safe remediation boundary is outside the 3.0 bug fix release.

| Alert | Advisory | Component | Classification | FutureShops reachability | First patched version | GitHub disposition | Required remediation |
| ---: | --- | --- | --- | --- | --- | --- | --- |
| 25 | GHSA-558v-64gr-wgg4 | Netty Codec | Runtime platform | Unreachable. FutureShops does not install Bzip2Decoder. | 4.1.136.Final | Tolerable risk | Compatible Minecraft or Forge runtime upgrade. |
| 24 | GHSA-c653-97m9-rcg9 | Netty Handler | Runtime platform | Unreachable. FutureShops does not configure a plain TLS trust manager. | 4.1.135.Final | Tolerable risk | Compatible Minecraft or Forge runtime upgrade. |
| 23 | GHSA-w573-9ffj-6ff9 | Netty Native Epoll | Runtime platform | Unreachable. FutureShops does not receive Unix socket file descriptors. | 4.1.135.Final | Tolerable risk | Compatible Minecraft or Forge runtime upgrade. |
| 22 | GHSA-x4gw-5cx5-pgmh | Netty Handler | Runtime platform | Unreachable. FutureShops does not install an SNI handler. | 4.1.135.Final | Tolerable risk | Compatible Minecraft or Forge runtime upgrade. |
| 21 | GHSA-3qp7-7mw8-wx86 | Netty Handler | Runtime platform | Unreachable. FutureShops does not configure Netty subnet filters. | 4.1.135.Final | Tolerable risk | Compatible Minecraft or Forge runtime upgrade. |
| 20 | GHSA-mj4r-2hfc-f8p6 | Netty Codec | Runtime platform | Unreachable. FutureShops does not install LZ4 frame decoding. | 4.1.133.Final | Tolerable risk | Compatible Minecraft or Forge runtime upgrade. |
| 19 | GHSA-6hg6-v5c8-fphq | Log4j Core | Runtime platform | Unreachable from FutureShops. Exposure depends on operator TLS appender configuration. | 2.25.4 | Tolerable risk | Compatible Minecraft or Forge runtime upgrade and operator logging review. |
| 18 | GHSA-3pxv-7cmr-fjr4 | Log4j Core | Runtime platform | Unreachable. FutureShops does not select XmlLayout. | 2.25.4 | Tolerable risk | Compatible Minecraft or Forge runtime upgrade. |
| 17 | GHSA-6fmv-xxpf-w3cw | Plexus Utils | Runtime tooling and build time | Unreachable. FutureShops does not call archive extraction APIs. | 3.6.1 | Tolerable risk | Compatible Forge runtime tooling and ForgeGradle upgrades. |
| 16 | GHSA-vc5p-v9hr-52mj | Log4j Core | Runtime platform | Unreachable from FutureShops. The later 2.25.4 boundary also resolves this advisory. | 2.25.3 | Tolerable risk | Compatible Minecraft or Forge runtime upgrade to at least 2.25.4. |
| 15 | GHSA-3p8m-j85q-pgmj | Netty Codec | Runtime platform | Unreachable. FutureShops does not install the affected compression decoders. | 4.1.125.Final | Tolerable risk | Compatible Minecraft or Forge runtime upgrade. |
| 14 | GHSA-j288-q9x7-2f5v | Commons Lang | Runtime platform and build time | Unreachable. FutureShops does not call the affected class name parser. | 3.18.0 | Tolerable risk | Compatible Minecraft, Forge, and ForgeGradle upgrades. |
| 13 | GHSA-389x-839f-4rhx | Netty Common | Runtime platform | Unreachable from FutureShops. The affected Windows environment variable path is platform owned. | 4.1.118.Final | Tolerable risk | Compatible Minecraft or Forge runtime upgrade. |
| 12 | GHSA-xq3w-v528-46rv | Netty Common | Runtime platform | Unreachable from FutureShops. The 4.1.118.Final boundary for alert 13 supersedes this patch level. | 4.1.115.Final | Tolerable risk | Compatible Minecraft or Forge runtime upgrade to at least 4.1.118.Final. |
| 11 | GHSA-78wr-2p64-hpwj | Commons IO | Runtime platform and build time | Unreachable. FutureShops does not use XmlStreamReader. | 2.14.0 | Tolerable risk | Compatible Minecraft, Forge, and ForgeGradle upgrades. |
| 10 | GHSA-4g9r-vxhx-9pgx | Commons Compress | Runtime platform and build time | Unreachable. FutureShops does not parse DUMP archives. | 1.26.0 | Tolerable risk | Compatible Minecraft, Forge, and ForgeGradle upgrades. |
| 9 | GHSA-4265-ccf5-phj5 | Commons Compress | Runtime platform and build time | Unreachable. FutureShops does not parse Pack200 archives. | 1.26.0 | Tolerable risk | Compatible Minecraft, Forge, and ForgeGradle upgrades. |
| 8 | GHSA-5mg8-w23w-74h3 | Guava | Runtime platform and build time | Unreachable. FutureShops does not use the affected file creation helper. | 32.0.0 Android | Tolerable risk | Compatible platform and toolchain upgrade to the supported 32.0.0 jre or newer line. |
| 7 | GHSA-7g45-4rm6-3mm3 | Guava | Runtime platform and build time | Unreachable. FutureShops does not create Guava temporary directories. | 32.0.0 Android | Tolerable risk | Compatible platform and toolchain upgrade to the supported 32.0.0 jre or newer line. |
| 6 | GHSA-6mjq-h674-j845 | Netty Handler | Runtime platform | Unreachable from FutureShops. The 4.1.135.Final boundary for current handler alerts supersedes this patch level. | 4.1.94.Final | Tolerable risk | Compatible Minecraft or Forge runtime upgrade to at least 4.1.135.Final. |
| 5 | GHSA-mc84-pj99-q6hh | Commons Compress | Build time | Unreachable. The game runtime resolves 1.21, while ForgeGradle archive processing owns the affected 1.18 path. | 1.21 | Tolerable risk | Compatible ForgeGradle upgrade. |
| 4 | GHSA-xqfj-vm6h-2x34 | Commons Compress | Build time | Unreachable. The game runtime resolves 1.21, while ForgeGradle archive processing owns the affected 1.18 path. | 1.21 | Tolerable risk | Compatible ForgeGradle upgrade. |
| 3 | GHSA-crv7-7245-f45f | Commons Compress | Build time | Unreachable. The game runtime resolves 1.21, while ForgeGradle archive processing owns the affected 1.18 path. | 1.21 | Tolerable risk | Compatible ForgeGradle upgrade. |
| 2 | GHSA-7hfm-57qf-j43q | Commons Compress | Build time | Unreachable. The game runtime resolves 1.21, while ForgeGradle archive processing owns the affected 1.18 path. | 1.21 | Tolerable risk | Compatible ForgeGradle upgrade. |
| 1 | GHSA-53x6-4x5p-rrvv | Commons Compress | Build time | Unreachable. ForgeGradle archive processing owns the affected 1.18 path. | 1.19 | Tolerable risk | Compatible ForgeGradle upgrade. |

## Constraint and packaging decision

No direct Gradle constraint is added. A constraint in the mod project would only alter the
development classpath and would not replace libraries selected by a player launcher. Packaging
patched copies would create duplicate or split packages beside bootstrap libraries and could make
class selection environment dependent. Neither approach is an honest or safe player remediation.

The build runs `verifyPackagedDependencyBoundary` as part of `check`. It fails if the FutureShops
JAR contains class files under `io/netty`, `org/apache/commons`, `com/google/common`,
`org/apache/logging/log4j`, or `org/codehaus/plexus`, or if Forge Jar in Jar metadata is present.

## Monitoring and future remediation

Dependabot checks Gradle and GitHub Actions weekly. Minor and patch updates are grouped, while
major updates remain visible for compatibility planning. The configuration is effective only when
`.github/dependabot.yml` is present on the default branch.

Reopen this record when a compatible platform update changes any resolved version. Remove an
accepted risk only after the submitted graph reports the patched version and the unit, GameTest,
build, dedicated server, client, multiplayer, and packaged JAR gates pass.

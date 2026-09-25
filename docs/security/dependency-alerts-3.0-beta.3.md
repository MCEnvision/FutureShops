# dependency alert disposition for 3.0.0 beta 3

Date: 2026-09-24

The Forge 1.20.1 and Forge 47.4.20 dependency graph currently has four open Dependabot alerts
and twenty five historical alerts already dismissed with documented platform ownership. None of
the affected libraries is packaged inside the FutureShops jar.

| alert | advisory | component and classification | FutureShops reachability and disposition |
| ---: | --- | --- | --- |
| 26 | GHSA-qv9r-c865-cp47 | Log4j API, runtime platform | platform owned, no `MapMessage` JSON layout or attacker controlled non finite value path in FutureShops |
| 27 | GHSA-fccg-mwvh-qqg4 | Netty handler, runtime platform | platform owned, FutureShops does not install `SniHandler` or TLS termination |
| 28 | GHSA-c4c3-7fpv-j4q5 | Netty handler, runtime platform | platform owned, FutureShops does not configure SNI mTLS selection or fallback contexts |
| 29 | GHSA-5q95-hrpc-m3w3 | JLine reader, runtime tooling | Forge tooling owned, the mod does not configure JLine history or `HISTORY_IGNORE` |

The following historical alerts remain individually classified from the earlier graph. They are
platform owned or Forge tooling owned and are retained here so the current issue record has one
complete advisory inventory.

| alert | advisory | package and resolved version | patched version | FutureShops reachability and disposition |
| ---: | --- | --- | --- | --- |
| 25 | GHSA-558v-64gr-wgg4 | `io.netty:netty-codec` 4.1.82.Final | 4.1.136.Final | platform owned, no Bzip2 decoder route, requires a compatible Minecraft or Forge upgrade |
| 24 | GHSA-c653-97m9-rcg9 | `io.netty:netty-handler` 4.1.82.Final | 4.1.135.Final | platform owned, no plain TLS trust manager route, requires a compatible Minecraft or Forge upgrade |
| 23 | GHSA-w573-9ffj-6ff9 | `io.netty:netty-transport-native-epoll` 4.1.82.Final | 4.1.135.Final | platform owned, no Unix descriptor route, requires a compatible Minecraft or Forge upgrade |
| 22 | GHSA-x4gw-5cx5-pgmh | `io.netty:netty-handler` 4.1.82.Final | 4.1.135.Final | platform owned, no SNI handler route, requires a compatible Minecraft or Forge upgrade |
| 21 | GHSA-3qp7-7mw8-wx86 | `io.netty:netty-handler` 4.1.82.Final | 4.1.135.Final | platform owned, no subnet filter route, requires a compatible Minecraft or Forge upgrade |
| 20 | GHSA-mj4r-2hfc-f8p6 | `io.netty:netty-codec` 4.1.82.Final | 4.1.133.Final | platform owned, no LZ4 decoder route, requires a compatible Minecraft or Forge upgrade |
| 19 | GHSA-6hg6-v5c8-fphq | `org.apache.logging.log4j:log4j-core` 2.19.0 | 2.25.4 | platform owned, no TLS appender route, requires a compatible Minecraft or Forge upgrade |
| 18 | GHSA-3pxv-7cmr-fjr4 | `org.apache.logging.log4j:log4j-core` 2.19.0 | 2.25.4 | platform owned, no XML layout route, requires a compatible Minecraft or Forge upgrade |
| 17 | GHSA-6fmv-xxpf-w3cw | `org.codehaus.plexus:plexus-utils` 3.3.0 | 3.6.1 | Forge tooling owned, no archive extraction route, requires a compatible ForgeGradle upgrade |
| 16 | GHSA-vc5p-v9hr-52mj | `org.apache.logging.log4j:log4j-core` 2.19.0 | 2.25.3 | platform owned, no TLS socket appender route, requires a compatible Minecraft or Forge upgrade |
| 15 | GHSA-3p8m-j85q-pgmj | `io.netty:netty-codec` 4.1.82.Final | 4.1.125.Final | platform owned, no affected decoder route, requires a compatible Minecraft or Forge upgrade |
| 14 | GHSA-j288-q9x7-2f5v | `org.apache.commons:commons-lang3` 3.12.0 | 3.18.0 | platform owned and Forge tooling owned, no affected parser route, requires compatible upgrades |
| 13 | GHSA-389x-839f-4rhx | `io.netty:netty-common` 4.1.82.Final | 4.1.118.Final | platform owned, no affected Windows route, requires a compatible Minecraft or Forge upgrade |
| 12 | GHSA-xq3w-v528-46rv | `io.netty:netty-common` 4.1.82.Final | 4.1.115.Final | platform owned, no affected Windows route, requires a compatible Minecraft or Forge upgrade |
| 11 | GHSA-78wr-2p64-hpwj | `commons-io:commons-io` 2.11.0 | 2.14.0 | platform owned and Forge tooling owned, no XmlStreamReader route, requires compatible upgrades |
| 10 | GHSA-4g9r-vxhx-9pgx | `org.apache.commons:commons-compress` 1.21 | 1.26.0 | platform owned and Forge tooling owned, no DUMP parser route, requires compatible upgrades |
| 9 | GHSA-4265-ccf5-phj5 | `org.apache.commons:commons-compress` 1.21 | 1.26.0 | platform owned and Forge tooling owned, no Pack200 parser route, requires compatible upgrades |
| 8 | GHSA-5mg8-w23w-74h3 | `com.google.guava:guava` 31.1 jre | 32.0.0 jre | platform owned and Forge tooling owned, no affected helper route, requires compatible upgrades |
| 7 | GHSA-7g45-4rm6-3mm3 | `com.google.guava:guava` 31.1 jre | 32.0.0 jre | platform owned and Forge tooling owned, no temporary directory route, requires compatible upgrades |
| 6 | GHSA-6mjq-h674-j845 | `io.netty:netty-handler` 4.1.82.Final | 4.1.94.Final | platform owned, no SNI route, requires a compatible Minecraft or Forge upgrade |
| 5 | GHSA-mc84-pj99-q6hh | `org.apache.commons:commons-compress` 1.18 build time | 1.21 | Forge tooling owned, no affected parser route, requires a compatible ForgeGradle upgrade |
| 4 | GHSA-xqfj-vm6h-2x34 | `org.apache.commons:commons-compress` 1.18 build time | 1.21 | Forge tooling owned, no affected parser route, requires a compatible ForgeGradle upgrade |
| 3 | GHSA-crv7-7245-f45f | `org.apache.commons:commons-compress` 1.18 build time | 1.21 | Forge tooling owned, no affected parser route, requires a compatible ForgeGradle upgrade |
| 2 | GHSA-7hfm-57qf-j43q | `org.apache.commons:commons-compress` 1.18 build time | 1.21 | Forge tooling owned, no affected parser route, requires a compatible ForgeGradle upgrade |
| 1 | GHSA-53x6-4x5p-rrvv | `org.apache.commons:commons-compress` 1.18 build time | 1.19 | Forge tooling owned, no affected parser route, requires a compatible ForgeGradle upgrade |

The patched versions reported by GitHub are Log4j API 2.25.5 or 2.26.1, Netty 4.1.137.Final or
4.2.17.Final, and JLine 3.30.15 or 4.3.1. Direct constraints are not applied because Minecraft
and Forge supply these libraries to the launcher. Bundling replacement classes would create split
packages and would not repair existing player installations. The package boundary scan must still
confirm that no Netty, Log4j, JLine, Apache Commons, Guava, or Plexus classes enter the
FutureShops jar.

Dependabot covers the Gradle and GitHub Actions ecosystems on the default branch. Issue 9 remains
open with this current disposition. It is not described as fixed until the upstream platform
boundary changes and the full compatibility matrix passes.

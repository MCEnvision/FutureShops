# Dependency alert disposition for 3.0.0 beta 3

This record is the current Forge 1.20.1 dependency audit observed on September 24, 2026.
The runtime graph resolves platform libraries from Minecraft 1.20.1 and Forge 47.4.20. FutureShops
does not package these libraries in its jar, so a Gradle constraint or copied library would not
change the library selected by a player launcher and could create split packages.

## Resolved graph

| Family | Runtime version | Owner | Evidence |
| --- | --- | --- | --- |
| Netty codec, common, handler, transport, and native epoll | 4.1.82.Final | Minecraft and Forge | `dependencyInsight --dependency netty-handler --configuration runtimeClasspath` and the complete runtime graph |
| JLine reader and terminal | 3.12.1 | Forge terminal runtime | `dependencyInsight --dependency jline-reader --configuration runtimeClasspath` |
| Log4j API, core, and SLF4J bridge | 2.19.0 | Minecraft and Forge | complete runtime graph |
| Commons IO | 2.11.0 | Minecraft and Forge | complete runtime graph |
| Commons Compress | 1.21 | Minecraft and Forge | complete runtime graph |
| Commons Lang | 3.12.0 | Minecraft and Forge | complete runtime graph |
| Guava | 31.1 jre | Minecraft and Forge | complete runtime graph |
| Plexus Utils | 3.3.0 | Forge tooling | complete runtime graph |

The source and archive scans found no FutureShops use of Netty TLS or SNI handlers, compression
decoders, Unix socket descriptor passing, Log4j TLS or XML layouts, JLine `HISTORY_IGNORE`, or
Plexus archive extraction. FutureShops uses Netty only for bounded packet buffers and decoder
errors. This is the FutureShops reachability result, not a claim that the platform is patched.

## Current open alerts

All current alerts are inherited from the Forge or Minecraft graph. The first patched version is
recorded so a compatible platform upgrade can be selected later.

| Alert | Advisory | Package | Resolved | First patched | Severity | Disposition |
| ---: | --- | --- | --- | --- | --- | --- |
| 29 | GHSA-5q95-hrpc-m3w3 | org.jline:jline-reader | 3.12.1 | 3.30.15 | Medium | platform owned, not reachable through FutureShops |
| 28 | GHSA-c4c3-7fpv-j4q5 | io.netty:netty-handler | 4.1.82.Final | 4.1.137.Final | Critical | platform owned, no FutureShops TLS or SNI route |
| 27 | GHSA-fccg-mwvh-qqg4 | io.netty:netty-handler | 4.1.82.Final | 4.1.137.Final | Medium | platform owned, no FutureShops TLS or SNI route |
| 26 | GHSA-qv9r-c865-cp47 | org.apache.logging.log4j:log4j-api | 2.19.0 | 2.25.5 | Medium | platform owned, no FutureShops MapMessage route |
| 25 | GHSA-558v-64gr-wgg4 | io.netty:netty-codec | 4.1.82.Final | 4.1.136.Final | High | platform owned, no Bzip2 decoder route |
| 24 | GHSA-c653-97m9-rcg9 | io.netty:netty-handler | 4.1.82.Final | 4.1.135.Final | High | platform owned, no plain trust manager route |
| 23 | GHSA-w573-9ffj-6ff9 | io.netty:netty-transport-native-epoll | 4.1.82.Final | 4.1.135.Final | Medium | platform owned, no Unix descriptor route |
| 22 | GHSA-x4gw-5cx5-pgmh | io.netty:netty-handler | 4.1.82.Final | 4.1.135.Final | High | platform owned, no SNI handler route |
| 21 | GHSA-3qp7-7mw8-wx86 | io.netty:netty-handler | 4.1.82.Final | 4.1.135.Final | High | platform owned, no subnet filter route |
| 20 | GHSA-mj4r-2hfc-f8p6 | io.netty:netty-codec | 4.1.82.Final | 4.1.133.Final | High | platform owned, no LZ4 decoder route |
| 19 | GHSA-6hg6-v5c8-fphq | org.apache.logging.log4j:log4j-core | 2.19.0 | 2.25.4 | Medium | platform owned, no TLS appender route |
| 18 | GHSA-3pxv-7cmr-fjr4 | org.apache.logging.log4j:log4j-core | 2.19.0 | 2.25.4 | Medium | platform owned, no XML layout route |
| 17 | GHSA-6fmv-xxpf-w3cw | org.codehaus.plexus:plexus-utils | 3.3.0 | 3.6.1 | High | Forge tooling owned, no extraction route |
| 16 | GHSA-vc5p-v9hr-52mj | org.apache.logging.log4j:log4j-core | 2.19.0 | 2.25.3 | Medium | platform owned, no TLS socket appender route |
| 15 | GHSA-3p8m-j85q-pgmj | io.netty:netty-codec | 4.1.82.Final | 4.1.125.Final | Medium | platform owned, no affected decoder route |
| 14 | GHSA-j288-q9x7-2f5v | org.apache.commons:commons-lang3 | 3.12.0 | 3.18.0 | Medium | platform owned, no affected parser route |
| 13 | GHSA-389x-839f-4rhx | io.netty:netty-common | 4.1.82.Final | 4.1.118.Final | Medium | platform owned, no affected Windows route |
| 12 | GHSA-xq3w-v528-46rv | io.netty:netty-common | 4.1.82.Final | 4.1.115.Final | Medium | platform owned, no affected Windows route |
| 11 | GHSA-78wr-2p64-hpwj | commons-io:commons-io | 2.11.0 | 2.14.0 | High | platform owned, no XmlStreamReader route |
| 10 | GHSA-4g9r-vxhx-9pgx | org.apache.commons:commons-compress | 1.21 | 1.26.0 | Medium | platform owned, no DUMP parser route |
| 9 | GHSA-4265-ccf5-phj5 | org.apache.commons:commons-compress | 1.21 | 1.26.0 | Medium | platform owned, no Pack200 parser route |
| 8 | GHSA-5mg8-w23w-74h3 | com.google.guava:guava | 31.1 jre | 32.0.0 android | Low | platform owned, no affected helper route |
| 7 | GHSA-7g45-4rm6-3mm3 | com.google.guava:guava | 31.1 jre | 32.0.0 android | Medium | platform owned, no temporary directory route |
| 6 | GHSA-6mjq-h674-j845 | io.netty:netty-handler | 4.1.82.Final | 4.1.94.Final | Medium | platform owned, no SNI route |
| 5 | GHSA-mc84-pj99-q6hh | org.apache.commons:commons-compress | 1.21 | 1.21 | High | Forge tooling owned, no affected parser route |
| 4 | GHSA-xqfj-vm6h-2x34 | org.apache.commons:commons-compress | 1.21 | 1.21 | High | Forge tooling owned, no affected parser route |
| 3 | GHSA-crv7-7245-f45f | org.apache.commons:commons-compress | 1.21 | 1.21 | High | Forge tooling owned, no affected parser route |
| 2 | GHSA-7hfm-57qf-j43q | org.apache.commons:commons-compress | 1.21 | 1.21 | High | Forge tooling owned, no affected parser route |
| 1 | GHSA-53x6-4x5p-rrvv | org.apache.commons:commons-compress | 1.21 | 1.19 | High | Forge tooling owned, no affected parser route |

This record does not dismiss or close any alert. It records why this mod cannot safely patch a
launcher supplied library in beta 3. Issue 9 remains open until a compatible Minecraft or Forge
platform update supplies patched versions and the complete build, dedicated server, client when
applicable, multiplayer, and archive gates pass.

## Monitoring

Dependabot checks Gradle and GitHub Actions weekly. Reopen this record when the Forge graph,
Minecraft version, Forge version, loader, or any relevant route changes. The archive boundary
check fails if FutureShops starts bundling these platform libraries.

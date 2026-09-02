# Phase 001 verification

## Scope

This packet records repository controlled verification for the NeoForge 1.21.1 issue 22 correction. The accepted product change is commit `bfba91f7b0c51b03d07117c4f1851c38a98f6186`, based on `247d8f6842bfa1f586e5b18a9aab67cabd3db89f`. The change is isolated to the NeoForge support line and introduces one client screen base class whose `renderBackground` method suppresses the misplaced vanilla background pass. All 16 FutureShops screens use that base class.

The Forge 1.20.1 line was not changed. No protocol, persistence, configuration, dependency, registry, server, or global rendering behavior was changed.

## Automated verification

The following commands passed on Java 21 with the checked in Gradle wrapper.

```text
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 bash ./gradlew test --tests com.enviouse.futureshopsp.client.screen.ShopScreenBackgroundPolicyTest --no-daemon
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 bash ./gradlew test --no-daemon
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 bash ./gradlew build --no-daemon
```

The focused test passed on the candidate branch and the complete unit suite passed. The baseline focused test was attempted against the unmodified support head and reported no matching tests, while the candidate test executed and passed. `git diff --check` passed and the candidate tree contains exactly the accepted 21 file change set.

## Client lifecycle verification

A disposable fixture was used only during runtime verification and was removed before the final build. It connected a NeoForge 1.21.1 client to an isolated local dedicated server on `127.0.0.1:25566`, prepared deterministic shop, barter, player shop, history, department, and franchise state, and routed each production screen through `Minecraft.setScreen`. Each screen was rendered for multiple ticks, resized, ticked, given safe mouse move, scroll, click, release, and keyboard input, then replaced so the normal close lifecycle ran.

The fixture opened all 16 required screens with no client exception or FutureShops error. The client log recorded the initial title screen and pause screen as negative controls, then all screen entries and a clean `Minecraft` stop.

| Index | Screen | Result |
| ---: | --- | --- |
| 1 | `ShopMainScreen` | passed |
| 2 | `BalanceOverviewScreen` | passed |
| 3 | `BalTopOverviewScreen` | passed |
| 4 | `BarterScreen` | passed |
| 5 | `CartScreen` | passed |
| 6 | `ItemDetailScreen` | passed |
| 7 | `TransactionHistoryScreen` | passed |
| 8 | `LocalShopBrowserScreen` | passed |
| 9 | `PlayerShopBlockScreen` | passed |
| 10 | `PlayerShopCartScreen` | passed |
| 11 | `PlayerShopBarterScreen` | passed |
| 12 | `PlayerShopSellScreen` | passed |
| 13 | `PromoEditorModalScreen` | passed |
| 14 | `SettlementHistoryScreen` | passed |
| 15 | `DepartmentPickerScreen` | passed |
| 16 | `FranchiseManagementScreen` | passed |

The final runtime log recorded `futureshops screen fixture complete screens=16` followed by `Minecraft Stopping!`. Representative rendered captures were retained outside the repository. Their SHA 256 values are:

```text
barter screen, 9834538311470fb6ad30e21241e7992ad96d783d9e5ffd40ab4a3f281c28d9b3
cart screen, eec9ea1d938880116afbeb32f5b14ee9f9956307ce99dcba63ae21d59066a1f0
```

The captures show FutureShops custom content and widgets at normal sharpness. The title and pause screens remained outside the FutureShops policy.

## Artifact verification

The rebuilt artifact is `futureshops-2.2.1.jar` with SHA 256:

```text
baf469ea5c062a2c0682913b38869efa39dda269d2b3ccb7f9a48ffe76d1126
```

The archive contains `META-INF/neoforge.mods.toml`, `AbstractShopScreen.class`, and the FutureShops assets. Generated metadata identifies FutureShops `2.2.1`, NeoForge loader range `[4,)`, NeoForge dependency range `[21,)`, and Minecraft range `[1.21.1,1.22)`. The archive contains no disposable fixture class, local logs, test output, or credentials.

## Post merge verification

Pull request `#37` was merged through GitHub into the `1.21.1` support line as merge commit `51cc7c1831079c12a6d6070bd16873e9fbcad01b`. The fetched remote branch was checked to confirm that the accepted product commit and this verification packet are ancestors of `origin/1.21.1`.

The merged revision was checked out separately and rerun with the checked in wrapper on Java 21.

```text
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 bash ./gradlew test --tests com.enviouse.futureshopsp.client.screen.ShopScreenBackgroundPolicyTest --no-daemon
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 bash ./gradlew test --no-daemon
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 bash ./gradlew build --no-daemon
```

All three post merge commands passed. The merged artifact was rebuilt as `futureshops-2.2.1.jar` with SHA 256 `6cdf3f1ca0dd16f4b9289cec3b02e5c2d8b376f2444bd48be50fad43054eac34`. Its metadata and class inventory matched the candidate artifact.

The merged client was launched under a disposable Xvfb display and connected to an isolated NeoForge 1.21.1 dedicated server on `127.0.0.1:25566`. The same fixture opened, resized, ticked, rendered, interacted with, and replaced all 16 production screens. The merged runtime log recorded the title and pause negative controls, every screen from index 1 through 16, `fixture complete screens=16`, and a clean `Minecraft Stopping!`. No error, exception, crash, or failed line was present in the final runtime log.

## Completion decision

All phase gates passed on the candidate and the exact merged revision. The signed phase tag `phase-001-neoforge-issue-22` and issue 22 closure complete this phase. No public release or Forge line change is authorized by this phase.

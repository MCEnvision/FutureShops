# FutureShops 3.0 Plan

Status. The ATM deposit recovery and Bazaar refund access blocker in Section 20 is implemented on the active 3.0 beta branch. The `3.0.0-beta.4` gameplay maintenance, `3.0.0-beta.5` dependency audit closure, and `3.0.0-beta.6` dependency maintenance followup below are implemented, verified, and merged. Community delivery and the remaining live multiplayer, reconnect, restart, and recovery fault acceptance checks still block release approval.

This document is the implementation contract for FutureShops 3.0. It covers the shared escrow foundation, migration of all existing value moving features, the Auction House, the Bazaar, the shared market interface, configuration, recovery, abuse controls, testing, and release sequencing.

## Beta release identity

FutureShops remains on the `3.0.0` release line until the owner explicitly approves a different
release line. Feature plan names, merged feature groups, and network protocol revisions do not
change the public mod version by themselves.

Every bug fix increments only the final beta build number by exactly one. Artifact names use
`futureshops-3.0.0-beta.<n>.jar`. The correction recorded by this plan changes the current build
from the incorrect `3.1.0-beta.1` identity to `3.0.0-beta.3`. Future bug fixes continue with
`3.0.0-beta.4`, `3.0.0-beta.5`, and so on unless the owner explicitly changes the release line.

The Gradle project version is the single source of the beta identity. Packaging must project the
same value into the JAR filename, the expanded `META-INF/mods.toml`, and the manifest
`Implementation-Version`. Documentation and release notes must use the same release line. Network
compatibility remains governed by the independent FutureShops protocol number.

This maintenance change does not alter gameplay, persistence, configuration schemas, packet
formats, or the release publication state. It adds a build policy regression test and requires a
clean build followed by packaged JAR inspection. Acceptance requires all of the following.

- `gradle.properties` declares `mod_version=3.0.0-beta.5` for the next verified bug fix artifact.
- The produced artifact is named `futureshops-3.0.0-beta.5.jar`.
- The expanded mod metadata and manifest both report `3.0.0-beta.5`.
- Repository documentation no longer describes the current artifact as `3.1.0`.
- The release line regression test rejects a version outside `3.0.0-beta.<positive integer>`.
- Publishing, tagging, and changing the release line remain separate owner authorized actions.

Implementation status. Implemented and verified on `envy/version-3-0-beta-3` on July 30, 2026,
but not yet merged. The clean test run passed 1,874 tests with no failures, errors, or skips. The
build and beta identity verification passed. The packaged JAR filename, expanded mod metadata,
manifest version, logo, mixin configuration, and refmap were inspected. A dedicated server reached
ready state with FutureShops `3.0.0-beta.3` loaded, and the virtual display client reached
FutureShops client setup and the render thread. The existing development world reported Forge's
expected version difference from the incorrect `3.1.0-beta.1` label and then reached ready state.
There were no implementation deviations. Release publication and release line approval remain
unchanged.

### Beta 4 issue maintenance

Objective. Resolve the reproducible selling and cart checkout regressions reported against the
current beta, classify every open dependency alert without weakening the pinned Forge platform,
and publish the result only as the next `3.0.0` beta artifact.

Current evidence.

- Server shop sell quotes currently return zero because the shared money quote helper only prices
  acquire options. The sell intent rejects that zero payout as invalid. The same defect affects
  direct Sell to Shop and the server shop Sell Inventory workflow.
- Admin and player shop carts retain an immutable request after a response timeout, which is
  required for replay safety. Their mutation guards and buttons currently treat any tracked
  request as actively processing, so a missing response permanently prevents clearing, reducing,
  or editing the cart.
- GitHub reports 25 transitive dependency alerts from the submitted Gradle graph. The affected
  libraries are inherited from Minecraft 1.20.1, Forge 47.4.20, ForgeGradle, or their tooling.
  FutureShops does not package those libraries inside its mod JAR. Changing only the development
  graph would not update libraries supplied to players by the pinned platform.

Scope and architecture.

1. Quote a Sell to Shop option from its configured per unit payout with checked integer
   multiplication. Keep acquire pricing delegated to the existing server pricing policy. Both
   direct and bulk selling must consume the same authoritative quote.
2. Distinguish an actively pending cart request from a timed out but still tracked request. Active
   requests continue to block cart mutations. After timeout, players may edit or clear the visible
   cart contents while the immutable original submission and request UUID remain available for an
   exact idempotent result check. A different checkout cannot start until the original request
   reaches a terminal result.
3. The cart result check must resend the original snapshot and payment source, never the edited
   cart. Delayed or replayed success subtracts only the acknowledged original quantities from the
   current cart. Terminal failure clears the tracker without removing cart contents.
4. Record every dependency alert with its advisory, vulnerable dependency, resolved graph,
   runtime or build classification, reachability evidence, patched boundary, and disposition.
   Do not add direct dependency constraints unless they update the real shipped runtime and remain
   compatible with Minecraft 1.20.1 and Forge 47.4.20.
5. Review Dependabot coverage for Gradle and GitHub Actions. Existing unrelated or untracked
   workflow work must not be overwritten. Default branch coverage that cannot be safely integrated
   on this branch remains an explicit repository maintenance gate rather than a false gameplay
   completion claim.

Compatibility, failure, and recovery behavior.

- No catalog, offer, escrow, claim, configuration, or persistent schema changes are permitted.
- No packet identifier or payload shape changes are required. Existing request UUID replay and
  server authoritative validation remain mandatory.
- Checked payout overflow fails safely as an invalid request before inventory or money mutation.
- A cart timeout never creates a second request identity. Editing the visible cart does not cancel
  or replace the unresolved economic request.
- Platform owned dependency alerts that require a Minecraft or Forge upgrade remain documented
  and open. A development only override must not be presented as a player security fix.

Verification and acceptance.

- Focused tests prove direct and bulk sell quotes use the configured positive payout and preserve
  free, barter, and acquire pricing behavior.
- Focused tests prove timed out admin and player shop carts can be edited and cleared, retry the
  immutable original submission, reject a different checkout, handle delayed success by exact
  quantity, and unlock normally on terminal failure.
- Interface tests or source contracts prove both cart screens expose the result check and enable
  mutation controls only after the active timeout ends.
- The dependency record covers all 25 alerts and JAR inspection proves the reported third party
  packages are not bundled by FutureShops.
- The complete test suite, build, dedicated server smoke test, client smoke test, multiplayer
  request and response checks, packaged JAR inspection, and final diff inspection pass.
- The artifact, expanded metadata, manifest, documentation, and release notes all report
  `3.0.0-beta.4`. Issues remain open and in review until the fix is merged and its acceptance gates
  pass on the approved default branch.

Implementation status on July 31, 2026. The sell quote path now uses the configured server shop
payout for sell actions and fails closed on multiplication overflow. Both cart implementations
distinguish an actively pending request from an unresolved timed out request. A timed out cart can
be edited or cleared while retaining its immutable original submission, and the Check Result
action resends only that original request. Delayed success removes only acknowledged original
quantities, while terminal failure preserves current cart contents.

Local verification passed 1,886 unit and source contract tests, all 5 required GameTests, the
Gradle build, dedicated server startup, client startup, JAR metadata inspection, third party class
inspection, JSON validation, and final diff checks. The resulting artifact is
`futureshops-3.0.0-beta.4.jar`, and both its expanded mod metadata and manifest report
`3.0.0-beta.4`. The dependency record classifies all 25 open alerts. Those inherited platform and
tooling alerts are not falsely marked remediated because replacing their runtime libraries requires
a separately planned compatible Minecraft, Forge, and ForgeGradle upgrade. Dependabot coverage is
prepared for Gradle and GitHub Actions. The gameplay issues remain in review until merge and live
multiplayer confirmation.

### Beta 5 dependency audit closure

Objective. Complete GitHub issue 9 as an evidence based supply chain audit, enforce the boundary
between the FutureShops artifact and launcher supplied libraries, activate reviewed dependency
monitoring on the default branch, and produce the next `3.0.0` beta artifact for community
retesting.

Current evidence and decision.

- All 25 alerts originate from the `settings.gradle` dependency snapshot. They resolve through
  Minecraft 1.20.1, Forge 47.4.20, ForgeGradle 6.0.54, or MixinGradle 0.7 snapshot rather than a
  direct FutureShops library declaration.
- Netty, Log4j, Commons IO, Commons Compress, Commons Lang, and Guava exist on the game runtime.
  Plexus Utils and older Commons versions also exist in Forge runtime tooling or the build plugin
  graph. FutureShops directly uses only bounded Netty buffer and decoder exception APIs.
- The affected Netty handlers and codecs, Log4j layouts and appenders, archive readers, class name
  parser, XML reader, and Guava temporary file helpers are not selected by FutureShops. Minecraft,
  Forge, server operators, and the build tools can still reach platform owned library code, so the
  submitted graph is not a false positive.
- A normal Forge mod cannot replace libraries already selected by the launcher. Adding patched
  copies with Jar in Jar would introduce duplicate or split packages and would not safely replace
  bootstrap classes. Development only constraints would leave player installations unchanged.
  Therefore no direct constraint or bundled replacement is permitted in this beta.
- Alerts that require a Minecraft, Forge, or ForgeGradle upgrade receive an explicit accepted risk
  disposition with the pinned platform boundary and the first patched version. Dismissal means the
  audit is resolved for this release line, not that the upstream component was patched.

Scope and implementation.

1. Publish a beta 5 dependency disposition record that maps each alert number and advisory to its
   resolved version, runtime or build classification, FutureShops reachability, first patched
   version, GitHub disposition, and future remediation boundary.
2. Add a deterministic packaged dependency boundary gate. It must fail if the FutureShops JAR
   contains Netty, Apache Commons, Guava, Log4j, or Plexus class files or Forge Jar in Jar metadata.
3. Retain weekly Dependabot coverage for Gradle and GitHub Actions. Group only minor and patch
   updates. Keep major platform changes visible for explicit compatibility planning.
4. Bump the artifact, expanded mod metadata, manifest, documentation, and release notes to
   `3.0.0-beta.5` without changing the Minecraft, Forge, Java, mappings, loader, or protocol
   boundary.
5. After deterministic verification passes, merge through the protected pull request workflow so
   Dependabot coverage exists on the default branch. Reconcile GitHub issue 9 and its 25 alert
   dispositions only after the merged state is verified.
6. Send the verified JAR and checksum to each accessible active Discord support thread. Ask the
   reporter to replace both client and server copies, restart fully, reproduce the original issue,
   and return the latest log if it persists. If Discord access is unavailable, record the exact
   delivery blocker and provide the ready to send message without claiming outreach succeeded.

Compatibility, security, and recovery behavior.

- This work does not change gameplay, persistent schemas, configuration, packets, catalog data, or
  escrow behavior.
- Platform alert acceptance does not weaken packet validation, server authority, artifact
  inspection, or future upgrade obligations.
- A future compatible platform upgrade must reopen the record, remove obsolete accepted risks,
  and pass the complete compatibility and multiplayer matrix before release.
- No GitHub alert may be marked inaccurate or not used when the submitted graph remains vulnerable
  or platform code can still reach the dependency.

Verification and acceptance.

- A source contract proves every current alert and advisory is present exactly once with a runtime
  or build classification, reachability statement, patched boundary, and disposition.
- The packaged dependency boundary task passes against the exact release JAR and remains part of
  `check`.
- Unit tests, all required GameTests, the Gradle build, dedicated server startup, client startup,
  JAR metadata inspection, JSON validation, secret and generated output inspection, and complete
  Git diff inspection pass.
- The artifact is named `futureshops-3.0.0-beta.5.jar`. Its expanded mod metadata and manifest both
  report `3.0.0-beta.5`.
- `.github/dependabot.yml` exists on the verified default branch and covers both Gradle and GitHub
  Actions.
- GitHub issue 9 is closed only after its acceptance criteria pass on the default branch. Discord
  delivery is reported accurately and is not treated as complete when no authorized integration is
  available.

Implementation status on July 31, 2026. The beta 5 record classifies all 25 alerts and records the
accepted risk at the pinned platform boundary. No direct dependency override or duplicate packaged
library was added. The new packaged dependency gate is part of `check` and rejects the affected
class namespaces and Forge Jar in Jar metadata.

Local verification passed 1,887 unit and source contract tests, all 5 required GameTests, the
Gradle build, dedicated server ready state, client setup and render thread startup, expanded mod
metadata and manifest inspection, JSON validation, forbidden package inspection, and final diff
checks. The resulting artifact is `futureshops-3.0.0-beta.5.jar`. The final delivery checksum must
be calculated from the verified artifact after the last build because Forge reobfuscation rewrites
the archive during packaging. Default branch integration, GitHub alert reconciliation, issue
closure, and Discord delivery remain pending and must not be reported as complete before their
external state is verified.

### Beta 6 dependency maintenance

Objective. Reconcile the open Dependabot pull requests without crossing the pinned Forge platform
boundary, keep the JUnit test runtime internally compatible, and produce the next `3.0.0` beta
artifact only after the updated default branch passes the complete Gradle verification gate.

Current evidence and decisions.

- Pull request 14 replaces the moving MixinGradle `0.7-SNAPSHOT` selector with the fixed `0.7.38`
  release. Its Gradle and secret checks pass against beta 5.
- Pull request 16 updates the Foojay toolchain resolver convention from `0.7.0` to `1.0.0`. The
  plugin requires Java 17 or newer, which matches this project, and its Gradle and secret checks
  pass against beta 5.
- Pull requests 13 and 17 independently update JUnit Jupiter and the JUnit Platform launcher from
  the aligned `5.10.2` and `1.10.2` generation to `6.1.2`. Either pull request alone leaves an
  incompatible mixed JUnit runtime. The update must be combined by declaring JUnit Jupiter
  `6.1.2` and allowing its JUnit BOM to supply the matching launcher version.
- Pull request 15 updates the wrapper from Gradle `8.14.4` to `9.6.1`. ForgeGradle rejects Gradle 9
  during plugin application, before project compilation. The wrapper remains pinned to `8.14.4`,
  and Dependabot must ignore that incompatible major line until a separately approved ForgeGradle
  migration supports it.

Scope and implementation.

1. Merge the MixinGradle and Foojay updates through their existing Dependabot pull requests after
   their current heads and required checks are verified.
2. Repair the JUnit Jupiter pull request so Jupiter and the launcher resolve through the same JUnit
   `6.1.2` BOM. Close the separate launcher pull request as superseded only after the repaired pull
   request is verified.
3. Close the Gradle 9 wrapper pull request as incompatible and record the exact ForgeGradle failure.
   Do not modify the wrapper, ForgeGradle, Minecraft, Forge, Java, mappings, or loader boundary.
4. Increment the beta artifact identity once to `3.0.0-beta.6`. Keep the JAR filename, expanded mod
   metadata, manifest, documentation, and release notes aligned with that version.
5. After all compatible pull requests merge, update local `master` from `origin/master` and run the
   full Gradle test and build gates from the exact merged default branch.

Compatibility, security, and recovery behavior.

- These dependencies affect build configuration and test execution only. They do not change the
  shipped gameplay runtime, packets, persistent schemas, configuration, escrow, or market state.
- The packaged dependency boundary remains mandatory and must prove that no build or test library
  enters the FutureShops JAR.
- A failed pull request check, incompatible dependency graph, unresolved review, or nonmatching
  JUnit generation blocks merge. No required check or review is bypassed.
- The existing untracked local workflows and generated run data remain outside this maintenance
  change.

Verification and acceptance.

- Pull requests 14 and 16 merge with their successful checks intact.
- The repaired JUnit pull request resolves Jupiter and the launcher to `6.1.2`, passes all required
  checks, and merges. The separate launcher pull request closes as superseded.
- The Gradle 9 wrapper pull request closes without merge and records ForgeGradle's explicit Gradle
  9 incompatibility.
- `test`, `check`, and `build` pass from updated `master`. The final test count is recorded from the
  generated JUnit results.
- The packaged JAR is named `futureshops-3.0.0-beta.6.jar`. Its expanded `mods.toml` and manifest
  report `3.0.0-beta.6`, and the forbidden packaged dependency gate passes.
- GitHub shows no remaining open Dependabot pull request from this update batch. Any future major
  platform proposal remains review only.

Implementation status on July 31, 2026. Pull requests 14 and 16 merged the fixed MixinGradle
`0.7.38` and Foojay resolver `1.0.0` updates. Pull request 13 merged the aligned JUnit `6.1.2`
runtime after both review findings were corrected and every required GitHub check passed. Pull
request 17 closed as superseded. Pull request 15 closed without merge because ForgeGradle rejects
Gradle 9 during plugin application, and Dependabot was instructed to ignore that major line.

Verification on merged `master` passed `clean`, `test`, `check`, and `build` with 1,887 tests and
no failures, errors, or skips. Dependency insight resolves JUnit Jupiter and the JUnit Platform
launcher to `6.1.2` through the same BOM. The wrapper remains Gradle `8.14.4`. Packaged dependency
validation passed, and the JAR contains the expected logo, mixin configuration, and refmap. The
verified artifact is `futureshops-3.0.0-beta.6.jar`. Its expanded mod metadata and manifest report
`3.0.0-beta.6`. Its SHA-256 is
`d667668845e10ef30c89103c84411e46a1c2bc753aa03a5b5060fb81de0849fa`, and its SHA-512 is
`05e9a8fbc47d14c94ad9a9e4cfa5e0b99e4b0ae67d95cb488ca2bfa884019bda24fbdfdd7af5708e6afb5f830f6f075b2710a32da8ff553cae92bfdbb8b08b0d`.
No Dependabot pull request from this batch remains open. Gameplay, network, persistence,
configuration, escrow, and release publication behavior did not change.

### Issue 18 escrow lifecycle concurrency review

Objective. Resolve the twenty high severity CodeQL time of check and time of use findings on the
merged beta 6 baseline without weakening escrow readiness, replay protection, recovery,
checkpoints, claims, wallets, or logical server authority.

Release boundary. This security bug fix increments the test artifact once to `3.0.0-beta.7`.
Minecraft, Forge, ForgeGradle, Gradle, mappings, Java, and network protocol versions remain pinned.

Current evidence. The findings cover runtime acquisition in cash deposit and money claim flows,
runtime lifecycle checks in `EscrowRuntimeManager`, scheduler and coordinator state checks inside
`EscrowRuntimeService`, and Server Shop replay compaction. Runtime value mutation is already
confined to the owning logical server thread, and the relevant runtime methods are synchronized.
CodeQL cannot infer that thread confinement across separate method calls. The unbound static
runtime lookup is still a weak boundary because it does not prove that its caller owns the server
lifecycle. Nested synchronized resources also make their atomicity implicit instead of local to
the check and use.

Implementation.

1. Add server scoped runtime lookup and readiness methods that require the owning server thread,
   reject a mismatched server, and fail closed during quiescence. Use them for the reported money
   claim and cash deposit value paths.
2. Keep the no argument lookup only for existing read only status paths that have no server
   context. Do not broaden its use in value moving code.
3. Make each reported state check and dependent use atomic on the same runtime, coordinator,
   scheduler, or replay store monitor. Preserve the owning server thread assertion as a second
   invariant rather than relying on monitor locking alone.
4. Add source contracts and focused concurrency tests for the server bound lookup, wrong server
   rejection, off thread rejection, lifecycle quiescence, and replay retention locking. Do not
   change persistent data, journals, packets, configuration, pricing, or replay formats.
5. Run CodeQL on the exact pull request commit. Fix every valid finding. Dismiss a remaining false
   positive only with exact server thread and monitor evidence recorded on that alert.

Verification and acceptance.

- Focused runtime lifecycle and source contract tests compile and pass.
- The full unit suite, all GameTests, `check`, `build`, dedicated server smoke test, client smoke
  test, packaged JAR inspection, and complete diff inspection pass.
- The exact pull request commit receives successful Gradle, dependency review, secret scan, and
  Java CodeQL checks.
- Zero unexplained open findings remain for `java/toctou-race-condition`. Issue 18 stays open until
  the reviewed pull request is merged and the default branch scan confirms the final state.
- Issues 10 and 11 remain open until their original Discord reporters receive and test the exact
  distributable JAR. Their test evidence does not authorize release publication.

Implementation status. Signed commit `a41bec43137d0256d36dcb401e3928515bf140c4` passes all 1,888
unit and architecture tests, all five required GameTests, `check`, `build`, dedicated server startup
on an isolated port, headless client setup and atlas creation, metadata inspection, and dependency
boundary verification on node1. The resulting `futureshops-3.0.0-beta.7.jar` is 5,730,621 bytes.
Its SHA-256 is `04d03bee881fa7a1a3dfa4d7f07d1a099d40998d2b052bfe2592c131847b0c95`, and its
SHA-512 is `8ca549a6f163d3926809187c19ffd8a3441c0bed3fbfb0ad979d845c9524a02d8c9ecd36d925c1e1edaf737cae7abc837593a6492402152b84551eaf5d1334b1`.
Source contract tests normalize platform line endings so the same verification gate runs on Windows
and Linux checkouts.
Pull request checks, CodeQL confirmation, merge, default branch scanning, and reporter testing remain
open gates.

The required build order is.

1. Freeze and verify the regression baseline for all community bug reports.
2. Build mandatory durable escrow and the shared money ledger.
3. Migrate every existing value moving operation onto escrow.
4. Extract the current shop interface into a reusable themed market shell.
5. Build the Auction House.
6. Build the Bazaar and matching engine.
7. Complete crash, migration, abuse, performance, and interface hardening.

Auction House and Bazaar transaction code must not ship before escrow protects the existing economy. Adding long lived bids and orders to the current best effort rollback model would multiply the existing crash windows.

## 1. Non negotiable requirements

- Escrow is mandatory for every operation that moves money, items, stock, barter assets, or claims.
- Escrow cannot be disabled while value moving features are available.
- Bazaar and Auction House are independently toggleable in the main config.
- Disabling a module never deletes listings, orders, bids, history, custody, or claims.
- Claims, refunds, cancellation, and administrative recovery remain available while a module is disabled.
- The client never supplies trusted prices, fees, ownership, stock, deadlines, balances, or item identity.
- Every mutating request has a request UUID and can produce at most one economic result.
- Inventory overflow never drops or deletes value. It creates or retains a claim.
- Maximum wallet balance never deletes proceeds. Money remains claimable.
- Every monetary calculation uses integer minor units and exact overflow checked arithmetic.
- Active listings and orders snapshot the applicable rules. Config reload cannot alter a live contract retroactively.
- Semantic status colors remain separate from module themes.
- Foreign mod currency uses escrow request safety but receives no FutureShops physical currency duplication guarantee.
- Newer, corrupt, or unverifiable custody data fails closed.
- No value bearing record is archived while it still owns or owes an asset.

## 2. Target architecture

FutureShops Core owns shared safety and infrastructure. Shop, Auction House, Bazaar, ATM, deposit, withdrawal, and pay become domain clients of that core.

```text
Shop screens and commands
Auction House screens and commands
Bazaar screens and commands
ATM, deposit, withdrawal, and pay
             |
             v
      Domain transaction plans
             |
             v
        Escrow coordinator
             |
     +-------+--------+
     |                |
 Money ledger      Asset vault
     |                |
 Wallets          Exact item stacks
 Holds            Protected bills
 Claims           Foreign currency
 Fees             Barter assets
 Sinks            Stock reservations
     |                |
     +-------+--------+
             |
        Claim center
             |
     Automatic delivery
     Lost and Found screen
     Administrative recovery
```

Core responsibilities.

- Durable escrow journal and recovery.
- Double entry money ledger.
- Item and physical currency custody.
- Claims and Lost and Found.
- Transaction IDs and idempotency.
- Shared market interface and themes.
- Module lifecycle and public capability synchronization.
- Packet bounds and request rate limits.
- Audit events and history projection.
- Permission helpers.

Module responsibilities.

- Shop owns catalog and player shop business rules.
- Auction House owns listings, bids, expiration, settlement, and moderation.
- Bazaar owns products, orders, matching, fills, and market statistics.

## 3. Current risks addressed by escrow

The existing code has strong server validation and careful best effort rollback, but most rollback receipts live only in memory. Minecraft SavedData being dirty does not force a durable write at the transaction boundary.

The escrow migration must address the following known risks.

- Internal balances have no holds, transaction IDs, or applied operation index.
- Payment receipts exist only in memory.
- Protected bill deposits can consume mint capacity before wallet credit succeeds.
- Foreign currency deposits can credit value before the ordinary stack is durably removed.
- ATM debit, minting, delivery, and refund are not restart safe.
- Player shop settlement claims clear pending revenue before wallet credit.
- Player shop settlements omit dimension from shop identity.
- Reused shop coordinates can confuse ownership of old pending proceeds.
- Admin shop stock is runtime state rather than durable reserved stock.
- Shop purchase and sale paths have several crash boundaries between payment, stock, item delivery, and rollback.
- Player shop money purchases can record seller settlement before seller output is secured.
- Barter payment can enter owner storage before seller output is secured.
- Buyback can insert seller items before owner money and seller payout are committed.
- External storage adapters lack transaction IDs and durable mutation receipts.
- Partial adapter insertion and extraction can be mistaken for all or nothing behavior.
- Several calculations require exact overflow checks.
- Event modified prices require consistent post event validation.
- Transaction history has no transaction ID and cannot safely deduplicate replay.
- Administrative balance changes can bypass ordinary provider rules.
- Newer SavedData schemas currently warn rather than always failing closed.

## 4. Shared escrow system

### 4.1 Transaction record

Every transaction contains.

- Transaction UUID.
- Optional parent UUID for listing, order, fill, or claim trees.
- Client request UUID or deterministic idempotency key.
- Module and operation type.
- Initiator, payer, beneficiary, seller, buyer, and system account roles.
- Exact shop reference including dimension and block position.
- Creation time, update time, server tick, schema version, and revision.
- Config revision and immutable price, fee, and rule snapshot.
- Source assets.
- Expected destination assets.
- Money ledger legs.
- Stock and storage reservations.
- Retry count, next retry time, and last failure.
- Protection level for each asset.
- Journal sequence references.
- Administrative recovery reason when applicable.

Representative operation types.

- SERVER_SHOP_BUY.
- SERVER_SHOP_SELL.
- SERVER_SHOP_BARTER.
- PLAYER_SHOP_BUY.
- PLAYER_SHOP_BARTER.
- PLAYER_SHOP_COMPOUND.
- PLAYER_SHOP_BUYBACK.
- ATM_WITHDRAW.
- CURRENCY_DEPOSIT.
- PLAYER_PAY.
- AUCTION_LISTING.
- AUCTION_BID.
- AUCTION_SETTLEMENT.
- BAZAAR_ORDER.
- BAZAAR_FILL.
- CLAIM.
- ADMIN_BALANCE_MUTATION.

### 4.2 State machine

```text
CREATED
  to VALIDATED
  to HOLDING
  to HELD
  to COMMIT_DECIDED
  to COMMITTED
  to CLAIMS_CREATED
  to COMPLETED

VALIDATED, HOLDING, or HELD
  to ABORTING
  to REFUND_PENDING
  to REFUNDED

Any unresolved state
  to RECOVERY_REQUIRED
  to resumed normal processing or MANUAL_REVIEW
```

COMMIT_DECIDED is the durable ownership boundary.

- Before COMMIT_DECIDED, recovery restores assets to original owners.
- After COMMIT_DECIDED, ownership has changed and recovery completes delivery to new owners.
- Delivery failure never erases a committed sale. It leaves a claim.
- Each input asset has its own held state so partial acquisition can be recovered.
- Invalid transitions fail closed.

### 4.3 Durable journal

A new SavedData class alone is insufficient. The implementation requires an append only write ahead journal.

- Records are framed with length, schema, journal sequence, transaction ID, step ID, and checksum.
- Critical records are forced to disk before the associated side effect.
- Checkpoints store materialized state and the last applied journal sequence.
- Startup loads the latest verified checkpoint and replays subsequent records.
- A truncated final record may be discarded without losing valid earlier records.
- Checksum mismatch, disk failure, or unsupported newer schema enters economy maintenance mode.
- New value moving operations stop in maintenance mode.
- Safe browsing, inspection, claims, and recovery remain available when writes are safe.
- Journal compaction happens only after a verified checkpoint.
- Clean shutdown blocks new transactions, drains current work, forces the journal, and checkpoints.

### 4.4 Money ledger

The internal balance store evolves into a double entry ledger or equivalent atomic account model.

Account types.

- Player available wallet.
- Player reserved funds.
- Transaction escrow.
- Player money claim.
- Protected physical currency outstanding.
- Foreign currency source and sink.
- Server shop source and sink.
- Bazaar fee account.
- Auction fee account.
- Optional server treasury.
- Administrative source and sink.

All ledger operations balance. Only explicit system mint or sink operations create or destroy value.

All calculations use exact addition, subtraction, and multiplication. Percentage fees use basis points and one explicit rounding policy.

### 4.5 Asset custody

Escrow asset types.

- Wallet money.
- Protected FutureShops physical money.
- Foreign physical currency.
- Ordinary exact item stacks.
- Barter bundles.
- Admin catalog stock reservations.
- Player shop storage reservations.
- Auction listing payloads.
- Bazaar commodity lots.
- Fees and taxes.
- Explicit system sources and sinks.

Each item lot stores.

- Lot UUID.
- Previous and intended owner.
- Complete serialized item stack.
- Registry ID.
- Count.
- Full NBT and Forge capability data.
- Fingerprint.
- Original source.
- Protection tier.
- Allocated and unallocated quantities.
- Claim status.
- Raw fallback data if the item registry entry disappears.

Items are never reconstructed from only registry ID and count. Damage, enchantments, names, lore, contents, and mod specific data must survive custody.

### 4.6 Protection tiers

Protected assets include the internal wallet, FutureShops money, FutureShops vaults, and claims. These receive journal, mint ledger, idempotency, and recovery protection.

Reconciled assets include player inventory and supported vanilla storage. These receive exact stack custody plus preimage and postimage reconciliation.

External assets include foreign currency and unsupported third party storage. FutureShops prevents duplicate processing of its own requests, but cannot guarantee the external item supply or storage implementation.

Auction House and Bazaar first release listing items come from player inventory into a FutureShops controlled vault. Direct listing from external storage waits until that adapter can provide deterministic receipts.

### 4.7 Foreign currency warning

The generated TOML warning is.

```toml
# WARNING. Changing the currency provider from futureshops disables all FutureShops physical currency duplication protection. Currency items from other mods are spawned and accepted without mint ids, checksums, or spent mint tracking.
```

Mint checksums and spent mint protection are disabled for foreign currency. Escrow request deduplication remains active. The foreign mod remains responsible for crafting, loot, creative access, duplication bugs, and currency supply.

### 4.8 Protected bills

The mint ledger gains explicit authorized, available, reserved, spent, refunded, and quarantined states.

- Each stack uses one immutable mint batch ID, denomination, authorized count, and checksum. This matches Minecraft stack NBT semantics and avoids pretending that indistinguishable items in one stack carry separate identities.
- The ledger tracks exact quantities in authorized, available, reserved by transaction, spent by transaction, refunded, and quarantined buckets. Their sum must always equal the batch authorized count.
- Splitting and merging stacks preserves the same batch ID and checksum while the ledger caps their aggregate usable quantity. Stacks from different mint batches never merge unless the operation first consumes them and journals a replacement batch.
- Holding protected bills reserves an exact quantity from each mint batch for one transaction.
- Commit marks the reserved quantities spent.
- Refund returns protected value using a journaled replacement mint batch ID and exact count when needed. Spent batch quantities are never made available again.
- ATM mint plans, mint batch IDs, authorized counts, denominations, and checksums are persisted before item creation.
- Recovery reuses the same plan and does not mint a second unrelated batch.
- Custody provenance records the batch ID and exact bill count represented by each stack.
- A stack is rejected or quarantined when its count exceeds the batch quantity still available to that location, its checksum is invalid, its denomination differs, or its batch is spent, unknown, or reserved elsewhere.
- A protected bill does not expire while held in escrow.
- Long lived bid and order payments become escrow monetary value with an explicit refund policy.

### 4.9 Claims and Lost and Found

Access points.

- The escrow command.
- Claim and Claim All commands.
- Profile and balance screens.
- Claims tab in Shop, Auction House, and Bazaar.
- Shared header notification badge.

Claim types.

- Money.
- Items.
- Protected cash.
- Exact foreign cash.
- Auction winnings.
- Auction refunds.
- Expired auction items.
- Bazaar purchased goods.
- Bazaar proceeds.
- Price improvement refunds.
- Player shop proceeds.
- Barter proceeds.

Claim rules.

- Claims are idempotent.
- Full inventory leaves undelivered items claimable.
- Maximum wallet balance leaves money claimable.
- Offline players retain claims.
- Claim All is bounded and reports partial success.
- Module disable never blocks ownership claims.
- Permission revocation cannot confiscate claims.
- Unknown items remain quarantined with raw serialized data.
- Automatic delivery is a convenience attempt rather than the source of truth.

## 5. Existing flow migration

Every current flow migrates before Auction House or Bazaar launches.

### Server shop buy

Hold payment, reserve durable stock, commit payment to the system sink, create the exact output claim, and attempt delivery.

### Server shop sell

Hold exact player items, reserve stock capacity, commit items to stock or sink, create payout, and attempt wallet claim.

### Server shop barter

Hold every ingredient and reserve every output before one commit decision. Bundles remain atomic.

### Server shop cart

Use one parent transaction with child lines. Default to atomic all or nothing behavior.

### Player shop purchase

Snapshot the beneficiary, hold buyer payment, secure seller output, commit seller proceeds and buyer item claim, then attempt delivery.

### Player shop barter and compound trade

Hold buyer money when present, buyer barter assets, and seller output before one swap commit. Owner barter proceeds become an item claim by default rather than being inserted before commit.

### Player shop buyback

Hold owner money and seller items, then commit money to seller and items to owner claim or post commit storage delivery.

### ATM and withdrawal

Hold wallet value, persist denomination and mint plan, commit mint liability, create cash claim, and attempt inventory delivery.

### Deposit and right click deposit

Hold exact physical stacks, validate maximum balance, and commit physical consumption with wallet credit using the same durable decision.

### Pay

Hold sender funds and commit recipient wallet credit or claim.

### Settlement claim

Treat settlement as an existing claim. Wallet failure leaves the claim unchanged.

### Administrative balance changes

Use journaled administrative source or sink entries. Do not overwrite unrelated holds.

Additional migration requirements.

- Split player shop transaction logic from interface configuration logic.
- Make shop identity dimension aware and stable across owner changes.
- Make catalog stock persistent with reservations.
- Prevent stock refresh from overwriting reservations.
- Convert the current purchase payment service into a custody adapter.
- Allow cancellation only before custody. Post hold events are non cancellable and carry transaction IDs.

## 6. Payment source selection

Every purchase or money commitment shows a source confirmation unless server policy selects one.

Sources.

- Wallet balance.
- Inventory cash using the active provider.
- Optional automatic priority policy.
- Configurable wallet or physical payment with an explicit per-purchase choice.

The confirmation displays.

- Purchase or hold amount.
- Wallet balance.
- Available physical value.
- Exact representability.
- Provider name.
- Protected or unprotected indicator.
- Denomination breakdown.
- Fees and taxes.
- Physical remainder destination.
- Expected output.
- Quote expiration and changed price warning.

Policies.

- PROMPT is the default.
- WALLET_ONLY.
- PHYSICAL_ONLY.
- PREFER_WALLET.
- PREFER_PHYSICAL.
- Optional MIXED.

Long lived bids and buy orders consume physical money into escrow at placement. It is never merely checked and left accessible.

Recommended partial fill policy.

- Wallet refunds return to wallet claim.
- Protected physical change defaults to wallet claim.
- Foreign physical refund returns exact stacks only when representable.
- Otherwise unused value returns to wallet claim after explicit confirmation.
- Seller proceeds default to wallet claims regardless of buyer source.

## 7. Shared market interface

The current Shop layout is generalized rather than copied.

Every module uses.

- Header.
- Module switcher.
- Module local tabs.
- Search.
- Balance pill.
- Claims and profile pill.
- Close button.
- Breadcrumb.
- Left category rail.
- Toolbar and segmented filters.
- Grid, list, or master detail content.
- Contextual footer.
- Tooltips and keyboard navigation.
- Responsive compact layout.

Default module identity.

- Shop keeps Nocturne blurple with primary accent 9184D9.
- Bazaar uses emerald green with primary accent 48B978.
- Auction House uses crimson red with primary accent D85B68.

Themes change identity accents, selected surfaces, active borders, calls to action, header gradient, module icon, and focus highlight. They do not replace semantic success, warning, error, information, currency, or disabled colors.

### Navigation rules

- X always closes the complete interface.
- Back always includes an arrow and restores the exact prior route.
- Detail screens preserve query, page, filter, sorting, category, selection, and scroll.
- Escape returns from detail and closes a root screen.
- Switching modules closes any block bound shop session.
- Delayed packets cannot reopen a closed interface.
- No redundant Storefront and Close behavior.
- Disabled modules with claims appear as Claims Only.

Navigation uses explicit market routes and request nonces rather than arbitrary parent screen chains.

### Responsive behavior

Wide layout shows full brand, module chips, search, account controls, sidebar, and multiple columns.

Medium layout uses compact brand and tabs while preserving search.

Narrow layout uses a category drawer, one column rows, a secondary tab row, and child detail screens.

Required tests include 1280 by 720, 1920 by 1080, GUI scale 2, 3, and 4, long translations, high contrast, keyboard only use, reduced motion, and colorblind presets.

### Client customization

- Theme presets and accent overrides.
- High contrast.
- Colorblind modes.
- Reduced motion.
- Animation speed.
- Sound and volume.
- Compact, normal, and comfortable density.
- Card sizing.
- Remembered tab, filter, and sorting.
- Remembered payment source per session.
- 12 or 24 hour timestamps.
- Search behavior.
- Confirmation preferences.

Accessibility overrides always win over server branding.

## 8. Auction House

Commands include the short and long Auction House commands plus create, mine, bids, and claims routes.

### Screens

The Auction House uses the exact shared Nocturne shell used by `/shop`. Server Shop, Player Shops,
Bazaar, and Auction House remain in one stable top navigation row with the same blurple palette,
logo, wordmark, search, balance, profile, and close controls. A client theme override recolors the
whole marketplace consistently instead of assigning a different color to each module.

Tabs.

- Browse.
- My Auctions.
- My Bids.
- Watchlist.
- Claims.
- History.

Categories and saved filters live in the left rail. Sorting includes ending soon, newest, lowest price, highest current bid, most bids, seller, price range, enchantment, exact variant, mod ID, and category.

Listing cards show the exact item, seller name and skin head, buy now or current bid, minimum next
bid, bid count, remaining time, watched state, listing status, live listing count, and recent market
activity. Hovering the item shows the complete vanilla tooltip from the escrowed item snapshot,
including enchantments, lore, damage, custom names, and other NBT. Hovering a seller shows their
UUID and transaction participant context. Details show the top bidder or buyer, hourly and daily
activity, average price, units sold, and a bounded recent price graph.

The footer shows Create Listing, active listing count, active bid holds, outbid notifications, and claim count.

### Listing model

Each listing stores.

- Listing UUID.
- Seller UUID.
- Escrow item lot IDs.
- Exact item fingerprint and search document.
- Listing type.
- State and revision.
- Starting bid.
- Optional buyout.
- Bid increment snapshot.
- Listing fee and sale tax snapshots.
- Creation and deadline.
- Original duration and frozen remaining duration.
- Anti snipe count and cap.
- Highest bid reference and accepted bid count.
- Category.
- Moderation status.
- Config revision.

Listing types are Buy Now, Timed Auction, and Auction with Buyout.

States include Draft, Holding, Active, Frozen, Sold Pending, Ended Sold, Ended Unsold, Cancel Pending, Cancelled, Settled, and Manual Review.

### Listing creation

1. Select an inventory item.
2. Preview exact NBT and quantity.
3. Select listing type.
4. Enter price, optional buyout, and duration.
5. Receive server fee quote.
6. Select payment source for fee.
7. Server rechecks slot, fingerprint, permissions, restrictions, limits, and fee.
8. Escrow holds item and fee.
9. Listing becomes visible only after custody is durable.
10. Activation failure returns both item and fee.

First release supports one logical stack or exact item lot. Multi stack bundle listings remain off until the base market is proven.

### Bidding

Each bid request contains listing ID, expected revision, amount, payment source, and request UUID.

The server locks the listing, rechecks active state and deadline, rejects seller self bid, validates limits and minimum bid, holds funds, compares revision, commits the new highest bid, creates the former bidder refund claim, and applies any anti snipe extension.

Rules.

- Equal bids never replace an earlier accepted bid.
- Raising an existing bid holds only the delta.
- A request at the deadline succeeds only if processed while active.
- Lost responses return the stored result.
- Bid retraction is disabled by default.
- Seller cancellation is allowed only before an accepted bid by default.
- Proxy bidding remains disabled in the first release.

### Completion

No bid returns the exact item as a seller claim.

A winning bid allocates item claim, seller proceeds, tax, fees, and all refunds.

Buy Now locks the listing, holds the buyer funds, allocates item and proceeds, refunds any top bid, and stops further actions.

### Time and anti sniping

Real time is the recommended default. Online server time is optional. Deadline and module freeze intervals are persisted. Backward clock movement is clamped and overdue records are processed in bounded batches.

Anti snipe configuration includes threshold, extension length, maximum cumulative extension, maximum extension count, and buyout availability after bids.

### Moderation and abuse controls

- Active listing and bid limits.
- Maximum held value.
- Price and quantity limits.
- NBT size limits.
- Duration presets.
- Listing and bid rate limits.
- Listing fee and sale tax.
- Fee destination.
- Cancellation rules.
- Allowed and denied item IDs and tags.
- Container and capability item policy.
- Creative mode listing policy.
- Privacy and notification defaults.

Soulbound items, oversized NBT, nested containers, unsupported capability items, and active creative mode listing are denied by default.

## 9. Bazaar

Commands include the short and long Bazaar commands plus orders and claims routes.

### Screens

The Bazaar uses the same exact shared Nocturne shell and stable top navigation as `/shop` and the
Auction House. A theme choice applies to the entire marketplace so switching modules never changes
the navigation language or visual identity.

Tabs.

- Products.
- My Orders.
- Portfolio.
- Watchlist.
- Claims.
- History.

Product cards show item, instant buy, instant sell, spread, recent volume, trend, market status,
live buy and sell order counts, hourly trades, daily units, average price, and recent price history.
Player owned order and fill views resolve display names instead of exposing UUIDs as labels. UUIDs
remain available in identity tooltips for administrators and players who need exact diagnostics.

Product detail shows identity, order books, recent trades, quantity presets, price, total, fees, slippage, availability, payment source, average execution estimate, worst allowed execution, and claimable fills.

### Product definitions

Each product has stable ID, version, registry item, optional exact NBT identity, category, display overrides, enabled or retired state, lot size, price tick, quantity and price bounds, identity policy, dimension rules, and item restrictions.

Default commodity identity accepts tagless and undamaged stacks only. Named, damaged, enchanted, container, and capability backed variants require an explicit product definition.

Rich product definitions live under config slash futureshops slash bazaar slash products as JSON files. Bazaar TOML controls global market rules.

Tag discovery is opt in preview tooling rather than automatic exposure of every registry item.

### Orders

Each order stores order UUID, owner, product version, side, order type, time in force, state, revision, limit price, original quantity, remaining quantity, filled quantity, custody lots, maximum fee reserve, accrued fee and rounding remainder, accepted sequence, timestamps, and rule snapshot.

States include Holding, Matching, Open, Partially Filled, Filled, Cancel Pending, Cancelled, Expired, Frozen, Settled, and Manual Review.

### Order book and matching

Buy orders sort by price descending then accepted sequence. Sell orders sort by price ascending then accepted sequence. Canonical orders remain persisted and runtime indexes are rebuilt and validated.

A buy crosses when its limit is at least the best sell. A sell crosses when its limit is at most the best buy. Fill quantity is the smaller remainder. Default execution price is the resting maker price.

Every fill has its own UUID, order references, product version, quantity, price, gross, buyer fee, seller fee, price improvement, journal sequence, and escrow settlement.

The fill commits before public remaining quantities change.

### Buyer reserve

A limit buy reserves limit price multiplied by quantity plus maximum buyer fee. Lower execution price releases price improvement while retaining the amount needed for the remaining quantity.

Physical payment becomes escrow value at placement. Partial fill change defaults to wallet claim.

### Seller custody

Sell items enter a FutureShops vault before the order opens. Partial fills split exact held stacks. Cancelling returns only unfilled items. Committed fills never reverse.

### Order types

Initial release supports limit orders, instant buy, instant sell, Good Until Cancelled, and configurable expiration.

Immediate Or Cancel and Fill Or Kill follow after the core matching path. Market requests always include maximum spend or minimum proceeds and a slippage bound.

### Fees and rounding

Fees use basis points. Cumulative fee calculation or carried numerator remainder prevents tiny fill fragmentation from changing total fees.

Fees can route to a void sink, server treasury, or configured system account.

### Self trading and manipulation

Self trade policies are Cancel Taker, Cancel Maker, or Skip Self. Cancel Taker is the recommended default.

Controls include order and cancellation rate limits, open order limits, maximum notional and escrow value, optional minimum lifetime, price bands, global and product circuit breakers, emergency halt, abnormal price audit signals, and wash trade reporting.

A halt stops matching but never blocks cancellation or claims. Bazaar prices do not automatically control unrelated shop prices, taxes, or collateral.

### Market information and fun features

- Favorites and watchlists.
- Price alerts.
- Recent products.
- Lightweight sparklines.
- OHLCV history buckets.
- Daily and weekly volume.
- Spread and depth summaries.
- Estimated portfolio value.
- Fill and expiration notifications.
- Configurable market sounds.
- Public volume leaders.
- Treasury dashboard.
- Optional daily market bulletin.

## 10. Configuration layout

The main config is the single source of module enablement.

```toml
[modules]
bazaar_enabled = true
auction_house_enabled = true
show_module_navigation = true
default_module = "shop"
```

Escrow has no enabled toggle.

Files.

- futureshops common TOML.
- futureshops escrow TOML.
- futureshops auction house TOML.
- futureshops bazaar TOML.
- futureshops client TOML.
- Bazaar product JSON directory.
- Optional Auction House category JSON.

Dedicated specs are registered unconditionally so disabled modules can be configured before enabling.

Main config owns module toggles, navigation, economy, currency provider, and global compatibility.

Escrow config owns recovery work, retry timing, checkpoint interval, checkpoint journal byte and record thresholds, claim delivery, claim limits, retention, asset bounds, storage strictness, refund policy, audit detail, and administrative recovery rules.

Auction config owns types, durations, limits, increments, anti snipe, fees, taxes, payment policy, restrictions, cancellation, notifications, privacy, disable behavior, and branding.

Bazaar config owns order types, limits, fees, tick and lot defaults, expiration, self trade policy, fill budget, slippage, price bands, circuit breakers, history, payment policy, disable behavior, and branding.

Client config owns time format, density, themes, accessibility, animation, sound, remembered views, and confirmation preferences.

### Reload behavior

Safe immediate reload includes module state, labels, theme, notifications, new record limits, and bounded page sizes.

Rules such as fees, durations, increments, expiration, price tick, and anti snipe apply only to new contracts.

Command aliases, currency precision, product identity, persistent key formats, and journal formats require restart or migration.

Invalid reload keeps the last valid snapshot and reports exact rejected fields.

## 11. Module disable behavior

FREEZE is the recommended default.

- No new listing, bid, order, instant trade, or match.
- Existing custody remains.
- Auction time pauses when configured.
- Cancellation and refund remain available.
- Claims remain available.
- Browse may become read only or Claims Only.
- No data is deleted.
- Re enable resumes after recovery and validation.

Optional explicit administration modes are Freeze, Drain, and Cancel and Refund. Config reload does not silently trigger mass cancellation.

## 12. Networking

The existing strict SimpleChannel remains. New packet registrations are appended in stable order and registered regardless of module state. Protocol is bumped when the first wire changes land.

Packet groups.

- Market capabilities and module status.
- Escrow and claims.
- Auction queries and mutations.
- Bazaar queries, orders, and fills.
- Notifications.
- Administrative inspection.

Every mutation includes request UUID, expected revision, session or route nonce, and bounded input.

Every response includes request UUID, transaction UUID when created, result status, new revision, public config revision, and server timestamp.

Auction House and Bazaar use server pagination rather than complete data downloads. Stale response IDs are discarded. Search is debounced. Small invalidations refresh affected pages.

Hard bounds cover query length, page size, item list size, NBT bytes, labels, bid history, order book depth, chart points, and request rate.

## 13. Permissions and administration

Permission nodes cover Auction House use, creation, bidding, buying, claiming, and administration. Bazaar nodes cover use, orders, instant trades, claiming, and administration. Escrow nodes cover claiming and administration.

Use a permission integration when available with configurable operator level fallback.

Administrative functions include module status, freeze, resume, escrow inspect, retry, refund, auction cancel, Bazaar product halt, cancel all, and audit export.

Forced settlement or refund requires elevated permission, explicit confirmation, written reason, immutable audit record, and idempotent administration request ID.

Commands remain registered while modules are disabled.

## 14. Persistence and migration

New stores include escrow checkpoints, ledger data, Auction House canonical data, Bazaar canonical data, market controls, notifications, and aggregated history. Derived indexes rebuild from canonical records.

Migration sequence.

1. Back up the world.
2. Import wallet balances with deterministic migration IDs.
3. Import pending player shop settlement money as owner claims.
4. Preserve lifetime and rows as analytics only.
5. Resolve legacy dimensionless positions only when unambiguous.
6. Import mint records into authorized, available, and spent states.
7. Preserve legacy history without fabricated transaction IDs.
8. Seed durable catalog stock from configuration once.
9. Verify totals and conservation.
10. Write migration checksum and completion sequence.
11. Retain legacy files until the new checkpoint verifies.

Newer schema enters read only recovery mode.

Initial release supports one world directory and one server process. Cross server markets require an external transactional database and distributed locks.

## 15. Edge cases

- Duplicate packet returns original result.
- Timeout retry cannot charge twice.
- Disconnect, death, or dimension change preserves UUID transaction ownership.
- Full inventory and maximum wallet preserve claims.
- Concurrent last stock produces one winner and one safe refund.
- Equal Auction House bids preserve earlier sequence.
- Bid, expiration, and buy now races resolve under one listing lock.
- Order cancellation waits for the current fill boundary.
- Product retirement preserves active product versions.
- Module disable preserves custody.
- Config reload preserves contract snapshots.
- Clock rollback is clamped and forward jumps are processed in bounded batches.
- Missing item registry entries create quarantined claims.
- Oversized or malformed NBT is rejected before custody.
- Damaged or enchanted Bazaar items require explicit variants.
- Counterfeit and spent protected bills are rejected without credit.
- Currency provider changes invalidate stale quotes.
- Foreign creative deposits are blocked by default.
- Unrepresentable foreign amounts are rejected or explicitly converted to wallet remainder.
- External storage failure leaves post commit claims.
- Storage divergence enters reconciliation or manual review.
- Arithmetic overflow fails closed.
- Invalid event modified price is rejected.
- Listener exception after hold triggers recovery.
- Permission revocation blocks new actions but preserves resolution and claims.
- Offline or banned beneficiaries retain UUID claims.
- Shop destruction or owner change does not alter snapshotted beneficiary.
- Dimension aware identity prevents coordinate collision.
- Truncated journal tail loses only the incomplete record.
- Checksum mismatch and disk full stop value mutations.
- Backup lineage mismatch is detected.
- Repeated forced refund is idempotent.
- Claim All reports partial completion.
- Recovery can itself restart safely.
- Protected bills do not age out while held.
- Fee greater than gross is rejected.
- Tiny fills cannot evade fees through rounding.
- Self trading follows configured prevention.
- Stale interface revisions refresh rather than execute.

## 16. Community regression gate

The following behaviors are checked before escrow work and after every major interface or transaction phase.

- Player shop item detail has a clear Back arrow.
- X closes rather than acting as Back.
- Marketplace Profile and placed shop routes return correctly.
- Transaction History Back has an arrow.
- Funds is replaced with a context specific label.
- 12 hour time works in all modules.
- Add Barter Items exists.
- Add Barter Held does not lead to a dead end.
- Newly added barter entries open a functional editor.
- Barter Add opens the searchable picker.
- Multi select and quantity changes work.
- Tag and namespace search predicts partial mod names.
- Marketplace Profile has no redundant Storefront and Close behavior.
- Main Shop defaults to All.
- Cart changes have a visible badge or count.
- Purchase confirmation asks Wallet or Inventory Cash.
- Every shop purchase path preserves selected payment source.
- Foreign currency warning appears in generated TOML.
- ATM planning works for internal and external providers.
- Internal ATM minting registers protected bills.
- Foreign ATM minting does not claim internal protection.
- A retryable ATM deposit recovery can be resumed with the original request identity and never asks the player to create a new economic request.
- Pending ATM deposit recovery never blocks collection of an already committed cash claim.
- Closing, reopening, reconnecting, and restarting reconcile ATM recovery from server state without duplicating consumption or credit.
- Bazaar cancellation reports the exact unfilled refund amount, identifies Claims as its destination, refreshes the claim count, and provides a direct route to collection.

## 17. Testing strategy

Unit tests cover escrow transitions, idempotency, ledger conservation, overflow, mint states, serialization, partial claims, Auction House bidding and completion, Bazaar priority and matching, partial fills, cancellation, self trade, and fee rounding.

Property tests preserve the invariant that wallets, escrow, claims, protected physical outstanding, system accounts, and fee accounts equal starting supply plus explicit sources minus explicit sinks.

Random order streams prove that price limits hold, quantities stay nonnegative, priority remains deterministic, claims do not repeat, and fee fragmentation does not change totals.

Crash injection occurs before and after every journal append, wallet hold, inventory mutation, mint reservation, stock reservation, commit decision, claim creation, delivery, and recovery step.

Fault adapters test partial insertion, partial extraction, thrown exceptions, unload, slot reorder, simulation mismatch, network loss, and unsupported reconciliation.

Game tests cover dedicated server startup, real inventory, offline claims, player shop storage, protected money, foreign currency, config reload, full inventory, maximum wallet, restart, mod removal, dimensions, simultaneous bids, and high load matching.

Network tests cover round trips, protocol constant, invalid enums, oversized fields, duplicate request IDs, stale revisions, packet flood, delayed responses, and disabled modules.

Interface tests cover the regression gate, GUI scale 2, 3, and 4, wide and narrow layouts, keyboard use, long translations, both time formats, themes, accessibility, Back, X, Escape, resize, and stale response handling.

Performance tests cover thousands of auctions, tens of thousands of orders, large partial fill counts, claim queues, startup rebuild, recovery, search, pagination, and expiration spikes. Matching, expiry, and recovery use bounded work per tick.

ATM recovery tests inject failures before and after intent persistence, reservation, inventory removal, settlement, evidence cleanup, and recovery enqueue. Each test repeats the original request, reopens the ATM, reconnects, and restarts the server. The assertions prove that bills are consumed at most once, wallet value is credited at most once, exact terminal state is reported, stale evidence is durably removed only when it is proven safe, and unrelated cash claims remain collectible.

Bazaar cancellation tests cover unfilled and partially filled buy orders funded by wallet and Inventory Cash. The response, refreshed claim count, Claims route, exact pending amount, collection, maximum wallet handling, module disable behavior, reconnect, and restart must agree with the durable `BUYER_REFUND_CLAIM`.

## 18. Implementation phases

Each phase uses an envy branch created from the latest approved version branch. Each completed phase is tested, committed and pushed as EnVy, reviewed, fast forwarded after approval, and given a lightweight phase tag.

### Phase 0. Baseline and contracts

- Community regression acceptance tests.
- Escrow invariants and schemas.
- Module states and config ownership.
- Transaction and asset serialization fixtures.
- Performance scenarios.

### Phase 1. Escrow foundation

Branch. envy slash escrow foundation.

- Durable journal.
- Checkpoints and replay.
- Double entry ledger.
- Transaction repository.
- Claim center.
- Wallet and item custody.
- Protected and foreign currency custody.
- Recovery scheduler.
- Administration and maintenance mode.
- Conservation and crash tests.

### Phase 2. Existing flow migration

Branch. envy slash escrow existing flows.

Migration order.

1. ATM and withdrawal.
2. Deposit and right click deposit.
3. Pay.
4. Server shop buy and cart.
5. Server shop sell.
6. Server shop barter.
7. Player shop purchases.
8. Player shop barter and compound.
9. Player shop buyback.
10. Settlement claims.
11. Administrative balance changes.
12. Persistent stock reservations.

No normal value path may directly mutate a balance, mint ledger, stock, settlement, or trade inventory outside escrow when this phase completes.

### Phase 3. Shared market shell

Branch. envy slash market shell.

- Immutable market theme.
- Shared layout.
- Generic header, rail, toolbar, content, and footer.
- Scoped screens and sessions.
- Explicit market routes.
- Module capability packets.
- Green and red presets.
- Accessibility and client overrides.
- Existing Shop migration without behavior change.

### Phase 4. Auction House

Branch. envy slash auction house.

- Browse and search.
- Item custody and Buy Now.
- Claims and cancellation.
- Timed bidding.
- Outbid refunds.
- Expiration.
- Anti sniping.
- Watchlists and notifications.
- Moderation.
- History and analytics.

### Phase 5. Bazaar

Branch. envy slash bazaar.

- Product registry and versioning.
- Browse and order book.
- Sell and buy limit orders.
- Matching and partial fills.
- Cancellation and expiration.
- Claims and price improvement.
- Instant buy and sell.
- Immediate Or Cancel and Fill Or Kill.
- Fees and treasury.
- Circuit breakers.
- History, charts, alerts, and portfolio.

### Phase 6. Hardening and release

Branch. envy slash markets hardening.

- Legacy migrations.
- Corruption drills.
- Backup and restore guide.
- Disable and re enable drills.
- Fault adapter tests.
- Performance soak.
- Documentation and config examples.
- Permission examples.
- API events.
- Compatibility matrix.
- ATM deposit recovery and claim access release blocker from Section 20.
- Final complete regression pass.

## 19. Recommended defaults

- Escrow always required.
- Bazaar and Auction House independently enabled.
- Module disable behavior is Freeze.
- Claims and cancellation remain available.
- Auction House and Bazaar support wallet and physical payment.
- Wallet is default for long lived orders and bids.
- Physical partial fill remainder returns to wallet claim.
- Seller proceeds become wallet claims.
- First release market items come from player inventory.
- Third party storage waits for transaction receipts.
- Bazaar commodities are tagless and undamaged by default.
- NBT variants require explicit products.
- Seller self bidding is prohibited.
- Bazaar self trade policy is Cancel Taker.
- Bid retraction is disabled.
- Proxy bidding is initially disabled.
- Anti sniping is enabled.
- Auction time uses real time.
- Freeze pauses remaining auction time.
- Bazaar executes at maker price.
- Claims do not expire automatically.
- Initial release is single server.
- Shop, Player Shops, Bazaar, and Auction House share one blurple Nocturne schema. Optional client
  theme and accessibility overrides recolor the entire marketplace consistently.

These defaults maximize safety while retaining server and client customization. FutureShops does not claim control over foreign currency supply or arbitrary third party storage behavior that it cannot technically enforce.

## 20. Release blocker. ATM deposit recovery and refund access

Issue status. Fixed in the active 3.0 implementation. Live acceptance remains pending.

The pre fix audit reproduced the reported single player deposit failure from the former control flow. The ATM showed `RECOVERY_REQUIRED`, retained the original deposit request, and offered Retry deposit. The retry correctly reused the same request UUID, but a stored nonterminal transaction or active player evidence was classified as `RECOVERY_REQUIRED` again without a client visible reconciliation result. This could become an endless status loop even though creating a new request was correctly forbidden.

The former screen could display an already committed cash claim while making it unreachable. `AtmScreen` enabled Collect cash only while no deposit request was pending and while withdrawal mode was selected. It also prevented switching between Deposit and Withdrawal while that request was pending. A retryable deposit recovery could therefore disable Collect cash and prevent the player from entering the mode that would enable it. Back remained available, but reopening restored the static client tracker, so leaving the screen did not resolve the dead end.

The former recovery enqueue path contained a confirmed false success result. `ProtectedCashRedemptionWorkflow.enqueueIntentRecovery` caught a runtime failure from the runtime queue and returned `true`. `ForeignCashDepositWorkflow.enqueueIntentRecovery` had the same behavior. Their callers interpreted `true` as successfully queued recovery, so a failed enqueue could leave live evidence attached to the player while no recovery work was accepted. Future deposits then detected that evidence and returned `RECOVERY_REQUIRED`.

Bazaar cancellation was a related discoverability problem, not the same claim type as the ATM screenshot. Canceling an unfilled or partially filled buy order releases the remaining hold into a durable `BUYER_REFUND_CLAIM`. `BazaarEscrowLifecyclePlanner` stores it as a pending `ClaimKind.MONEY` claim labeled `Bazaar buyer refund`. This is a Claims balance, not an ATM physical cash claim, including when Inventory Cash first funded the order under the current refund policy. The former success text said that remaining value returned through Claims, but it did not report the exact amount, open Claims, or guarantee that the refreshed claim badge made the refund obvious. The screenshot's Collect cash count represents a separate cash claim and must not be presented as proof that the Bazaar refund is available there.

The implementation now preserves and synchronizes the exact request and transaction identity, uses explicit recovery enqueue results, performs bounded exact recovery checks, fails ambiguous evidence into manual review, keeps unrelated ATM navigation and cash claim collection available, and exposes `/marketadmin inspect <transactionId>`. Bazaar buy order cancellation reports the exact durable refund, refreshes capabilities, and opens Claims directly. Network protocol 52 carries the authoritative ATM recovery snapshot used after open and reconnect.

### 20.1 Server recovery contract

1. Preserve the original deposit request UUID and deterministic recovery transaction UUID. Retry, screen reopen, reconnect, and restart must inspect or resume that exact transaction. They must never create a replacement economic request.
2. Replace the boolean recovery enqueue result with an explicit result such as `QUEUED`, `NO_DURABLE_EVIDENCE`, or `FAILED`. A thrown enqueue operation can never be reported as queued.
3. Apply the same result contract to protected FutureShops bills and foreign currency deposits.
4. Distinguish a safe orphaned intent from evidence recorded after reservation or inventory mutation. An intent may be discarded only when the exact evidence matches, no reservation or transaction exists, and the live inventory still matches the recorded state before mutation.
5. Make safe orphan cleanup durable. Remove only the matching evidence and force the player data save before reporting that the recovery block is gone.
6. Never discard evidence after a reservation, physical removal, settlement decision, or ambiguous failure. Queue exact recovery or enter explicit manual review.
7. Make a recovery check actively inspect, enqueue, or resume the exact transaction before responding. Repeated checks must converge on a terminal result or an actionable manual review state instead of returning an undifferentiated recovery result forever.
8. Expose explicit server authoritative states for completed credit, refunded value, cash claim ready, recovery pending, manual review, and request conflict. Every terminal result includes the credited or returned amount and its destination.
9. Include active ATM recovery identity and state in the ATM data snapshot. Opening the ATM or reconnecting must adopt server state and clear stale client tracking after the server proves a terminal result.
10. Keep new deposits blocked while earlier evidence is genuinely nonterminal, but do not block unrelated claim collection, navigation, inspection, or administrative recovery.
11. Add an administrator inspection path keyed by the full recovery transaction UUID. It reports the request, player, currency provider, evidence stage, reservation, inventory mutation, settlement, claim, last recovery attempt, and safe next action without exposing unrelated player data.

### 20.2 ATM interface requirements

1. Replace Retry deposit with Check recovery or Resume recovery while the server owns a nonterminal recovery. The action reuses the original request and explains that it cannot charge or consume again.
2. Keep Deposit and Withdrawal navigation usable while recovery is pending. Disable only actions that would create a conflicting ATM value mutation.
3. Keep Collect cash available whenever a committed cash claim can be delivered and no cash collection request is already awaiting a response. Deposit recovery state alone cannot disable it.
4. When returned cash is ready, make Collect returned cash the primary recovery action and show the exact amount and claim count.
5. Wrap recovery messages inside the available panel. Show the full recovery handle and provide a copy action so the player can give an administrator an exact identifier.
6. On open, reopen, reconnect, and server restart, reconcile the local tracker against the server snapshot before enabling Deposit, Retry, or Check recovery.
7. After a terminal result, clear the pending tracker, refresh balance and claims, preserve a concise receipt, and return normal controls without requiring logout.

### 20.3 Bazaar refund requirements

1. A successful buy order cancellation response reports the exact unfilled refund amount and says that it is pending in Claims.
2. The client immediately refreshes capabilities, open claim count, and the Claims page after the durable cancel commit.
3. The success notice provides a direct View refund action. It must remain available when Bazaar is frozen or disabled because claims and cancellation are resolution actions.
4. The Claims row identifies `Bazaar buyer refund`, the order, the amount, the original payment source, and whether collection credited the wallet or left a remainder because of the maximum balance.
5. Wallet funded and Inventory Cash funded orders use the configured refund policy consistently. The interface must not imply that a money claim is collectible from the ATM as physical cash.
6. Partial fill cancellation reports only the remaining reserve. Filled quantity, fees, price improvement, and earlier claims remain separate and conserve exactly.

### 20.4 Required verification matrix

- Normal protected bill deposit in single player and on a dedicated server.
- Normal foreign currency deposit with every supported provider.
- Failure before and after intent persistence, reservation, inventory removal, settlement persistence, settlement commit, and evidence cleanup.
- Recovery queue rejection and thrown recovery enqueue for protected and foreign deposits.
- Safe orphan cleanup followed by an immediate player save and restart.
- Ambiguous post mutation evidence entering recovery or manual review without evidence deletion.
- Same request replay before and after every recovery state transition.
- Close and reopen, logout and reconnect, client restart, and server restart.
- Full inventory, maximum wallet, partial wallet capacity, and a cash claim ready during deposit recovery.
- Recovery started from Deposit mode and Withdrawal mode at every supported GUI scale.
- Corrupt, mismatched, and unknown evidence with a complete administrator recovery handle.
- Two rapid retry packets, delayed duplicate responses, packet loss, and request conflict.
- No double bill consumption, no double wallet credit, no duplicate claim, and no silent value loss.
- Bazaar cancel before any fill, after a partial fill, after full fill, and concurrent with a fill boundary.
- Bazaar refunds funded by wallet and Inventory Cash, collected normally and at maximum wallet balance.
- Bazaar disabled after order placement with cancellation and Claims still available.

### 20.5 Exact item delivery tick budget

Issue 23 demonstrates that the automatic claim delivery limit does not represent the real cost of exact item delivery. A purchase of about 2300 items produces about 36 exact stack claims. The current default can process 32 claims in one server tick, and every successful exact item delivery persists the player and forces the player data file before the claim is committed. This can place dozens of durable saves in one tick and reduce a healthy dedicated server to about 10 TPS until the delivery completes.

The repair is a maintenance extension of the existing bounded work and no value loss contracts.

1. Automatic exact item delivery must enforce a non bypassable durability operation budget per server tick. Existing configuration values from earlier 3.0 builds must not permit a burst of player saves after upgrade.
2. Delivery remains automatic and fair across online players and pending claims. Excess claims remain durable and pending for later ticks. The repair must not delete value, drop items into the world, merge unlike item data, or convert exact items into money.
3. A full inventory produces a pending claim without repeated durable writes. Manual claim collection remains available and uses the same exact item identity and replay protection.
4. Money claims and exact item claims share the configured scan budget. Every exact item delivery attempt consumes the durability operation budget because recovery can perform durable work even when the final result is not delivered. Failure and retry logging remains bounded.
5. The public configuration must describe the expensive operation being limited. Existing configurations automatically receive `claims.exact_item_delivery_operations_per_tick = 1`. The older general `claims.delivery_work_per_tick` setting remains compatible but cannot bypass the exact item durability limit.
6. Regression coverage must prove that a large pending claim set cannot execute more than the safe exact item delivery budget in one server tick, that work resumes on later eligible ticks, and that fairness rotation is preserved.
7. Verification requires the formatter, static checks, unit tests, relevant GameTests, data validation, the full Gradle build, dedicated server startup through FutureShops initialization, and a final artifact inspection. A live dedicated server acceptance run should confirm stable tick time while delivering more items than one inventory can hold.

Implementation status. The source repair adds a dedicated exact item operation limit of one per tick, applies it to immediate purchase and barter delivery, and preserves the larger general scan budget for money claims. Selection rotates across pending claims and online players. Focused regression tests, the complete unit suite, data generation, all five GameTests, packaged dependency checks, and the full build pass on Java 17. The GameTest server reaches FutureShops ready state and shuts down cleanly. Final acceptance still requires the reporter's Windows server and large purchase scenario after the issue 25 recovery repair is integrated.

This work is separate from the issue 25 startup recovery repair. The issue 23 log also shows escrow remaining in `RECOVERING`, which is addressed by the issue 25 beta artifact. Issue 23 remains open until both the recovery repair and this delivery budget repair are tested against the reporter's world and large purchase scenario.

### 20.6 Issue 23 ATM evidence and administrator recovery

The reporter's dedicated server trace proves that commit `b954755d8891` addresses only the large exact item delivery tick spike. ATM cash collection can still insert bills and then classify the same delivery as unknown because inventory evidence is based on byte serialization of the complete main inventory. Forge capability compounds may serialize in a different key order after an `ItemStack.copy()` or player data round trip, and an unrelated modded stack may normalize between the live inventory and the saved player file. The current ATM path also discards the custody rejection detail and one runtime exception, while maintenance mode has no command that invokes the implemented verified resume workflow.

The issue 23 recovery repair must preserve fail closed escrow behavior without producing false unknown outcomes.

1. New player inventory delivery evidence uses a deterministic semantic NBT encoding. Compound keys are ordered before hashing, list order and exact primitive values remain significant, and Forge capability data remains part of the item identity. The exact custody snapshot codec remains unchanged.
2. The insertion plan computes before and after evidence from the final defensive copies owned by the plan. It must not hash one object graph and later compare a second copy with potentially different serialization order.
3. Delivery confirmation uses the recorded changed slots as the authoritative before and after proof. Unrelated inventory slots must not invalidate a completed receipt or make an untouched plan appear applied. A changed slot that matches neither state remains unknown and enters quarantine.
4. Existing version one delivery tokens and receipts remain readable. Version two records identify the canonical hash contract. Legacy records use their original hash contract and the narrowest safe changed slot proof available. No migration may reinterpret an old digest with the new algorithm.
5. ATM collection, custody rejection, packet rejection, and entry into maintenance produce bounded actionable logs with the player, claim or request identity, stable error code, and recovery handle. Logs must not contain private inventory contents or unbounded NBT.
6. `/marketadmin maintenance status` reports runtime state, incident, recovery backlog, conservation verification, current revision, journal sequence, and verification fingerprint. `/marketadmin maintenance verify` performs a read only verification and prints the exact resume preconditions. `/marketadmin maintenance resume confirm <reason>` invokes the existing `VerifyAndResume` processor with the current revision and global verification snapshot. It requires escrow administrator permission, explicit confirmation, and an audit reason. A failed precondition leaves maintenance active and reports the blocking reason.
7. Recovery commands never delete claims, receipts, journal records, checkpoints, or custody evidence. They remain available while market modules are disabled because maintenance is an escrow runtime concern rather than a module navigation action.
8. Regression coverage must reproduce semantically equal capability compounds with different insertion order, defensive copy normalization, unrelated slot changes, changed slot ambiguity, legacy version one decoding, ATM exception diagnostics, and verified maintenance resume success and refusal.

### 20.7 Issue 23 admin shop catalog maintenance

Large admin shops need an operator controlled catalog ceiling and a recovery path when a removed mod leaves unknown item identifiers in `config/futureshops/shops/admin.json`.

1. `admin_shop.maximum_listings` is added to `futureshops-common.toml`. It defaults to 512 for compatibility and is bounded from 1 through 10000. Loading and admin editing enforce the configured value. The network protocol retains an independent hard ceiling of 10000 and a bounded payload size so a configuration increase cannot create an unbounded packet.
2. `/marketadmin adminshop validate` is read only. It validates every listing, reports the total error count, listing identifier, item identifier, field path, and stable validation code, and reports when the configured listing ceiling is exceeded. Output is paged or bounded so a catalog with thousands of failures cannot flood chat or logs.
3. `/marketadmin adminshop quarantine_missing confirm <reason>` moves listings containing missing registry items from the active `listings` array into a timestamped recovery file under `config/futureshops/shops/recovery/`, then atomically writes and reloads the valid catalog. The recovery file preserves the complete original listing JSON and source metadata. Existing backups remain intact.
4. The repair command requires escrow administrator permission, explicit confirmation, and an audit reason. It is idempotent when no missing item listings remain. Parse failures, duplicate identifiers, invalid NBT, and unrelated validation errors are reported but are not silently removed by the missing item repair.
5. The editor must identify the other invalid listing by stable listing identifier and validation path instead of only reporting that another listing is invalid. An operator can run the validation command, quarantine only missing item listings, and resume editing without manually searching thousands of entries.
6. Configuration reload rejects values outside the bound and preserves the last valid runtime setting. Increasing the configured ceiling does not rewrite an existing catalog and does not weaken per listing, component, NBT, or packet size validation.
7. Regression coverage must include the default 512 limit, a configured catalog above 512, the 10000 hard ceiling, a missing output, cost, icon, and sell input item, dry validation, confirmed quarantine, recovery file contents, reload success, repeat execution, path containment, and write or reload rollback.

This issue 23 maintenance extension targets `3.0.0-beta.10`. It remains isolated from concurrent issue work and must not merge or publish until the earlier beta phase is integrated. Completion requires focused tests, the complete unit suite, data validation, relevant GameTests, the full build, dedicated server and client startup, multiplayer and reconnect checks for ATM receipt proof, final JAR inspection, and the reporter's Windows dedicated server confirmation.

The source blockers described above are implemented. The focused issue 23 tests, complete unit suite, data generation, all five required GameTests including `atm_multiplayer`, packaged dependency checks, full build, final JAR inspection, and standalone dedicated server startup pass on Java 17. The final safety review also confirms that applying or restoring a delivery plan writes only its changed slots, so unrelated inventory changes are neither rejected nor overwritten. A fresh client smoke attempt prepared the Forge client run but the shared verification host exhausted memory before Minecraft started. Release approval still requires a successful client startup and the reporter's Windows dedicated server multiplayer, reconnect, restart, and fault drills from the matrix above.

## 21. Completion record

The record below describes the implemented 3.0 scope together with the completed Section 20 source fixes. The active phase remains a beta until the remaining live acceptance checks are complete.

The implementation covers the escrow foundation, migrated shop and currency flows, shared market
shell, independently configurable Auction House and Bazaar modules, wallet and inventory cash
funding, protected and foreign currency handling, claims, administrative recovery, permissions,
module lifecycle controls, analytics, alerts, API events, migrations, backup guidance, and bounded
performance tests described above.

Release verification includes the complete automated regression suite, packet round trips,
durability and replay fixtures, corruption and recovery drills, module disable and re enable tests,
exact item identity tests, administrative cancellation with standing bid refunds, and restore
soaks with 2000 auctions and 10000 Bazaar orders. The supported platform and integration boundaries
are documented in [docs/compatibility-matrix.md](docs/compatibility-matrix.md).

The post release market usability pass adds `bazaar_control` with admin and players modes. Admin
mode keeps the curated JSON product registry and reconciliation rules. Players mode preserves the
durable registry and provides a complete registered item browser without a per player catalog
quota. Search covers localized item names, registry identifiers, predictive mod filters, and
predictive item tag filters. A player can select an item they do not possess, create or reuse its
market, and fund a buy order entirely through escrow. Every sell still requires matching inventory
items to enter custody. Both modes retain escrow, versioning, circuit breakers, request rate limits,
and history.

Auction listing creation now presents the exact server listing fee before payment selection and
reconciles the result against the durable escrow commit before showing a recovery warning. The
shared market shell now calls the exact `/shop` header renderer for its logo, wordmark, Server Shop,
Player Shops, Bazaar and Auction House tabs, search, balance, profile, and close controls. All market
surfaces use one Nocturne theme, contain empty states inside the content region, and use responsive
detail panels for exact item data, seller identity and skin, bidder or buyer status, price, timing,
state, live supply, hourly activity, daily volume, average price, recent price graphs, and actions.

The market body now mirrors the Server Shop structure as well as its header. Bazaar and Auction
House routes use the shared segmented control for their local views and the shared Departments
panel for All and category rows, including market wide category counts and the same selection,
hover, scrolling, spacing, and accent treatment. Routine escrow ready banners are intentionally
hidden. Escrow remains mandatory, while a temporary readiness or recovery failure produces a
deduplicated chat warning and an explicit Retry control that resubmits only the safe read request
or invites the player to repeat an economic action only after the server confirms that nothing
changed.

The Auction House creation route now exposes Create Listing both in the route content and in the
footer, so the listing wizard remains discoverable even when the route has no cards. Auction detail
pages use the available space for exact item condition and data, listing type, bid count, buyout
distance, abbreviated identity, seller context, live activity, and a black chart surface. Recorded
price history is drawn with gold, green, and red movement segments. When recorded history is not
available, the chart is explicitly labeled as a bid ladder and displays only the current bid, next
bid, and buyout. Market prices and fees use a configurable currency symbol, with the dollar sign as
the default, and gold price text across cards, details, payment prompts, and creation fees.

The community navigation and presentation followup keeps parent navigation visible on both owner
and visitor player shop block screens while the close control still closes the complete interface.
The shared marketplace header uses the player skin for the compact profile control and identifies
Marketplace Profile and Transaction History on hover. Bazaar and Auction House breadcrumbs use the
same Marketplace path formatting as the Server Shop, and Back appears only on detail routes instead
of replaying ordinary local tab history. Auction creation owns a complete market workspace so
underlying department and toolbar surfaces do not show through it. ATM deposit safety guidance wraps
inside the available panel. The Bazaar footer identifies admin JSON catalog control or player catalog
control and explains where Browse Items is available. Admin item tag search matches both registry
namespaces and normalized Forge mod display names, including aliases such as Applied Energistics and
Macaw mod families.

The Player Shops skin followup keeps every rendered face bound to the same UUID and player name
shown by its card or selected shop block. Franchise cards resolve the franchise leader identity so
the displayed name and head cannot refer to different members, while offline owners retain their
last known block identity as a fallback.

The compatibility warning followup uses the FutureShops network protocol for the multiplayer server
list instead of requiring identical release labels. Compatible builds therefore remain joinable
without a false warning. A failed handshake now distinguishes a missing client installation, a
missing server installation, an older client protocol, an older server protocol, and an unknown
incompatible protocol. Each case explains the correct recovery action without assuming that the
client always needs an update.

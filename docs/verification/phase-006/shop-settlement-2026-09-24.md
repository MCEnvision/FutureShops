# Phase 006 shop settlement evidence

## Candidate

The Forge 1.20.1 candidate is commit `a34c77c` on the phase 006 branch. The build uses Java 17 and retains the Forge beta 2 artifact identity because beta 3 release metadata is owned by the final release phase.

The packaged jar is `build/libs/futureshops-3.0.0-beta.2.jar`.

SHA 256 is `e15da7748134af51539e136d94e3417e0a655ea387dbc3af3fde16376eb65e55`.

SHA 512 is `0e8b7bcbd464599df4d9aa66a0d1f74e809935acbbc1f1d60b84a98bce5fb6d57d19d9a63042e9e1205d146743ced72e3ce469efc72cca114c33f47ae73313fe`.

## Settlement boundary

| route | internal provider | external or unavailable provider |
| --- | --- | --- |
| legacy server shop buy | existing escrow purchase path | refused by the selected provider gate before fresh escrow admission |
| legacy server shop sell | existing item custody and payout path | refused before fresh preflight and item extraction |
| normalized offers | money, free, barter, bundles, and mixed internal routes retain stable request and offer identities | money legs refuse before stock or item custody. free and pure barter do not require a monetary provider |
| offer carts | internal cart fanout and exact line reservation | monetary carts refuse before preparation or stock reservation |
| server shop sellall | internal quote and per line payout | quote and commit refuse before inventory extraction |
| player shop and buyback | existing player shop escrow, storage custody, offline proceeds, and original claims | live escrow rejects any monetary transfer. existing internal claims remain readable |

The live escrow guard now checks both the declared payment source and the intent money transfer list. This closes the server shop sell payout path, which previously used an admin mint transfer with no payment source and could reach internal ledger settlement after an external provider was selected. Prepared offer and cart recovery remains readable, but new external monetary effects cannot be admitted.

## Verification

```text
./gradlew test --no-daemon --max-workers=1
BUILD SUCCESSFUL
2008 tests completed, 0 failed

./gradlew build --no-daemon --max-workers=1
BUILD SUCCESSFUL
15 actionable tasks completed or up to date

git diff --check
clean
```

A focused `ServerShopSellServiceTest` run also passed after the live backend gate was added. The source regression suite asserts all covered shop money boundaries and the no custody ordering.

A disposable Forge dedicated server was launched on node 1 with Java 17 in `phase-006-server-runtime`, using port 25575 because the pre existing development runtime occupied 25565. The server reached `Done (13.763s)`, loaded FutureShops, wrote the admin catalog, loaded the Bazaar catalog, and reached the normal FutureShops server starting state. It was stopped through its owned process and the runtime was removed after the final log consumer. No client or audio stream was started.

The first smoke attempt was rejected only because port 25565 was already occupied. It was not used as product evidence, and the owned retry on port 25575 passed startup. The Gradle task exit after the bounded server stop was 143, which is expected for the intentional process termination and does not change the successful readiness observation.

## Scope and remaining boundary

This phase does not add an unsafe external multi account conversion. API v1 exposes single account mutations but no atomic multi account transfer, so external shop money remains explicitly refused. Bazaar and Auction House lifecycle work remains owned by CORE-PHASE-007. The next phase consumes this route evidence and extends the same coordinator boundary to market holds and claims.


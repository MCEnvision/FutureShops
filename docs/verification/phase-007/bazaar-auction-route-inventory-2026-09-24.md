# Phase 007 Bazaar and Auction House route inventory

Date: 2026-09-24

Branch: `envy/3.0.0-beta.3-phase-007`

Base: `a7863b86ce9b9b8d32f921578d6255832a16aa6d`

Scope: CORE-REQ-013 and P007-TASK-001 entry work on Forge 1.20.1 with Java 17.

## Route inventory

| family | entrypoint | monetary effects | custody owner | current settlement boundary |
| --- | --- | --- | --- | --- |
| Bazaar | `C2SBazaarOrderPacket` to `BazaarActionService.order` | wallet reserve, physical cash funding, fills, price improvement, proceeds, fees | `BazaarSellItemCustody` and the Bazaar lifecycle WAL | internal provider only, durable lifecycle prepare and commit |
| Bazaar | `C2SBazaarCancelPacket` to `BazaarActionService.cancel` | unfilled reserve or item remainder refund | Bazaar lifecycle WAL and claims | replay first, then internal provider admission |
| Bazaar | `BazaarExpirationScheduler` | expiration refunds and prepared create recovery | Bazaar lifecycle WAL and exact item runtime | scheduler pauses before recovery or expiry when provider is not admitted |
| Auction | `C2SAuctionCreatePacket` to `AuctionActionService.create` | listing fee and listing custody | `AuctionEscrowItemCustody` and the Auction lifecycle WAL | internal provider only, durable lifecycle prepare and commit |
| Auction | `C2SAuctionBidPacket` to `AuctionActionService.bid` | bid hold and displaced bidder refund | Auction lifecycle WAL and claims | internal provider only, replay first |
| Auction | `C2SAuctionBuyNowPacket` to `AuctionActionService.buyNow` | buyout hold, outbid refund, seller proceeds | Auction lifecycle WAL and exact item custody | internal provider only, replay first |
| Auction | `C2SAuctionCancelPacket` to `AuctionActionService.cancel` | listing remainder and bid refund | Auction lifecycle WAL and claims | replay first, then internal provider admission |
| Auction | `AuctionExpirationScheduler` | expiry refund or sold settlement | Auction lifecycle WAL and exact item custody | scheduler pauses before recovery or settlement when provider is not admitted |
| Claims | market and escrow claim delivery | refund, proceed, fee, and cash claims | claim saved data and escrow ledger | replay is checked before provider admission, then external mode refuses delivery |

The order book and auction book remain the authoritative owners of matching, price and quantity
rules, revisions, cancellation eligibility, expiry, and terminal state. The action services do not
move value directly. They prepare the market lifecycle and commit through `EscrowRuntimeService`,
which owns the Forge WAL and exact item runtime.

## Provider boundary

Forge API v1 exposes single account withdraw and deposit operations. It does not prove an atomic
multi account transfer contract for market holds, proceeds, refunds, and fees. No market code may
turn that API into an internal shadow wallet or infer success from a balance query. The active beta
therefore admits the internal durable wallet only. An external provider receives a typed economy
unavailable result before market or item mutation. Pure browsing and non monetary inspection stay
available, while existing liabilities remain readable and retain their original local facts.

The admission is deliberately after request replay checks. A duplicate request with a stored
terminal response returns that response even if the provider selection changed after the original
operation. A new request, cancellation, scheduler recovery, or claim collection does not mutate
the internal ledger while an external provider is selected.

## Changes in this task

* Centralized the market money admission in `MarketSettlementPolicy`.
* Added provider admission after replay to Bazaar cancellation and Auction cancellation.
* Paused Bazaar and Auction recovery and expiry sweeps before any lifecycle commit when the
  provider is not admitted.
* Moved monetary claim provider admission after replay so a previously recorded result cannot be
  hidden by a later provider switch.
* Made market opening and capability projection fail closed for external providers without
  throwing while reading the wallet balance. Claims remain available through the claims only
  projection state.

## Verification

The focused regression suite passed with `./gradlew test --tests
com.enviouse.futureshops.PaymentSourceRegressionTest`. The related market capability and claim
tests also passed. The complete Forge suite and packaging build passed with 2,010 tests, zero
failures, and zero errors. The packaged candidate was `build/libs/futureshops-3.0.0-beta.2.jar`
with SHA 256 `93dcc528e0c15defb562d98617fef921826e7ddbf3b4a4770760990a6b787991`.

The route guards, replay ordering, scheduler admission, and external capability projection were
verified. The dedicated server market matrix remains the later P007 task 004 gate.

No client or graphical process was started for this task. No temporary server runtime, world,
network listener, or audio stream was created.

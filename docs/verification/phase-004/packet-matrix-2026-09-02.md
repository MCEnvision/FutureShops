# Phase 004 packet matrix

Forge revision `254a8788aa9a1d2f228abd84665882de5b69c075` uses the versioned Forge channel protocol `58`. NeoForge revision `51cc7c1831079c12a6d6070bd16873e9fbcad01b` uses its independent NeoForge payload registration. The two lines are not interchangeable.

## Forge client to server packets

The 55 Forge C2S packets are: `C2SAdminBulkCommitPacket`, `C2SAdminBulkPreviewPacket`, `C2SAdminOfferSavePacket`, `C2SAdminShopAddItemsPacket`, `C2SAdminShopEditPacket`, `C2SAtmCollectCashPacket`, `C2SAtmDepositPacket`, `C2SAtmDepositRecoveryPacket`, `C2SAtmWithdrawPacket`, `C2SAuctionBidPacket`, `C2SAuctionBuyNowPacket`, `C2SAuctionCancelPacket`, `C2SAuctionCreatePacket`, `C2SBarterRequestPacket`, `C2SBazaarCancelPacket`, `C2SBazaarOrderPacket`, `C2SBazaarRegisterProductPacket`, `C2SBulkSellCancelPacket`, `C2SBulkSellCommitPacket`, `C2SBulkSellQuotePacket`, `C2SBuyRequestPacket`, `C2SCloseMarketSessionPacket`, `C2SFetchDepartmentsPacket`, `C2SFetchHistoryPacket`, `C2SFetchLocalShopsPacket`, `C2SFetchSettlementHistoryPacket`, `C2SFranchiseActionPacket`, `C2SInventorySyncPacket`, `C2SMarketCapabilitiesPacket`, `C2SMarketClaimCollectionPacket`, `C2SMarketPageQueryPacket`, `C2SMarketProfileMutationPacket`, `C2SOpenAtmPacket`, `C2SOpenBalTopUiPacket`, `C2SOpenBalanceUiPacket`, `C2SOpenMarketModulePacket`, `C2SOpenShopPacket`, `C2SPlayerShopActionPacket`, `C2SPlayerShopBuyPacket`, `C2SPlayerShopBuybackConfigPacket`, `C2SPlayerShopConfigPacket`, `C2SPlayerShopIconPacket`, `C2SPlayerShopOfferPacket`, `C2SPlayerShopOfferSavePacket`, `C2SPlayerShopPromoPacket`, `C2SPlayerShopSellPacket`, `C2SPlayerShopSettlementClaimPacket`, `C2SPlayerShopUnlinkStoragePacket`, `C2SSellRequestPacket`, `C2SServerShopOfferCartPacket`, `C2SServerShopOfferPacket`, `C2SSetDepartmentPacket`, `C2SVerifyAdminCartPacket`, and `C2SVerifyCartPacket`.

Every decoder now gives text fields an explicit maximum. Identifiers and labels are bounded between 16 and 256 characters according to their protocol role. Exact NBT fields are bounded at 65,536 characters. Collections and quantities retain the existing domain limits. A source scan regression rejects any literal unbounded `readUtf()` in a Forge C2S class.

Every handler validates the active `ServerPlayer`, enqueues to the main server thread, checks route, session, permission, ownership, registry identity, readiness, revision, request identity and replay state where applicable, then calls the authoritative service. A malformed, oversized, wrong direction, stale or unauthorized packet must not mutate state.

## NeoForge differential review

The NeoForge line has 21 C2S and 16 S2C packet classes. Its 17 text decoding sites were independently identified as the same risk class and are tracked in issue [#41](https://github.com/MCEnvision/FutureShops/issues/41) for a separate line specific repair. No Forge packet source was copied into NeoForge, and no clean NeoForge result is claimed until its own bounds tests, build and runtime checks pass.

## Server to client privacy

S2C responses bind to the requesting player or the explicitly authorized recipient. Balances, claims, history, inventories, NBT and operator context are minimized and bounded. Client snapshots are presentation only. The server rechecks all authority and value before every mutation.

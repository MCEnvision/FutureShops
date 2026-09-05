package com.enviouse.futureshopsp.gametest;

import com.enviouse.futureshopsp.Futureshops;
import com.enviouse.futureshopsp.api.ShopModAPI;
import com.enviouse.futureshopsp.api.economy.MutationKind;
import com.enviouse.futureshopsp.api.economy.BalanceSnapshot;
import com.enviouse.futureshopsp.api.economy.CurrencyMetadata;
import com.enviouse.futureshopsp.api.economy.EconomyProvider;
import com.enviouse.futureshopsp.api.economy.MutationRequest;
import com.enviouse.futureshopsp.api.economy.MutationReceipt;
import com.enviouse.futureshopsp.api.economy.ProviderCapabilities;
import com.enviouse.futureshopsp.api.economy.ProviderError;
import com.enviouse.futureshopsp.api.economy.ProviderLifecycle;
import com.enviouse.futureshopsp.api.economy.ProviderReadiness;
import com.enviouse.futureshopsp.api.economy.ProviderResult;
import com.enviouse.futureshopsp.api.economy.ProviderResultStatus;
import com.enviouse.futureshopsp.api.economy.RequestId;
import com.enviouse.futureshopsp.compat.danconomy.DanConomyEconomyProvider;
import com.enviouse.futureshopsp.compat.danconomy.DanConomyEconomyProviderRegistration;
import com.enviouse.futureshopsp.compat.danconomy.DanConomyLedgerAccess;
import com.enviouse.futureshopsp.block.ShopBlockEntity;
import com.enviouse.futureshopsp.catalog.ItemDef;
import com.enviouse.futureshopsp.catalog.ShopCatalog;
import com.enviouse.futureshopsp.init.ModBlocks;
import com.enviouse.futureshopsp.init.ModItems;
import com.enviouse.futureshopsp.network.packets.C2SBuyRequestPacket;
import com.enviouse.futureshopsp.network.packets.C2SSellRequestPacket;
import com.enviouse.futureshopsp.server.economy.BalanceManager;
import com.enviouse.futureshopsp.server.economy.ClaimState;
import com.enviouse.futureshopsp.server.economy.EconomyLifecycleController;
import com.enviouse.futureshopsp.server.economy.EconomyTransactionCoordinator;
import com.enviouse.futureshopsp.server.economy.FileEconomyReceiptAuditJournal;
import com.enviouse.futureshopsp.server.economy.InMemoryEconomyClaimStore;
import com.enviouse.futureshopsp.server.economy.InMemoryEconomyCustodyStore;
import com.enviouse.futureshopsp.server.economy.InMemoryEconomyTransactionJournal;
import com.enviouse.futureshopsp.server.debug.DebugDiagnostics;
import com.enviouse.futureshopsp.server.debug.DebugModule;
import com.enviouse.futureshopsp.server.session.ShopSessionManager;
import com.enviouse.futureshopsp.server.shop.PlayerShopBlockService;
import com.enviouse.futureshopsp.server.transaction.ShopBuyService;
import com.enviouse.futureshopsp.server.transaction.ShopSellService;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforge.common.IOUtilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import org.slf4j.Logger;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

@GameTestHolder(Futureshops.MODID)
@PrefixGameTestTemplate(false)
public final class DanConomyGameTests {
    private static final UUID FIXTURE_PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000321");
    private static final UUID RESTART_PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000324");
    private static final UUID ROUTE_PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000325");
    private static final UUID DEBUG_PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000326");
    private static final RequestId RESTART_REQUEST =
            new RequestId(UUID.fromString("00000000-0000-0000-0000-000000000322"));
    private static final String CRASH_POINT_PROPERTY = "futureshops.danconomy.crash.point";
    private static final long CRASH_AMOUNT = 17L;
    private static final Logger LOGGER = LogUtils.getLogger();

    private DanConomyGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void exactLedgerMutationAndOrdinaryCall(GameTestHelper helper) {
        if (!danconomyReady() || crashPoint() != null) {
            helper.succeed();
            return;
        }
        try {
            DanConomyEconomyProvider provider = new DanConomyEconomyProvider(helper.getLevel().getServer());
            helper.assertTrue(provider.readiness().lifecycle() == ProviderLifecycle.READY,
                    "the exact DanConomy ledger provider must be ready");
            long before = provider.balance(FIXTURE_PLAYER).value().orElseThrow().balanceMinorUnits();
            helper.assertTrue(before >= 25L, "the DanConomy fixture balance must cover the debit");
            MutationRequest request = MutationRequest.forPlayer(RequestId.random(), FIXTURE_PLAYER,
                    25L, MutationKind.WITHDRAW);

            var mutation = provider.withdraw(request);
            helper.assertTrue(mutation.confirmed(),
                    "the exact DanConomy ledger mutation must confirm after durable replacement");
            var duplicate = provider.retry(request);
            helper.assertTrue(duplicate.confirmed() && duplicate.receipt().equals(mutation.receipt()),
                    "the exact DanConomy request must deduplicate by request identity");
            MutationRequest conflict = MutationRequest.forPlayer(request.requestId(), FIXTURE_PLAYER,
                    26L, MutationKind.WITHDRAW);
            helper.assertTrue(provider.retry(conflict).error() == ProviderError.INVALID_REQUEST,
                    "a conflicting DanConomy request reuse must be rejected");
            MutationRequest actorConflict = MutationRequest.forPlayer(request.requestId(), RESTART_PLAYER,
                    25L, MutationKind.WITHDRAW);
            helper.assertTrue(provider.retry(actorConflict).error() == ProviderError.INVALID_REQUEST,
                    "a DanConomy request reused for another account must be rejected");
            MutationRequest kindConflict = MutationRequest.forPlayer(request.requestId(), FIXTURE_PLAYER,
                    25L, MutationKind.DEPOSIT);
            helper.assertTrue(provider.retry(kindConflict).error() == ProviderError.INVALID_REQUEST,
                    "a DanConomy request reused for another mutation kind must be rejected");
            helper.assertTrue(provider.balance(FIXTURE_PLAYER).value().orElseThrow().balanceMinorUnits() == before - 25L,
                    "the exact DanConomy debit must occur once");
            CompoundTag persisted = readLedger(helper);
            helper.assertTrue(hasReceipt(persisted, request.requestId()),
                    "the DanConomy request receipt must share the durable ledger image");
            CompoundTag persistedReceipt = findReceipt(persisted, request.requestId());
            helper.assertTrue(persistedReceipt.getUUID("account_id").equals(FIXTURE_PLAYER)
                            && persistedReceipt.getString("currency_id").equals("dollar")
                            && persistedReceipt.getString("kind").equals(MutationKind.WITHDRAW.name())
                            && persistedReceipt.getLong("amount_minor_units") == 25L
                            && persistedReceipt.getLong("resulting_balance_minor_units") == before - 25L
                            && persistedReceipt.getString("checksum").length() == 64,
                    "the durable DanConomy receipt must bind every immutable request field");
            int receiptCount = receiptCount(persisted);

            invokeOrdinaryDeposit(helper, FIXTURE_PLAYER, 7L);
            invokeOrdinaryWithdraw(helper, FIXTURE_PLAYER, 3L);
            helper.getLevel().getDataStorage().save();
            IOUtilities.waitUntilIOWorkerComplete();
            helper.assertTrue(provider.balance(FIXTURE_PLAYER).value().orElseThrow().balanceMinorUnits() == before - 21L,
                    "ordinary DanConomy mutation behavior must remain unchanged");
            helper.assertTrue(receiptCount(readLedger(helper)) == receiptCount,
                    "ordinary DanConomy calls must not mint FutureShops receipts");

            Object corrupted = loadLedger(helper, corruptedReceiptData(persisted));
            helper.assertTrue(corrupted instanceof DanConomyLedgerAccess,
                    "the exact DanConomy ledger must retain its request aware mixin");
            var corruptLookup = ((DanConomyLedgerAccess) corrupted)
                    .futureshopsLookup(helper.getLevel(), request.requestId());
            helper.assertTrue(corruptLookup.status() == ProviderResultStatus.RECOVERY_REQUIRED,
                    "unknown DanConomy receipt data must require recovery");
            Object checksumCorrupted = loadLedger(helper, corruptedChecksumData(persisted, request.requestId()));
            var checksumLookup = ((DanConomyLedgerAccess) checksumCorrupted)
                    .futureshopsLookup(helper.getLevel(), request.requestId());
            helper.assertTrue(checksumLookup.status() == ProviderResultStatus.RECOVERY_REQUIRED,
                    "a checksum invalid DanConomy receipt must require recovery");
            LOGGER.info("futureshops.danconomy.gametest mutation confirmed request={} duplicate={} conflict={} receipt_count={} ordinary_balance={} unknown={} checksum={}",
                    request.requestId().value(), duplicate.status(), provider.retry(conflict).error(), receiptCount,
                    before - 21L, corruptLookup.status(), checksumLookup.status());
        } catch (ReflectiveOperationException | java.io.IOException | RuntimeException exception) {
            helper.fail("the exact DanConomy ledger probe failed: " + exception.getClass().getSimpleName());
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void exactLedgerMutationKindsAndThreading(GameTestHelper helper) {
        if (!danconomyReady() || crashPoint() != null) {
            helper.succeed();
            return;
        }
        DanConomyEconomyProvider provider = new DanConomyEconomyProvider(helper.getLevel().getServer());
        long baseline = provider.balance(ROUTE_PLAYER).value().orElseThrow().balanceMinorUnits();
        AtomicReference<ProviderResult<BalanceSnapshot>> offThreadBalance = new AtomicReference<>();
        AtomicReference<ProviderResult<MutationReceipt>> offThreadMutation = new AtomicReference<>();
        Thread probe = new Thread(() -> {
            offThreadBalance.set(provider.balance(ROUTE_PLAYER));
            offThreadMutation.set(provider.deposit(MutationRequest.forPlayer(RequestId.random(), ROUTE_PLAYER,
                    1L, MutationKind.DEPOSIT)));
        }, "futureshops-danconomy-thread-probe");
        probe.start();
        try {
            probe.join(5_000L);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            helper.fail("the DanConomy thread probe was interrupted");
            return;
        }
        helper.assertTrue(!probe.isAlive(), "the DanConomy thread probe must finish");
        helper.assertTrue(offThreadBalance.get().error() == ProviderError.NOT_READY
                        && offThreadMutation.get().error() == ProviderError.NOT_READY,
                "off thread DanConomy access must refuse before saved data access");

        ProviderResult<MutationReceipt> withdraw = provider.withdraw(request(ROUTE_PLAYER, 10L,
                MutationKind.WITHDRAW));
        ProviderResult<MutationReceipt> deposit = provider.deposit(request(ROUTE_PLAYER, 5L,
                MutationKind.DEPOSIT));
        ProviderResult<MutationReceipt> transferDebit = provider.withdraw(request(ROUTE_PLAYER, 7L,
                MutationKind.TRANSFER_DEBIT));
        ProviderResult<MutationReceipt> transferCredit = provider.deposit(request(ROUTE_PLAYER, 11L,
                MutationKind.TRANSFER_CREDIT));
        ProviderResult<MutationReceipt> fee = provider.withdraw(request(ROUTE_PLAYER, 3L, MutationKind.FEE));
        ProviderResult<MutationReceipt> refund = provider.deposit(request(ROUTE_PLAYER, 2L, MutationKind.REFUND));
        ProviderResult<MutationReceipt> compensation = provider.deposit(request(ROUTE_PLAYER, 4L,
                MutationKind.COMPENSATION));
        helper.assertTrue(withdraw.confirmed() && deposit.confirmed() && transferDebit.confirmed()
                        && transferCredit.confirmed() && fee.confirmed() && refund.confirmed()
                        && compensation.confirmed(),
                "every supported DanConomy mutation kind must confirm durably");
        helper.assertTrue(provider.balance(ROUTE_PLAYER).value().orElseThrow().balanceMinorUnits() == baseline + 2L,
                "the DanConomy mutation kind matrix must conserve the exact balance delta");

        ProviderResult<MutationReceipt> overflow = provider.deposit(request(ROUTE_PLAYER, Long.MAX_VALUE,
                MutationKind.DEPOSIT));
        helper.assertTrue(overflow.error() == ProviderError.INVALID_AMOUNT,
                "an overflowing DanConomy amount must be rejected without mutation");
        helper.assertTrue(provider.balance(ROUTE_PLAYER).value().orElseThrow().balanceMinorUnits() == baseline + 2L,
                "rejected DanConomy amounts must leave the balance unchanged");
        LOGGER.info("futureshops.danconomy.gametest routes balance={} off_thread={} overflow={}",
                baseline + 2L, offThreadMutation.get().error(), overflow.error());
        helper.succeed();
    }

    private static MutationRequest request(UUID player, long amount, MutationKind kind) {
        return MutationRequest.forPlayer(RequestId.random(), player, amount, kind);
    }

    @SuppressWarnings("removal")
    private static ServerPlayer mockPlayer(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        NetworkRegistry.configureMockConnection(player.connection.getConnection());
        return player;
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void exactLedgerCoordinatorSurface(GameTestHelper helper) {
        if (!danconomyReady() || crashPoint() != null) {
            helper.succeed();
            return;
        }
        UUID player = UUID.fromString("00000000-0000-0000-0000-000000000323");
        long before = BalanceManager.queryBalance(player).value().orElseThrow().balanceMinorUnits();
        MutationRequest request = MutationRequest.forPlayer(RequestId.random(), player, 30L, MutationKind.DEPOSIT);
        var result = BalanceManager.getCoordinator().deposit(request);
        helper.assertTrue(result.confirmed(), "the coordinator must confirm a DanConomy deposit");
        helper.assertTrue(BalanceManager.queryBalance(player).value().orElseThrow().balanceMinorUnits() == before + 30L,
                "the coordinator must expose the exact DanConomy resulting balance");
        helper.assertTrue(hasReceipt(readLedgerUnchecked(helper), request.requestId()),
                "the coordinator request must have an authoritative DanConomy receipt");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void exactLedgerServerShopSellSurface(GameTestHelper helper) {
        if (!danconomyReady() || crashPoint() != null) {
            helper.succeed();
            return;
        }
        ItemDef item = ShopCatalog.getItem("default", "minecraft:iron_ingot").orElse(null);
        if (item == null || item.sellPriceMinorUnits() <= 0L) {
            item = ShopCatalog.getItem("default", "minecraft:diamond").orElse(null);
        }
        helper.assertTrue(item != null && item.sellPriceMinorUnits() > 0L,
                "the DanConomy catalog must expose one positive sell listing");
        Item resolved = BuiltInRegistries.ITEM.get(ResourceLocation.parse(item.itemId()));
        helper.assertTrue(resolved != null && resolved != Items.AIR,
                "the DanConomy sell listing must resolve to a registered item");

        ServerPlayer player = mockPlayer(helper);
        player.getInventory().add(new ItemStack(resolved, 1));
        int itemsBefore = player.getInventory().countItem(resolved);
        long balanceBefore = BalanceManager.queryBalance(player.getUUID()).value().orElseThrow().balanceMinorUnits();
        int stockBefore = ShopCatalog.getCurrentStock("default", item.resolutionKey());
        ShopSessionManager.open(player.getUUID(), "default");
        try {
            ShopSellService.handleSellRequest(player,
                    new C2SSellRequestPacket("default", item.resolutionKey(), 1));
        } finally {
            ShopSessionManager.close(player.getUUID());
        }

        long balanceAfter = BalanceManager.queryBalance(player.getUUID()).value().orElseThrow().balanceMinorUnits();
        helper.assertTrue(player.getInventory().countItem(resolved) == itemsBefore - 1,
                "the DanConomy server shop sell must remove the sold item after confirmation");
        helper.assertTrue(balanceAfter == balanceBefore + item.sellPriceMinorUnits(),
                "the DanConomy server shop sell must credit the exact amount");
        helper.assertTrue(ShopCatalog.getCurrentStock("default", item.resolutionKey()) == stockBefore + 1,
                "the DanConomy server shop sell must update stock after confirmation");
        helper.assertTrue(!BalanceManager.getCustodyStore().hasIncompleteRecords(),
                "the DanConomy server shop sell must release custody");
        LOGGER.info("futureshops.danconomy.gametest surface=server_shop_sell status=CONFIRMED amount={} balance_delta={} item_delta={} stock_delta={}",
                item.sellPriceMinorUnits(), balanceAfter - balanceBefore,
                player.getInventory().countItem(resolved) - itemsBefore,
                ShopCatalog.getCurrentStock("default", item.resolutionKey()) - stockBefore);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void exactLedgerServerShopBuySurface(GameTestHelper helper) {
        if (!danconomyReady() || crashPoint() != null) {
            helper.succeed();
            return;
        }
        ItemDef item = ShopCatalog.getItem("default", "minecraft:diamond").orElse(null);
        helper.assertTrue(item != null && item.buyPriceMinorUnits() > 0L,
                "the DanConomy catalog must expose one positive buy listing");
        Item resolved = BuiltInRegistries.ITEM.get(ResourceLocation.parse(item.itemId()));
        helper.assertTrue(resolved != null && resolved != Items.AIR,
                "the DanConomy buy listing must resolve to a registered item");

        ServerPlayer buyer = mockPlayer(helper);
        helper.assertTrue(BalanceManager.deposit(buyer.getUUID(), item.buyPriceMinorUnits() + 100L).success(),
                "the DanConomy buy fixture must seed sufficient funds");
        long balanceBefore = BalanceManager.queryBalance(buyer.getUUID()).value().orElseThrow().balanceMinorUnits();
        int itemsBefore = buyer.getInventory().countItem(resolved);
        ShopSessionManager.open(buyer.getUUID(), "default");
        try {
            ShopBuyService.handleBuyRequest(buyer,
                    C2SBuyRequestPacket.single("default", item.resolutionKey(), 1));
        } finally {
            ShopSessionManager.close(buyer.getUUID());
        }

        long balanceAfter = BalanceManager.queryBalance(buyer.getUUID()).value().orElseThrow().balanceMinorUnits();
        helper.assertTrue(buyer.getInventory().countItem(resolved) == itemsBefore + 1,
                "the DanConomy server shop buy must deliver the item after confirmation");
        helper.assertTrue(balanceAfter == balanceBefore - item.buyPriceMinorUnits(),
                "the DanConomy server shop buy must debit the exact amount");
        helper.assertTrue(!BalanceManager.getCustodyStore().hasIncompleteRecords(),
                "the DanConomy server shop buy must claim custody");
        LOGGER.info("futureshops.danconomy.gametest surface=server_shop_buy status=CONFIRMED amount={} balance_delta={} item_delta={}",
                item.buyPriceMinorUnits(), balanceAfter - balanceBefore,
                buyer.getInventory().countItem(resolved) - itemsBefore);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void exactLedgerPlayerShopBuySurface(GameTestHelper helper) {
        if (!danconomyReady() || crashPoint() != null) {
            helper.succeed();
            return;
        }
        BlockPos relativeShopPos = new BlockPos(1, 1, 1);
        BlockPos shopPos = helper.absolutePos(relativeShopPos);
        helper.setBlock(relativeShopPos, ModBlocks.SHOP_BLOCK.get().defaultBlockState());
        ShopBlockEntity shop = helper.getBlockEntity(relativeShopPos);
        helper.assertTrue(shop != null, "the DanConomy player shop block must be available");
        shop.setPlacedByCreative(true);
        helper.assertTrue(shop.setAdminShopMode(true), "the disposable shop must enter admin mode");
        shop.setOwnerUuid(UUID.fromString("00000000-0000-0000-0000-000000000327"));
        int listingIndex = shop.addOrSelectListing("minecraft:diamond");
        ShopBlockEntity.Listing listing = shop.getListing(listingIndex);
        helper.assertTrue(listing != null, "the disposable DanConomy shop must have a listing");
        listing.setTradeMode(ShopBlockEntity.TradeMode.MONEY);
        listing.setMoneyPriceMinor(1L);
        listing.setBaseQuantity(1);

        ServerPlayer buyer = mockPlayer(helper);
        helper.assertTrue(BalanceManager.deposit(buyer.getUUID(), 10L).success(),
                "the DanConomy player shop fixture must seed sufficient funds");
        long balanceBefore = BalanceManager.queryBalance(buyer.getUUID()).value().orElseThrow().balanceMinorUnits();
        int diamondsBefore = buyer.getInventory().countItem(Items.DIAMOND);
        PlayerShopBlockService.buy(buyer, shopPos, listingIndex, 1, "MONEY");

        long balanceAfter = BalanceManager.queryBalance(buyer.getUUID()).value().orElseThrow().balanceMinorUnits();
        helper.assertTrue(buyer.getInventory().countItem(Items.DIAMOND) == diamondsBefore + 1,
                "the DanConomy player shop buy must deliver the item after confirmation");
        helper.assertTrue(balanceAfter == balanceBefore - 1L,
                "the DanConomy player shop buy must debit the exact amount");
        helper.assertTrue(!BalanceManager.getCustodyStore().hasIncompleteRecords(),
                "the DanConomy player shop buy must claim custody");
        LOGGER.info("futureshops.danconomy.gametest surface=player_shop_buy status=CONFIRMED amount=1 balance_delta={} item_delta={}",
                balanceAfter - balanceBefore, buyer.getInventory().countItem(Items.DIAMOND) - diamondsBefore);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void exactLedgerCartPayAndMultiplayerSurface(GameTestHelper helper) {
        if (!danconomyReady() || crashPoint() != null) {
            helper.succeed();
            return;
        }
        ServerPlayer cartPlayer = mockPlayer(helper);
        helper.assertTrue(BalanceManager.deposit(cartPlayer.getUUID(), 1_000L).success(),
                "the DanConomy cart fixture must seed sufficient funds");
        long cartBefore = BalanceManager.queryBalance(cartPlayer.getUUID()).value().orElseThrow().balanceMinorUnits();
        int diamondsBefore = cartPlayer.getInventory().countItem(Items.DIAMOND);
        ShopSessionManager.open(cartPlayer.getUUID(), "default");
        try {
            ShopBuyService.handleBuyRequest(cartPlayer,
                    C2SBuyRequestPacket.cart("default",
                            java.util.List.of(new C2SBuyRequestPacket.LineItem("minecraft:diamond", 1))));
        } finally {
            ShopSessionManager.close(cartPlayer.getUUID());
        }
        long cartAfter = BalanceManager.queryBalance(cartPlayer.getUUID()).value().orElseThrow().balanceMinorUnits();
        helper.assertTrue(cartPlayer.getInventory().countItem(Items.DIAMOND) == diamondsBefore + 1,
                "the DanConomy cart buy must deliver the prepared reward");
        helper.assertTrue(cartAfter == cartBefore - 500L,
                "the DanConomy cart buy must debit the aggregate price");

        ServerPlayer payer = mockPlayer(helper);
        ServerPlayer recipient = mockPlayer(helper);
        helper.assertTrue(!payer.getUUID().equals(recipient.getUUID()),
                "the DanConomy multiplayer fixture must create distinct players");
        helper.assertTrue(BalanceManager.deposit(payer.getUUID(), 100L).success(),
                "the DanConomy pay fixture must seed sufficient payer funds");
        long payerBefore = BalanceManager.queryBalance(payer.getUUID()).value().orElseThrow().balanceMinorUnits();
        long recipientBefore = BalanceManager.queryBalance(recipient.getUUID()).value().orElseThrow().balanceMinorUnits();
        var transfer = BalanceManager.transfer(payer.getUUID(), recipient.getUUID(), 25L);
        helper.assertTrue(transfer.success(), "the DanConomy pay transfer must confirm");
        helper.assertTrue(BalanceManager.queryBalance(payer.getUUID()).value().orElseThrow().balanceMinorUnits()
                        == payerBefore - 25L,
                "the DanConomy pay transfer must debit the payer once");
        helper.assertTrue(BalanceManager.queryBalance(recipient.getUUID()).value().orElseThrow().balanceMinorUnits()
                        == recipientBefore + 25L,
                "the DanConomy pay transfer must credit the recipient once");
        helper.assertTrue(!BalanceManager.getCustodyStore().hasIncompleteRecords(),
                "the DanConomy cart and pay routes must leave no incomplete custody");
        LOGGER.info("futureshops.danconomy.gametest surface=cart_pay_multiplayer cart_delta={} item_delta={} payer_delta={} recipient_delta={}",
                cartAfter - cartBefore, cartPlayer.getInventory().countItem(Items.DIAMOND) - diamondsBefore,
                BalanceManager.queryBalance(payer.getUUID()).value().orElseThrow().balanceMinorUnits() - payerBefore,
                BalanceManager.queryBalance(recipient.getUUID()).value().orElseThrow().balanceMinorUnits()
                        - recipientBefore);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void exactLedgerPublicApiReconnectClaimsAndPhysicalRefusal(GameTestHelper helper) {
        if (!danconomyReady() || crashPoint() != null) {
            helper.succeed();
            return;
        }
        ServerPlayer player = mockPlayer(helper);
        helper.assertTrue(ShopModAPI.deposit(player.getUUID(), 20L).success(),
                "the DanConomy public api fixture must seed sufficient funds");
        long before = ShopModAPI.queryBalance(player.getUUID()).value().orElseThrow().balanceMinorUnits();
        helper.assertTrue(ShopModAPI.withdraw(player.getUUID(), 2L).success()
                        && ShopModAPI.deposit(player.getUUID(), 3L).success(),
                "the DanConomy public api must confirm withdrawal and deposit");
        helper.assertTrue(ShopModAPI.queryBalance(player.getUUID()).value().orElseThrow().balanceMinorUnits()
                        == before + 1L,
                "the DanConomy public api must preserve exact deltas");

        RequestId reconnectId = RequestId.random();
        MutationRequest reconnectRequest = MutationRequest.forPlayer(reconnectId, player.getUUID(),
                4L, MutationKind.WITHDRAW);
        var first = BalanceManager.getCoordinator().withdraw(reconnectRequest);
        long reconnectBalance = BalanceManager.queryBalance(player.getUUID())
                .value().orElseThrow().balanceMinorUnits();
        var replay = BalanceManager.getCoordinator().withdraw(reconnectRequest);
        helper.assertTrue(first.confirmed() && replay.confirmed() && replay.receipt().equals(first.receipt()),
                "the DanConomy reconnect replay must return the original receipt");
        helper.assertTrue(BalanceManager.queryBalance(player.getUUID()).value().orElseThrow().balanceMinorUnits()
                        == reconnectBalance,
                "the DanConomy reconnect replay must not debit twice");

        RequestId claimId = RequestId.random();
        helper.assertTrue(BalanceManager.getCoordinator().createClaim(claimId, player.getUUID(), 7L,
                        "danconomy surface claim").state() == ClaimState.PENDING,
                "the DanConomy claim route must preserve pending custody");
        helper.assertTrue(BalanceManager.getCoordinator().deliverClaim(claimId).state() == ClaimState.DELIVERED,
                "the DanConomy claim route must record delivery");
        helper.assertTrue(BalanceManager.getCoordinator().resolveClaim(claimId).state() == ClaimState.RESOLVED,
                "the DanConomy claim route must record resolution");

        ItemStack money = new ItemStack(ModItems.MONEY_ITEM.get(), 1);
        player.setItemInHand(InteractionHand.MAIN_HAND, money);
        int moneyBefore = money.getCount();
        InteractionResultHolder<ItemStack> use = ModItems.MONEY_ITEM.get()
                .use(player.level(), player, InteractionHand.MAIN_HAND);
        helper.assertTrue(!use.getResult().consumesAction() && money.getCount() == moneyBefore,
                "DanConomy must refuse physical money without consuming the item");
        int inventoryBefore = player.getInventory().countItem(ModItems.MONEY_ITEM.get());
        player.getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack(), "withdraw 1");
        player.getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack(), "deposit");
        helper.assertTrue(player.getInventory().countItem(ModItems.MONEY_ITEM.get()) == inventoryBefore,
                "DanConomy physical commands must not mint or consume internal money");
        helper.assertTrue(!BalanceManager.getCustodyStore().hasIncompleteRecords(),
                "the DanConomy public and physical routes must leave no incomplete custody");
        LOGGER.info("futureshops.danconomy.gametest surface=public_reconnect_claim_physical public_delta=1 reconnect={} claim={} physical=REFUSED",
                replay.status(), BalanceManager.getCoordinator().claim(claimId).orElseThrow().state());
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void exactLedgerDebugCommand(GameTestHelper helper) {
        if (!danconomyReady() || crashPoint() != null) {
            helper.succeed();
            return;
        }
        var server = helper.getLevel().getServer();
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),
                "futureshops debug on danconomy");
        try {
            helper.assertTrue(DebugDiagnostics.enabled(DebugModule.DANCONOMY),
                    "the operator command must enable the DanConomy debug module");
            DanConomyEconomyProvider provider = new DanConomyEconomyProvider(server);
            MutationRequest request = MutationRequest.forPlayer(RequestId.random(), DEBUG_PLAYER,
                    1L, MutationKind.DEPOSIT);
            helper.assertTrue(provider.deposit(request).confirmed(),
                    "the DanConomy debug probe mutation must confirm");
            helper.assertTrue(DebugDiagnostics.statusLine().contains("module=danconomy"),
                    "the DanConomy debug status must identify the selected module");
            LOGGER.info("futureshops.danconomy.gametest debug_command request={} status={}",
                    request.requestId().value(), DebugDiagnostics.statusLine());
        } finally {
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),
                    "futureshops debug off");
            helper.assertTrue(DebugDiagnostics.session().isEmpty(),
                    "the operator command must disable the DanConomy debug session");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void exactLedgerProcessRestart(GameTestHelper helper) {
        if (!danconomyReady() || crashPoint() != null) {
            helper.succeed();
            return;
        }
        Path marker = helper.getLevel().getServer().getWorldPath(LevelResource.ROOT)
                .resolve("futureshops-danconomy-restart.marker");
        try {
            DanConomyEconomyProvider provider = new DanConomyEconomyProvider(helper.getLevel().getServer());
            MutationRequest request = MutationRequest.forPlayer(RESTART_REQUEST, RESTART_PLAYER,
                    11L, MutationKind.DEPOSIT);
            if (!Files.exists(marker)) {
                var mutation = provider.deposit(request);
                helper.assertTrue(mutation.confirmed(), "the first DanConomy restart pass must confirm");
                long balance = mutation.receipt().orElseThrow().resultingBalanceMinorUnits().orElseThrow();
                Files.writeString(marker, Long.toString(balance), StandardCharsets.UTF_8);
                LOGGER.info("futureshops.danconomy.gametest process_restart phase=FIRST request={} balance={} receipt=COMPLETED",
                        RESTART_REQUEST.value(), balance);
            } else {
                long expectedBalance = Long.parseLong(Files.readString(marker, StandardCharsets.UTF_8).trim());
                var lookup = provider.lookup(RESTART_REQUEST);
                helper.assertTrue(lookup.confirmed(),
                        "the restarted DanConomy provider must find the durable receipt");
                var duplicate = provider.retry(request);
                helper.assertTrue(duplicate.confirmed() && duplicate.receipt().equals(lookup.receipt()),
                        "the restarted DanConomy request must deduplicate");
                helper.assertTrue(provider.balance(RESTART_PLAYER).value().orElseThrow().balanceMinorUnits()
                                == expectedBalance,
                        "the restarted DanConomy balance must remain changed once");
                Files.deleteIfExists(marker);
                LOGGER.info("futureshops.danconomy.gametest process_restart phase=SECOND request={} lookup={} duplicate={} balance={}",
                        RESTART_REQUEST.value(), lookup.status(), duplicate.status(), expectedBalance);
            }
        } catch (java.io.IOException | RuntimeException exception) {
            helper.fail("the DanConomy process restart probe failed: " + exception.getClass().getSimpleName());
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void exactLedgerCrashBoundary(GameTestHelper helper) {
        CrashPoint point = crashPoint();
        Path marker = crashMarker(helper);
        if (!danconomyReady() || FMLLoader.isProduction()
                || point == null && !Files.exists(marker)) {
            helper.succeed();
            return;
        }
        try {
            if (point != null) {
                runCrashPass(helper, point, marker);
                helper.fail("the DanConomy crash process did not halt at the selected boundary");
                return;
            }
            runCrashRecoveryPass(helper, marker);
        } catch (java.io.IOException | RuntimeException exception) {
            helper.fail("the DanConomy crash probe failed: " + exception.getClass().getSimpleName()
                    + ": " + String.valueOf(exception.getMessage()));
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void exactLedgerRefusal(GameTestHelper helper) {
        if (!danconomySelected() || danconomyReady()) {
            helper.succeed();
            return;
        }
        helper.assertTrue(BalanceManager.getLifecycleSnapshotOrUnresolved().providerId().equals("danconomy"),
                "an unavailable DanConomy selection must not fall back to another provider");
        ProviderLifecycle selectedLifecycle = BalanceManager.getLifecycleSnapshotOrUnresolved().lifecycle();
        helper.assertTrue(selectedLifecycle != ProviderLifecycle.READY,
                "an unavailable DanConomy selection must remain fail closed");
        if (!supportedDanconomyVersion()) {
            helper.assertTrue(!BalanceManager.queryBalance(FIXTURE_PLAYER).confirmed(),
                    "an unsupported DanConomy version must refuse the public balance path");
            LOGGER.info("futureshops.danconomy.gametest version_refusal selected_lifecycle={}",
                    selectedLifecycle);
            helper.succeed();
            return;
        }
        DanConomyEconomyProvider provider = new DanConomyEconomyProvider(helper.getLevel().getServer());
        helper.assertTrue(!provider.capabilities().withdraw() && !provider.capabilities().deposit()
                        && !provider.capabilities().receiptLookup() && !provider.capabilities().idempotentRetry(),
                "an unsupported DanConomy currency must not advertise mutation capabilities");
        helper.assertTrue(provider.balance(FIXTURE_PLAYER).status() == ProviderResultStatus.UNAVAILABLE,
                "an unsupported DanConomy currency must refuse balance use");
        LOGGER.info("futureshops.danconomy.gametest refusal selected_lifecycle={} provider_lifecycle={} diagnostic={}",
                selectedLifecycle, provider.readiness().lifecycle(), provider.readiness().diagnostic());
        helper.succeed();
    }

    private static void runCrashPass(GameTestHelper helper, CrashPoint point, Path marker)
            throws java.io.IOException {
        DanConomyEconomyProvider provider = new DanConomyEconomyProvider(helper.getLevel().getServer());
        UUID player = point.player();
        RequestId requestId = point.requestId();
        long baseline = provider.balance(player).value().orElseThrow().balanceMinorUnits();
        helper.getLevel().getDataStorage().save();
        IOUtilities.waitUntilIOWorkerComplete();
        writeCrashMarker(marker, point, baseline);
        MutationRequest request = MutationRequest.forPlayer(requestId, player, CRASH_AMOUNT, MutationKind.DEPOSIT);

        if (point == CrashPoint.BEFORE_MUTATION) {
            LOGGER.info("futureshops.danconomy.gametest crash point={} request={} balance={} action=HALT",
                    point.id(), requestId.value(), baseline);
            Runtime.getRuntime().halt(86);
        }
        if (point == CrashPoint.AFTER_MEMORY_BEFORE_DURABLE) {
            Path data = helper.getLevel().getServer().getWorldPath(LevelResource.ROOT).resolve("data");
            Files.setPosixFilePermissions(data, EnumSet.of(PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_EXECUTE));
            ProviderResult<MutationReceipt> result = provider.deposit(request);
            helper.assertTrue(result.status() == ProviderResultStatus.RECOVERY_REQUIRED,
                    "the blocked DanConomy save must leave the outcome unacknowledged");
            helper.assertTrue(provider.readiness().lifecycle() == ProviderLifecycle.RECOVERING,
                    "a nondurable DanConomy receipt must put the provider into recovery");
            ProviderResult<MutationReceipt> blockedNewRequest = provider.deposit(MutationRequest.forPlayer(
                    RequestId.random(), player, 1L, MutationKind.DEPOSIT));
            helper.assertTrue(blockedNewRequest.status() == ProviderResultStatus.RECOVERY_REQUIRED,
                    "a nondurable DanConomy receipt must block every new mutation");
            LOGGER.info("futureshops.danconomy.gametest crash point={} request={} balance={} result={} new_request={} action=HALT",
                    point.id(), requestId.value(), baseline, result.status(), blockedNewRequest.status());
            Runtime.getRuntime().halt(86);
        }

        FileEconomyReceiptAuditJournal audit = new FileEconomyReceiptAuditJournal(crashAuditDirectory(helper, point));
        InMemoryEconomyTransactionJournal journal = new InMemoryEconomyTransactionJournal();
        EconomyLifecycleController lifecycle = new EconomyLifecycleController(DanConomyEconomyProvider.PROVIDER_ID);
        lifecycle.resolve(ProviderLifecycle.READY, "", true, true, false);
        EconomyProvider crashAfterDurable = new CrashAfterDurableProvider(provider, point, baseline);
        EconomyTransactionCoordinator coordinator = new EconomyTransactionCoordinator(crashAfterDurable, lifecycle,
                journal, new InMemoryEconomyCustodyStore(), new InMemoryEconomyClaimStore(), audit);
        coordinator.deposit(request);
        helper.fail("the DanConomy durable receipt was acknowledged before the crash process halted");
    }

    private static void runCrashRecoveryPass(GameTestHelper helper, Path marker)
            throws java.io.IOException {
        Properties state = readCrashMarker(marker);
        CrashPoint point = CrashPoint.parse(state.getProperty("point"));
        long baseline = Long.parseLong(state.getProperty("baseline"));
        MutationRequest request = MutationRequest.forPlayer(point.requestId(), point.player(), CRASH_AMOUNT,
                MutationKind.DEPOSIT);
        DanConomyEconomyProvider provider = new DanConomyEconomyProvider(helper.getLevel().getServer());
        long expected = baseline + CRASH_AMOUNT;

        if (point == CrashPoint.AFTER_DURABLE_BEFORE_ACK) {
            helper.assertTrue(provider.balance(point.player()).value().orElseThrow().balanceMinorUnits() == expected,
                    "the durable DanConomy crash must preserve the balance effect");
            helper.assertTrue(provider.lookup(request).confirmed(),
                    "the durable DanConomy crash must preserve the provider receipt");
            FileEconomyReceiptAuditJournal audit = new FileEconomyReceiptAuditJournal(
                    crashAuditDirectory(helper, point));
            var pending = audit.latest(request.requestId()).orElseThrow();
            helper.assertTrue(pending.incomplete(),
                    "the local crash audit must remain pending before provider reconciliation");
            InMemoryEconomyTransactionJournal journal = new InMemoryEconomyTransactionJournal();
            journal.append(pending);
            EconomyLifecycleController lifecycle = new EconomyLifecycleController(DanConomyEconomyProvider.PROVIDER_ID);
            lifecycle.resolve(ProviderLifecycle.READY, "", false, true, true);
            EconomyTransactionCoordinator coordinator = new EconomyTransactionCoordinator(provider, lifecycle,
                    journal, new InMemoryEconomyCustodyStore(), new InMemoryEconomyClaimStore(), audit);
            helper.assertTrue(coordinator.recover(request.requestId()).confirmed(),
                    "the coordinator must reconcile the pending request from the authoritative receipt");
            helper.assertTrue(lifecycle.snapshot().lifecycle() == ProviderLifecycle.READY,
                    "the coordinator must return to ready after authoritative recovery");
            helper.assertTrue(!audit.latest(request.requestId()).orElseThrow().incomplete(),
                    "the recovered local audit must end in a terminal record");
            helper.assertTrue(provider.balance(point.player()).value().orElseThrow().balanceMinorUnits() == expected,
                    "authoritative recovery must not apply a second balance effect");
        } else {
            helper.assertTrue(provider.balance(point.player()).value().orElseThrow().balanceMinorUnits() == baseline,
                    "a precommit DanConomy crash must leave no durable balance effect");
            helper.assertTrue(provider.lookup(request).error() == ProviderError.RECEIPT_NOT_FOUND,
                    "a precommit DanConomy crash must leave no provider receipt");
            ProviderResult<MutationReceipt> retry = provider.retry(request);
            helper.assertTrue(retry.confirmed(), "a precommit DanConomy request must retry safely");
            helper.assertTrue(provider.retry(request).receipt().equals(retry.receipt()),
                    "the retried DanConomy request must deduplicate");
            helper.assertTrue(provider.balance(point.player()).value().orElseThrow().balanceMinorUnits() == expected,
                    "the retried DanConomy request must apply one balance effect");
        }
        Files.delete(marker);
        LOGGER.info("futureshops.danconomy.gametest crash_recovery point={} request={} baseline={} balance={} result=CONFIRMED",
                point.id(), request.requestId().value(), baseline, expected);
    }

    private static Path crashMarker(GameTestHelper helper) {
        return helper.getLevel().getServer().getWorldPath(LevelResource.ROOT)
                .resolve("futureshops-danconomy-crash.properties");
    }

    private static Path crashAuditDirectory(GameTestHelper helper, CrashPoint point) {
        return helper.getLevel().getServer().getWorldPath(LevelResource.ROOT)
                .resolve("data").resolve("futureshops").resolve("danconomy-crash-receipts").resolve(point.id());
    }

    private static void writeCrashMarker(Path marker, CrashPoint point, long baseline) throws java.io.IOException {
        Properties state = new Properties();
        state.setProperty("point", point.id());
        state.setProperty("baseline", Long.toString(baseline));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        state.store(output, null);
        Files.write(marker, output.toByteArray(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        try (FileChannel channel = FileChannel.open(marker, StandardOpenOption.READ)) {
            channel.force(true);
        }
    }

    private static Properties readCrashMarker(Path marker) throws java.io.IOException {
        Properties state = new Properties();
        try (var input = Files.newInputStream(marker)) {
            state.load(input);
        }
        return state;
    }

    private static CrashPoint crashPoint() {
        return CrashPoint.parseNullable(System.getProperty(CRASH_POINT_PROPERTY));
    }

    private enum CrashPoint {
        BEFORE_MUTATION("before_mutation", "00000000-0000-0000-0000-000000000330",
                "00000000-0000-0000-0000-000000000331"),
        AFTER_MEMORY_BEFORE_DURABLE("after_memory_before_durable", "00000000-0000-0000-0000-000000000332",
                "00000000-0000-0000-0000-000000000333"),
        AFTER_DURABLE_BEFORE_ACK("after_durable_before_ack", "00000000-0000-0000-0000-000000000334",
                "00000000-0000-0000-0000-000000000335");

        private final String id;
        private final UUID player;
        private final RequestId requestId;

        CrashPoint(String id, String player, String requestId) {
            this.id = id;
            this.player = UUID.fromString(player);
            this.requestId = new RequestId(UUID.fromString(requestId));
        }

        String id() {
            return id;
        }

        UUID player() {
            return player;
        }

        RequestId requestId() {
            return requestId;
        }

        static CrashPoint parse(String value) {
            CrashPoint point = parseNullable(value);
            if (point == null) {
                throw new IllegalArgumentException("the DanConomy crash point is invalid");
            }
            return point;
        }

        static CrashPoint parseNullable(String value) {
            if (value == null || value.isBlank()) {
                return null;
            }
            for (CrashPoint point : values()) {
                if (point.id.equals(value)) {
                    return point;
                }
            }
            throw new IllegalArgumentException("the DanConomy crash point is unsupported");
        }
    }

    private record CrashAfterDurableProvider(DanConomyEconomyProvider delegate, CrashPoint point,
                                              long baseline) implements EconomyProvider {
        @Override
        public String providerId() {
            return delegate.providerId();
        }

        @Override
        public int compatibilityVersion() {
            return delegate.compatibilityVersion();
        }

        @Override
        public CurrencyMetadata currency() {
            return delegate.currency();
        }

        @Override
        public ProviderCapabilities capabilities() {
            return delegate.capabilities();
        }

        @Override
        public ProviderReadiness readiness() {
            return delegate.readiness();
        }

        @Override
        public ProviderResult<BalanceSnapshot> balance(UUID playerId) {
            return delegate.balance(playerId);
        }

        @Override
        public ProviderResult<BalanceSnapshot> precheck(MutationRequest request) {
            return delegate.precheck(request);
        }

        @Override
        public ProviderResult<MutationReceipt> withdraw(MutationRequest request) {
            return crash(delegate.withdraw(request), request);
        }

        @Override
        public ProviderResult<MutationReceipt> deposit(MutationRequest request) {
            return crash(delegate.deposit(request), request);
        }

        @Override
        public ProviderResult<MutationReceipt> lookup(RequestId requestId) {
            return delegate.lookup(requestId);
        }

        @Override
        public ProviderResult<MutationReceipt> lookup(MutationRequest request) {
            return delegate.lookup(request);
        }

        @Override
        public ProviderResult<MutationReceipt> retry(MutationRequest request) {
            return delegate.retry(request);
        }

        private ProviderResult<MutationReceipt> crash(ProviderResult<MutationReceipt> result,
                                                       MutationRequest request) {
            if (!result.confirmed()) {
                throw new IllegalStateException("the DanConomy provider did not reach durable confirmation");
            }
            LOGGER.info("futureshops.danconomy.gametest crash point={} request={} baseline={} result={} action=HALT",
                    point.id(), request.requestId().value(), baseline, result.status());
            Runtime.getRuntime().halt(86);
            return result;
        }
    }

    private static boolean danconomySelected() {
        return ModList.get().isLoaded("danconomy")
                && "danconomy".equals(BalanceManager.getLifecycleSnapshotOrUnresolved().providerId());
    }

    private static boolean supportedDanconomyVersion() {
        return ModList.get().getModContainerById("danconomy")
                .map(container -> DanConomyEconomyProviderRegistration.isSupportedVersion(
                        container.getModInfo().getVersion().toString()))
                .orElse(false);
    }

    private static boolean danconomyReady() {
        return danconomySelected()
                && BalanceManager.getLifecycleSnapshotOrUnresolved().lifecycle() == ProviderLifecycle.READY;
    }

    private static void invokeOrdinaryDeposit(GameTestHelper helper, UUID playerId, long amount)
            throws ReflectiveOperationException {
        invokeOrdinaryMutation(helper, "deposit", playerId, amount);
    }

    private static void invokeOrdinaryWithdraw(GameTestHelper helper, UUID playerId, long amount)
            throws ReflectiveOperationException {
        invokeOrdinaryMutation(helper, "withdraw", playerId, amount);
    }

    private static void invokeOrdinaryMutation(GameTestHelper helper, String method, UUID playerId, long amount)
            throws ReflectiveOperationException {
        ClassLoader loader = DanConomyGameTests.class.getClassLoader();
        Class<?> registry = Class.forName("com.danners45.danconomy.currency.CurrencyRegistry", false, loader);
        String defaultId = String.valueOf(invoke(registry.getMethod("getDefaultCurrencyId"), null));
        Object currency = invoke(registry.getMethod("get", String.class), null, defaultId);
        Class<?> currencyClass = Class.forName("com.danners45.danconomy.currency.Currency", false, loader);
        Class<?> access = Class.forName("com.danners45.danconomy.economy.EconomyAccess", false, loader);
        invoke(access.getMethod(method, net.minecraft.server.level.ServerLevel.class, UUID.class,
                currencyClass, long.class), null, helper.getLevel(), playerId, currency, amount);
    }

    private static Object loadLedger(GameTestHelper helper, CompoundTag data) throws ReflectiveOperationException {
        Class<?> ledger = Class.forName("com.danners45.danconomy.data.LedgerData", false,
                DanConomyGameTests.class.getClassLoader());
        return invoke(ledger.getMethod("load", CompoundTag.class, HolderLookup.Provider.class),
                null, data, helper.getLevel().registryAccess());
    }

    private static CompoundTag corruptedReceiptData(CompoundTag fileRoot) {
        CompoundTag data = fileRoot.getCompound("data").copy();
        CompoundTag receipts = data.getCompound("FutureShopsReceipts");
        ListTag entries = receipts.getList("entries", Tag.TAG_COMPOUND);
        entries.add(new CompoundTag());
        return data;
    }

    private static CompoundTag corruptedChecksumData(CompoundTag fileRoot, RequestId requestId) {
        CompoundTag data = fileRoot.getCompound("data").copy();
        CompoundTag receipts = data.getCompound("FutureShopsReceipts");
        ListTag entries = receipts.getList("entries", Tag.TAG_COMPOUND);
        for (Tag rawEntry : entries) {
            if (rawEntry instanceof CompoundTag entry && entry.hasUUID("request_id")
                    && requestId.value().equals(entry.getUUID("request_id"))) {
                entry.putString("checksum", "invalid");
            }
        }
        return data;
    }

    private static CompoundTag readLedgerUnchecked(GameTestHelper helper) {
        try {
            return readLedger(helper);
        } catch (java.io.IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static CompoundTag readLedger(GameTestHelper helper) throws java.io.IOException {
        Path path = helper.getLevel().getServer().getWorldPath(LevelResource.ROOT)
                .resolve("data").resolve("danconomy_ledger.dat");
        return NbtIo.readCompressed(path, NbtAccounter.create(64L * 1024L * 1024L));
    }

    private static boolean hasReceipt(CompoundTag fileRoot, RequestId requestId) {
        return findReceipt(fileRoot, requestId) != null;
    }

    private static CompoundTag findReceipt(CompoundTag fileRoot, RequestId requestId) {
        if (!(fileRoot.get("data") instanceof CompoundTag data)
                || !(data.get("FutureShopsReceipts") instanceof CompoundTag receipts)
                || !(receipts.get("entries") instanceof ListTag entries)) {
            return null;
        }
        for (Tag rawEntry : entries) {
            if (rawEntry instanceof CompoundTag entry && entry.hasUUID("request_id")
                    && requestId.value().equals(entry.getUUID("request_id"))) {
                return entry;
            }
        }
        return null;
    }

    private static int receiptCount(CompoundTag fileRoot) {
        if (!(fileRoot.get("data") instanceof CompoundTag data)
                || !(data.get("FutureShopsReceipts") instanceof CompoundTag receipts)
                || !(receipts.get("entries") instanceof ListTag entries)) {
            return 0;
        }
        return entries.size();
    }

    private static Object invoke(Method method, Object target, Object... arguments)
            throws ReflectiveOperationException {
        try {
            return method.invoke(target, arguments);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof ReflectiveOperationException reflective) {
                throw reflective;
            }
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new ReflectiveOperationException("DanConomy GameTest call failed", cause);
        }
    }
}

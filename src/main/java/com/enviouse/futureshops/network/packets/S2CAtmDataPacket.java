package com.enviouse.futureshops.network.packets;

import com.enviouse.futureshops.client.ShopClientPacketHandler;
import com.enviouse.futureshops.data.AtmDenominationData;
import com.enviouse.futureshops.money.CurrencyWithdrawalService;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;

public record S2CAtmDataPacket(
        long balanceMinor,
        boolean balanceKnown,
        String currencyName,
        int currencyDecimals,
        String providerId,
        String route,
        boolean protectedMinting,
        String currencySignature,
        List<AtmDenominationData> denominations,
        boolean serviceAvailable,
        String availabilityCode,
        boolean openScreen,
        int pendingCashClaimCount,
        List<CashClaimSummary> collectibleCashClaims
) {
    public static final int MAX_COLLECTIBLE_CASH_CLAIMS = 4;
    public static final int MAX_PENDING_CASH_CLAIMS = 1_000_000;
    public static final String ROUTE_PROTECTED = "PROTECTED_ESCROW";
    public static final String ROUTE_FOREIGN = "FOREIGN_UNPROTECTED";
    public static final String AVAILABLE = "AVAILABLE";

    private static final Pattern IDENTIFIER = Pattern.compile(
            "[a-z0-9_.-]+:[a-z0-9/._-]+");
    private static final Pattern SIGNATURE = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern CODE = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");
    private static final Set<String> ROUTES = Set.of(
            ROUTE_PROTECTED, ROUTE_FOREIGN);

    public S2CAtmDataPacket {
        currencyName = requireText(currencyName, 256, "currencyName");
        providerId = requireText(providerId, 128, "providerId");
        route = requireText(route, 32, "route");
        currencySignature = Objects.requireNonNull(
                currencySignature, "currencySignature");
        availabilityCode = Objects.requireNonNull(
                availabilityCode, "availabilityCode");
        denominations = List.copyOf(Objects.requireNonNull(
                denominations, "denominations"));
        collectibleCashClaims = List.copyOf(Objects.requireNonNull(
                collectibleCashClaims, "collectibleCashClaims"));
        if (!balanceKnown && balanceMinor != 0L
                || currencyDecimals < 0 || currencyDecimals > 6
                || !ROUTES.contains(route)
                || route.equals(ROUTE_PROTECTED) != protectedMinting
                || !SIGNATURE.matcher(currencySignature).matches()
                || !CODE.matcher(availabilityCode).matches()
                || serviceAvailable != availabilityCode.equals(AVAILABLE)
                || serviceAvailable && !balanceKnown
                || denominations.size()
                > CurrencyWithdrawalService.MAX_DENOMINATIONS
                || serviceAvailable && denominations.isEmpty()
                || pendingCashClaimCount < collectibleCashClaims.size()
                || pendingCashClaimCount > MAX_PENDING_CASH_CLAIMS
                || collectibleCashClaims.size()
                > MAX_COLLECTIBLE_CASH_CLAIMS
                || pendingCashClaimCount == 0
                && !collectibleCashClaims.isEmpty()) {
            throw new IllegalArgumentException("ATM data values are invalid");
        }
        for (AtmDenominationData denomination : denominations) {
            Objects.requireNonNull(denomination, "denomination");
            if (denomination.itemId() == null
                    || denomination.itemId().length() > 256
                    || !IDENTIFIER.matcher(denomination.itemId()).matches()
                    || denomination.valueMinor() <= 0L
                    || denomination.maxStackSize() <= 0
                    || denomination.maxStackSize()
                    > CurrencyWithdrawalService.MAX_SELECTED_ITEMS) {
                throw new IllegalArgumentException(
                        "ATM denomination data is invalid");
            }
        }
        Set<UUID> claimIds = new HashSet<>();
        for (CashClaimSummary claim : collectibleCashClaims) {
            Objects.requireNonNull(claim, "collectibleCashClaim");
            if (!claimIds.add(claim.claimId())) {
                throw new IllegalArgumentException(
                        "ATM cash claim summary is duplicated");
            }
        }
    }

    public S2CAtmDataPacket(
            long balanceMinor,
            boolean balanceKnown,
            String currencyName,
            int currencyDecimals,
            String providerId,
            String route,
            boolean protectedMinting,
            String currencySignature,
            List<AtmDenominationData> denominations,
            boolean serviceAvailable,
            String availabilityCode,
            boolean openScreen
    ) {
        this(balanceMinor, balanceKnown, currencyName, currencyDecimals,
                providerId, route, protectedMinting, currencySignature,
                denominations, serviceAvailable, availabilityCode,
                openScreen, 0, List.of());
    }

    public static void encode(S2CAtmDataPacket packet,
                              FriendlyByteBuf buffer) {
        buffer.writeLong(packet.balanceMinor());
        buffer.writeBoolean(packet.balanceKnown());
        buffer.writeUtf(packet.currencyName(), 256);
        buffer.writeVarInt(packet.currencyDecimals());
        buffer.writeUtf(packet.providerId(), 128);
        buffer.writeUtf(packet.route(), 32);
        buffer.writeBoolean(packet.protectedMinting());
        buffer.writeUtf(packet.currencySignature(), 64);
        buffer.writeVarInt(packet.denominations().size());
        for (AtmDenominationData denomination : packet.denominations()) {
            buffer.writeUtf(denomination.itemId(), 256);
            buffer.writeLong(denomination.valueMinor());
            buffer.writeVarInt(denomination.maxStackSize());
        }
        buffer.writeBoolean(packet.serviceAvailable());
        buffer.writeUtf(packet.availabilityCode(), 64);
        buffer.writeBoolean(packet.openScreen());
        buffer.writeVarInt(packet.pendingCashClaimCount());
        buffer.writeVarInt(packet.collectibleCashClaims().size());
        for (CashClaimSummary claim : packet.collectibleCashClaims()) {
            buffer.writeUUID(claim.claimId());
            buffer.writeUtf(claim.kind(), 16);
            buffer.writeVarInt(claim.billCount());
        }
    }

    public static S2CAtmDataPacket decode(FriendlyByteBuf buffer) {
        try {
            long balance = buffer.readLong();
            boolean balanceKnown = buffer.readBoolean();
            String currencyName = buffer.readUtf(256);
            int decimals = buffer.readVarInt();
            String providerId = buffer.readUtf(128);
            String route = buffer.readUtf(32);
            boolean protectedMinting = buffer.readBoolean();
            String signature = buffer.readUtf(64);
            int size = buffer.readVarInt();
            if (size < 0
                    || size > CurrencyWithdrawalService.MAX_DENOMINATIONS) {
                throw new DecoderException(
                        "ATM denomination data is invalid");
            }
            List<AtmDenominationData> denominations = new ArrayList<>(size);
            for (int index = 0; index < size; index++) {
                denominations.add(new AtmDenominationData(
                        buffer.readUtf(256),
                        buffer.readLong(),
                        buffer.readVarInt()));
            }
            boolean serviceAvailable = buffer.readBoolean();
            String availabilityCode = buffer.readUtf(64);
            boolean openScreen = buffer.readBoolean();
            int pendingCashClaimCount = buffer.readVarInt();
            int claimCount = buffer.readVarInt();
            if (pendingCashClaimCount < 0
                    || pendingCashClaimCount > MAX_PENDING_CASH_CLAIMS
                    || claimCount < 0
                    || claimCount > MAX_COLLECTIBLE_CASH_CLAIMS
                    || claimCount > pendingCashClaimCount) {
                throw new DecoderException(
                        "ATM cash claim summary count is invalid");
            }
            List<CashClaimSummary> cashClaims = new ArrayList<>(claimCount);
            for (int index = 0; index < claimCount; index++) {
                cashClaims.add(new CashClaimSummary(buffer.readUUID(),
                        buffer.readUtf(16), buffer.readVarInt()));
            }
            return new S2CAtmDataPacket(
                    balance, balanceKnown, currencyName, decimals,
                    providerId, route, protectedMinting, signature,
                    denominations, serviceAvailable, availabilityCode,
                    openScreen, pendingCashClaimCount, cashClaims);
        } catch (DecoderException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new DecoderException("ATM data packet is invalid",
                    exception);
        }
    }

    public static void handle(S2CAtmDataPacket packet,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ShopClientPacketHandler.handleAtmData(packet)));
        context.setPacketHandled(true);
    }

    private static String requireText(String value, int maximumLength,
                                      String name) {
        String normalized = Objects.requireNonNull(value, name);
        if (normalized.isEmpty() || normalized.length() > maximumLength
                || !normalized.equals(normalized.strip())) {
            throw new IllegalArgumentException("ATM data text is invalid");
        }
        return normalized;
    }

    public record CashClaimSummary(
            UUID claimId,
            String kind,
            int billCount
    ) {
        private static final Set<String> KINDS = Set.of(
                "PROTECTED_CASH", "FOREIGN_CASH");

        public CashClaimSummary {
            Objects.requireNonNull(claimId, "claimId");
            kind = Objects.requireNonNull(kind, "kind");
            if (claimId.equals(new UUID(0L, 0L))
                    || !KINDS.contains(kind)
                    || billCount < 0 || billCount > 4096) {
                throw new IllegalArgumentException(
                        "ATM cash claim summary is invalid");
            }
        }
    }
}

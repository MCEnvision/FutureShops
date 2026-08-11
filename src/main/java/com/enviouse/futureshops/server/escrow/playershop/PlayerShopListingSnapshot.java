package com.enviouse.futureshops.server.escrow.playershop;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public record PlayerShopListingSnapshot(
        String listingId,
        int listingIndex,
        Direction direction,
        ConfiguredTradeMode configuredTradeMode,
        int baseQuantity,
        long moneyPriceMinorUnits,
        ItemTemplate barterTemplate,
        int barterUnitsPerPurchase,
        long buybackPriceMinorUnits,
        int buybackCap,
        int buybackBought,
        List<ItemTemplate> outputs,
        PromotionSnapshot promotion,
        boolean hidden,
        boolean showcase,
        boolean adminShop,
        String revisionFingerprint
) {
    public PlayerShopListingSnapshot {
        listingId = PlayerShopBinarySupport.requireString(listingId,
                PlayerShopEscrowConstants.MAX_IDENTIFIER_LENGTH, "listing id");
        if (listingIndex < 0 || listingIndex > 1_000_000
                || baseQuantity <= 0 || moneyPriceMinorUnits < 0L
                || barterUnitsPerPurchase < 0 || buybackPriceMinorUnits < 0L
                || buybackCap < 0 || buybackBought < 0
                || buybackCap > 0 && buybackBought > buybackCap) {
            throw new IllegalArgumentException("Player shop listing values are invalid");
        }
        direction = Objects.requireNonNull(direction, "direction");
        configuredTradeMode = Objects.requireNonNull(configuredTradeMode,
                "configuredTradeMode");
        outputs = List.copyOf(Objects.requireNonNull(outputs, "outputs"));
        if (outputs.isEmpty()
                || outputs.size() > PlayerShopEscrowConstants.MAX_LISTING_OUTPUTS) {
            throw new IllegalArgumentException("Player shop listing outputs are invalid");
        }
        promotion = Objects.requireNonNull(promotion, "promotion");
        revisionFingerprint = PlayerShopBinarySupport.requireString(
                revisionFingerprint, 64, "listing revision fingerprint");
        boolean needsBarter = configuredTradeMode != ConfiguredTradeMode.MONEY;
        if (needsBarter != (barterTemplate != null)
                || needsBarter && barterUnitsPerPurchase <= 0
                || !needsBarter && barterUnitsPerPurchase != 0) {
            throw new IllegalArgumentException("Player shop listing barter values are invalid");
        }
        if (!revisionOf(listingId, listingIndex, direction,
                configuredTradeMode, baseQuantity, moneyPriceMinorUnits,
                barterTemplate, barterUnitsPerPurchase,
                buybackPriceMinorUnits, buybackCap, buybackBought, outputs,
                promotion, hidden, showcase, adminShop)
                .equals(revisionFingerprint)) {
            throw new IllegalArgumentException("Player shop listing revision is invalid");
        }
    }

    public static PlayerShopListingSnapshot capture(
            String listingId,
            int listingIndex,
            Direction direction,
            ConfiguredTradeMode configuredTradeMode,
            int baseQuantity,
            long moneyPriceMinorUnits,
            ItemTemplate barterTemplate,
            int barterUnitsPerPurchase,
            long buybackPriceMinorUnits,
            int buybackCap,
            int buybackBought,
            List<ItemTemplate> outputs,
            PromotionSnapshot promotion,
            boolean hidden,
            boolean showcase,
            boolean adminShop
    ) {
        String revision = revisionOf(listingId, listingIndex, direction,
                configuredTradeMode, baseQuantity, moneyPriceMinorUnits,
                barterTemplate, barterUnitsPerPurchase,
                buybackPriceMinorUnits, buybackCap, buybackBought, outputs,
                promotion, hidden, showcase, adminShop);
        return new PlayerShopListingSnapshot(listingId, listingIndex,
                direction, configuredTradeMode, baseQuantity,
                moneyPriceMinorUnits, barterTemplate, barterUnitsPerPurchase,
                buybackPriceMinorUnits, buybackCap, buybackBought, outputs,
                promotion, hidden, showcase, adminShop, revision);
    }

    public int remainingBuybackUnits() {
        return buybackCap == 0 ? Integer.MAX_VALUE : buybackCap - buybackBought;
    }

    private static String revisionOf(
            String listingId,
            int listingIndex,
            Direction direction,
            ConfiguredTradeMode tradeMode,
            int baseQuantity,
            long moneyPrice,
            ItemTemplate barter,
            int barterUnits,
            long buybackPrice,
            int buybackCap,
            int buybackBought,
            List<ItemTemplate> outputs,
            PromotionSnapshot promotion,
            boolean hidden,
            boolean showcase,
            boolean adminShop
    ) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeUTF("futureshops player shop listing revision v1");
            PlayerShopBinarySupport.writeString(output, listingId,
                    PlayerShopEscrowConstants.MAX_IDENTIFIER_LENGTH);
            output.writeInt(listingIndex);
            output.writeByte(direction.ordinal());
            output.writeByte(tradeMode.ordinal());
            output.writeInt(baseQuantity);
            output.writeLong(moneyPrice);
            output.writeBoolean(barter != null);
            if (barter != null) {
                writeTemplate(output, barter);
            }
            output.writeInt(barterUnits);
            output.writeLong(buybackPrice);
            output.writeInt(buybackCap);
            output.writeInt(buybackBought);
            output.writeInt(outputs.size());
            for (ItemTemplate value : outputs) {
                writeTemplate(output, value);
            }
            writePromotion(output, promotion);
            output.writeBoolean(hidden);
            output.writeBoolean(showcase);
            output.writeBoolean(adminShop);
            output.flush();
            return PlayerShopBinarySupport.sha256(bytes.toByteArray());
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to fingerprint player shop listing", exception);
        }
    }

    static void writeTemplate(DataOutputStream output, ItemTemplate template)
            throws IOException {
        PlayerShopBinarySupport.writeString(output, template.itemId(),
                PlayerShopEscrowConstants.MAX_IDENTIFIER_LENGTH);
        output.writeInt(template.unitsPerPurchase());
        output.writeByte(template.matchMode().ordinal());
        PlayerShopBinarySupport.writeBytes(output,
                template.canonicalOneCountTemplate(),
                PlayerShopEscrowConstants.MAX_COMPONENT_BYTES);
    }

    static void writePromotion(DataOutputStream output, PromotionSnapshot promotion)
            throws IOException {
        PlayerShopBinarySupport.writeOptionalString(output,
                promotion.promotionType(), 64);
        output.writeLong(Double.doubleToLongBits(promotion.promotionValue()));
        output.writeInt(promotion.buyX());
        output.writeInt(promotion.getY());
        output.writeLong(promotion.startEpochSeconds());
        output.writeLong(promotion.endEpochSeconds());
        output.writeBoolean(promotion.flash());
        output.writeBoolean(promotion.activeAtQuote());
    }

    public enum Direction {
        SELL,
        BUY,
        BOTH
    }

    public enum ConfiguredTradeMode {
        MONEY,
        BARTER,
        BOTH,
        MONEY_AND_BARTER
    }

    public static final class ItemTemplate {
        private final String itemId;
        private final int unitsPerPurchase;
        private final PlayerShopItemMatchMode matchMode;
        private final byte[] canonicalOneCountTemplate;

        public ItemTemplate(
                String itemId,
                int unitsPerPurchase,
                PlayerShopItemMatchMode matchMode,
                byte[] canonicalOneCountTemplate
        ) {
            this.itemId = PlayerShopBinarySupport.requireString(itemId,
                    PlayerShopEscrowConstants.MAX_IDENTIFIER_LENGTH, "template item id");
            if (unitsPerPurchase <= 0) {
                throw new IllegalArgumentException("Player shop template quantity is invalid");
            }
            this.unitsPerPurchase = unitsPerPurchase;
            this.matchMode = Objects.requireNonNull(matchMode, "matchMode");
            this.canonicalOneCountTemplate = PlayerShopBinarySupport.requireBytes(
                    canonicalOneCountTemplate,
                    PlayerShopEscrowConstants.MAX_COMPONENT_BYTES,
                    "listing item template");
        }

        public String itemId() {
            return itemId;
        }

        public int unitsPerPurchase() {
            return unitsPerPurchase;
        }

        public PlayerShopItemMatchMode matchMode() {
            return matchMode;
        }

        public byte[] canonicalOneCountTemplate() {
            return canonicalOneCountTemplate.clone();
        }

        @Override
        public boolean equals(Object object) {
            return object instanceof ItemTemplate other
                    && itemId.equals(other.itemId)
                    && unitsPerPurchase == other.unitsPerPurchase
                    && matchMode == other.matchMode
                    && Arrays.equals(canonicalOneCountTemplate,
                            other.canonicalOneCountTemplate);
        }

        @Override
        public int hashCode() {
            return 31 * Objects.hash(itemId, unitsPerPurchase, matchMode)
                    + Arrays.hashCode(canonicalOneCountTemplate);
        }
    }

    public record PromotionSnapshot(
            String promotionType,
            double promotionValue,
            int buyX,
            int getY,
            long startEpochSeconds,
            long endEpochSeconds,
            boolean flash,
            boolean activeAtQuote
    ) {
        public PromotionSnapshot {
            promotionType = PlayerShopBinarySupport.optionalString(
                    promotionType, 64, "promotion type");
            if (!Double.isFinite(promotionValue) || promotionValue < 0.0D
                    || buyX < 0 || getY < 0 || startEpochSeconds < 0L
                    || endEpochSeconds < 0L) {
                throw new IllegalArgumentException("Player shop promotion is invalid");
            }
        }
    }
}

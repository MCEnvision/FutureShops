package com.enviouse.futureshops.server.market.auction;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

public final class AuctionRequestFingerprints {
    private AuctionRequestFingerprints() {
    }

    public static String create(CreateAuctionCommand command) {
        return hash(output -> {
            text(output, "auction_request_v1");
            text(output, "create");
            id(output, command.requestId());
            id(output, command.listingId());
            id(output, command.sellerId());
            id(output, command.activationTransactionId());
            lot(output, command.itemLot());
            number(output, command.type().wireCode());
            number(output, command.startingBidMinor());
            number(output, command.buyoutMinor());
            rules(output, command.rules());
            number(output, command.type().acceptsBids()
                ? Math.subtractExact(command.deadlineMillis(), command.createdAtMillis())
                : 0L);
        });
    }

    public static String bid(PlaceAuctionBidCommand command) {
        return hash(output -> {
            text(output, "auction_request_v1");
            text(output, "bid");
            id(output, command.requestId());
            id(output, command.listingId());
            number(output, command.expectedRevision());
            id(output, command.bidId());
            id(output, command.bidderId());
            id(output, command.holdAccountId());
            id(output, command.holdTransactionId());
            number(output, command.amountMinor());
            number(output, command.heldDeltaMinor());
        });
    }

    public static String buyNow(AuctionBuyNowCommand command) {
        return hash(output -> {
            text(output, "auction_request_v1");
            text(output, "buy_now");
            id(output, command.requestId());
            id(output, command.listingId());
            number(output, command.expectedRevision());
            id(output, command.buyerId());
            id(output, command.holdAccountId());
            id(output, command.holdTransactionId());
            id(output, command.settlementTransactionId());
            number(output, command.heldDeltaMinor());
        });
    }

    public static String cancel(CancelAuctionCommand command) {
        return hash(output -> {
            text(output, "auction_request_v1");
            text(output, "cancel");
            id(output, command.requestId());
            id(output, command.listingId());
            number(output, command.expectedRevision());
            id(output, command.actorId());
            id(output, command.terminalTransactionId());
            number(output, command.forced() ? 1L : 0L);
        });
    }

    public static String expire(ExpireAuctionCommand command) {
        return hash(output -> {
            text(output, "auction_request_v1");
            text(output, "expire");
            id(output, command.requestId());
            id(output, command.listingId());
            number(output, command.expectedRevision());
            id(output, command.terminalTransactionId());
        });
    }

    public static String freeze(FreezeAuctionCommand command) {
        return hash(output -> {
            text(output, "auction_request_v1");
            text(output, "freeze");
            id(output, command.requestId());
            id(output, command.listingId());
            number(output, command.expectedRevision());
        });
    }

    public static String resume(ResumeAuctionCommand command) {
        return hash(output -> {
            text(output, "auction_request_v1");
            text(output, "resume");
            id(output, command.requestId());
            id(output, command.listingId());
            number(output, command.expectedRevision());
        });
    }

    public static String settle(SettleAuctionCommand command) {
        return hash(output -> {
            text(output, "auction_request_v1");
            text(output, "settle");
            id(output, command.requestId());
            id(output, command.listingId());
            number(output, command.expectedRevision());
        });
    }

    private static void lot(DataOutputStream output, AuctionItemLot lot) throws IOException {
        id(output, lot.custodyLotId());
        text(output, lot.registryId());
        text(output, lot.fingerprint());
        number(output, lot.count());
        number(output, lot.serializedBytes());
        text(output, lot.categoryId());
        text(output, lot.searchDocument());
    }

    private static void rules(DataOutputStream output, AuctionRuleSnapshot rules) throws IOException {
        number(output, rules.listingFeeMinor());
        number(output, rules.saleTaxBasisPoints());
        number(output, rules.minimumIncrementMinor());
        number(output, rules.minimumIncrementBasisPoints());
        truth(output, rules.antiSnipeEnabled());
        number(output, rules.antiSnipeTriggerMillis());
        number(output, rules.antiSnipeExtensionMillis());
        number(output, rules.maximumAntiSnipeCumulativeMillis());
        number(output, rules.maximumAntiSnipeExtensionCount());
        truth(output, rules.allowSellerCancelBeforeBid());
        number(output, rules.timeBasis().wireCode());
        truth(output, rules.pauseWhileFrozen());
        number(output, rules.configRevision());
    }

    private static String hash(Writer writer) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                writer.write(output);
            }
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes.toByteArray()));
        } catch (IOException exception) {
            throw new IllegalStateException("Auction fingerprint encoding failed.", exception);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Auction fingerprint algorithm is unavailable.", exception);
        }
    }

    private static void id(DataOutputStream output, UUID id) throws IOException {
        output.writeLong(id.getMostSignificantBits());
        output.writeLong(id.getLeastSignificantBits());
    }

    private static void number(DataOutputStream output, long value) throws IOException {
        output.writeLong(value);
    }

    private static void truth(DataOutputStream output, boolean value) throws IOException {
        output.writeBoolean(value);
    }

    private static void text(DataOutputStream output, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    @FunctionalInterface
    private interface Writer {
        void write(DataOutputStream output) throws IOException;
    }
}

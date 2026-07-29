package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.catalog.offer.AcquireOfferOption;
import com.enviouse.futureshops.catalog.offer.OfferAction;
import com.enviouse.futureshops.catalog.offer.OfferLimitPolicy;
import com.enviouse.futureshops.catalog.offer.SellOfferOption;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record ServerShopOfferReplayReceipt(
        UUID requestId,
        Kind kind,
        String requestFingerprint,
        ServerShopOfferService.Status status,
        List<UsageEvidence> usageEvidence
) {
    public static final int MAXIMUM_USAGE_LINES =
            ServerShopOfferCartCommit.MAXIMUM_LINES;

    public ServerShopOfferReplayReceipt {
        java.util.Objects.requireNonNull(requestId, "requestId");
        java.util.Objects.requireNonNull(kind, "kind");
        requestFingerprint = java.util.Objects.requireNonNull(
                requestFingerprint, "requestFingerprint");
        java.util.Objects.requireNonNull(status, "status");
        usageEvidence = List.copyOf(usageEvidence);
        UUID player = usageEvidence.isEmpty()
                ? null : usageEvidence.get(0).playerId();
        String shop = usageEvidence.isEmpty()
                ? "" : usageEvidence.get(0).shopId();
        long committedAt = usageEvidence.isEmpty()
                ? -1L : usageEvidence.get(0).committedAtEpoch();
        boolean successful = status
                == ServerShopOfferService.Status.SUCCESS
                || status
                == ServerShopOfferService.Status.CLAIMS_PENDING;
        boolean terminalFailure = isDurableTerminalFailure(status);
        if (requestId.equals(new UUID(0L, 0L))
                || !requestFingerprint.matches("[0-9a-f]{64}")
                || !successful && !terminalFailure
                || successful && usageEvidence.isEmpty()
                || terminalFailure && !usageEvidence.isEmpty()
                || usageEvidence.size() > MAXIMUM_USAGE_LINES
                || successful && kind == Kind.SINGLE
                && usageEvidence.size() != 1
                || successful && kind == Kind.CART
                && usageEvidence.stream().anyMatch(evidence ->
                evidence.action() != OfferAction.ACQUIRE_FROM_SHOP)
                || usageEvidence.stream().anyMatch(evidence ->
                !evidence.requestId().equals(requestId)
                        || !evidence.playerId().equals(player)
                        || !evidence.shopId().equals(shop)
                        || evidence.committedAtEpoch()
                        != committedAt)) {
            throw new IllegalArgumentException(
                    "Server shop offer replay receipt is invalid");
        }
    }

    public static ServerShopOfferReplayReceipt terminal(
            ServerShopOfferService.Request request,
            ServerShopOfferService.Status status
    ) {
        java.util.Objects.requireNonNull(request, "request");
        return new ServerShopOfferReplayReceipt(
                request.requestId(), Kind.SINGLE,
                request.fingerprint(), status, List.of());
    }

    public static ServerShopOfferReplayReceipt terminal(
            ServerShopOfferCartService.Request request,
            ServerShopOfferService.Status status
    ) {
        java.util.Objects.requireNonNull(request, "request");
        return new ServerShopOfferReplayReceipt(
                request.requestId(), Kind.CART,
                request.fingerprint(), status, List.of());
    }

    public boolean successful() {
        return status == ServerShopOfferService.Status.SUCCESS
                || status
                == ServerShopOfferService.Status.CLAIMS_PENDING;
    }

    public static boolean isDurableTerminalFailure(
            ServerShopOfferService.Status status
    ) {
        return switch (java.util.Objects.requireNonNull(
                status, "status")) {
            case INVALID_REQUEST, STALE_REVISION, NOT_FOUND,
                    NOT_AVAILABLE, OUT_OF_STOCK, LIMIT_REACHED,
                    COOLDOWN, REJECTED, CANCELLED_BY_EVENT -> true;
            default -> false;
        };
    }

    public static ServerShopOfferReplayReceipt single(
            ServerShopOfferPreparedSavedData.Entry prepared,
            ServerShopOfferCommit commit
    ) {
        java.util.Objects.requireNonNull(prepared, "prepared");
        java.util.Objects.requireNonNull(commit, "commit");
        if (!prepared.requestId().equals(commit.requestId())
                || !prepared.playerId().equals(commit.playerId())
                || !prepared.shopId().equals(commit.shopId())
                || !prepared.listingId().equals(commit.listingId())
                || !prepared.optionId().equals(commit.optionId())
                || prepared.action() != commit.action()
                || prepared.quantity() != commit.quantity()
                || prepared.offerRevision() != commit.offerRevision()
                || !prepared.paymentSource().equals(
                commit.paymentSource())
                || !prepared.quotedAt().equals(commit.quotedAt())) {
            throw new IllegalArgumentException(
                    "Server shop offer replay evidence conflicts");
        }
        OfferLimitPolicy optionLimits;
        long capacity;
        if (prepared.action()
                == OfferAction.ACQUIRE_FROM_SHOP) {
            AcquireOfferOption option = prepared.listing()
                    .acquireOptions().stream()
                    .filter(value -> value.optionId().equals(
                            prepared.optionId()))
                    .findFirst().orElseThrow();
            optionLimits = option.limits();
            capacity = 0L;
        } else {
            SellOfferOption option = prepared.listing()
                    .sellOptions().stream()
                    .filter(value -> value.optionId().equals(
                            prepared.optionId()))
                    .findFirst().orElseThrow();
            optionLimits = option.limits();
            capacity = option.capacity();
        }
        ServerShopOfferService.Request request =
                new ServerShopOfferService.Request(
                        prepared.requestId(), prepared.playerId(),
                        prepared.shopId(), prepared.listingId(),
                        prepared.optionId(), prepared.action(),
                        prepared.quantity(), prepared.offerRevision(),
                        prepared.paymentSource(), 0);
        return new ServerShopOfferReplayReceipt(
                prepared.requestId(), Kind.SINGLE,
                request.fingerprint(),
                commit.claimsPending()
                        ? ServerShopOfferService.Status.CLAIMS_PENDING
                        : ServerShopOfferService.Status.SUCCESS,
                List.of(new UsageEvidence(
                        prepared.requestId(), prepared.playerId(),
                        prepared.shopId(), prepared.listingId(),
                        prepared.optionId(), prepared.action(),
                        prepared.quantity(),
                        prepared.listing().limits(), optionLimits,
                        capacity, prepared.quotedAt().getEpochSecond())));
    }

    public static ServerShopOfferReplayReceipt cart(
            ServerShopOfferCartPreparedSavedData.Entry prepared,
            ServerShopOfferCartCommit commit
    ) {
        java.util.Objects.requireNonNull(prepared, "prepared");
        java.util.Objects.requireNonNull(commit, "commit");
        if (!prepared.requestId().equals(commit.requestId())
                || !prepared.playerId().equals(commit.playerId())
                || !prepared.shopId().equals(commit.shopId())
                || !prepared.paymentSource().equals(
                commit.paymentSource())
                || !prepared.quotedAt().equals(commit.quotedAt())
                || prepared.lines().size() != commit.lines().size()) {
            throw new IllegalArgumentException(
                    "Server shop offer cart replay evidence conflicts");
        }
        List<UsageEvidence> evidence =
                new ArrayList<>(prepared.lines().size());
        for (int index = 0;
             index < prepared.lines().size(); index++) {
            ServerShopOfferCartPreparedSavedData.QuotedLine line =
                    prepared.lines().get(index);
            ServerShopOfferCartCommit.Line captured =
                    ServerShopOfferCartCommit.captureLine(
                            line.listing(), line.optionId(),
                            line.quantity(), line.savings());
            if (!captured.equals(commit.lines().get(index))) {
                throw new IllegalArgumentException(
                        "Server shop offer cart line replay evidence conflicts");
            }
            AcquireOfferOption option = line.listing()
                    .acquireOptions().stream()
                    .filter(value -> value.optionId().equals(
                            line.optionId()))
                    .findFirst().orElseThrow();
            evidence.add(new UsageEvidence(
                    prepared.requestId(), prepared.playerId(),
                    prepared.shopId(),
                    line.listing().listingId(), line.optionId(),
                    OfferAction.ACQUIRE_FROM_SHOP, line.quantity(),
                    line.listing().limits(), option.limits(), 0L,
                    prepared.quotedAt().getEpochSecond()));
        }
        return new ServerShopOfferReplayReceipt(
                prepared.requestId(), Kind.CART,
                prepared.requestFingerprint(),
                commit.claimsPending()
                        ? ServerShopOfferService.Status.CLAIMS_PENDING
                        : ServerShopOfferService.Status.SUCCESS,
                evidence);
    }

    public boolean matches(ServerShopOfferService.Request request) {
        return kind == Kind.SINGLE
                && requestId.equals(request.requestId())
                && requestFingerprint.equals(request.fingerprint());
    }

    public UUID playerId() {
        if (usageEvidence.isEmpty()) {
            throw new IllegalStateException(
                    "Terminal replay receipt has no usage player");
        }
        return usageEvidence.get(0).playerId();
    }

    public boolean matches(ServerShopOfferPreparedSavedData.Entry entry) {
        return matches(new ServerShopOfferService.Request(
                entry.requestId(), entry.playerId(), entry.shopId(),
                entry.listingId(), entry.optionId(), entry.action(),
                entry.quantity(), entry.offerRevision(),
                entry.paymentSource(), 0));
    }

    public boolean matches(ServerShopOfferCommit commit) {
        ServerShopOfferService.Request request =
                new ServerShopOfferService.Request(
                        commit.requestId(), commit.playerId(),
                        commit.shopId(), commit.listingId(),
                        commit.optionId(), commit.action(),
                        commit.quantity(), commit.offerRevision(),
                        commit.paymentSource(), 0);
        return matches(request)
                && status == (commit.claimsPending()
                ? ServerShopOfferService.Status.CLAIMS_PENDING
                : ServerShopOfferService.Status.SUCCESS);
    }

    public boolean matches(ServerShopOfferCartService.Request request) {
        return kind == Kind.CART
                && requestId.equals(request.requestId())
                && requestFingerprint.equals(request.fingerprint());
    }

    public boolean matches(
            ServerShopOfferCartPreparedSavedData.Entry entry
    ) {
        return kind == Kind.CART
                && requestId.equals(entry.requestId())
                && requestFingerprint.equals(
                entry.requestFingerprint());
    }

    public boolean matches(ServerShopOfferCartCommit commit) {
        List<ServerShopOfferCartService.LineRequest> lines =
                commit.lines().stream().map(line ->
                        new ServerShopOfferCartService.LineRequest(
                                line.listingId(), line.optionId(),
                                line.quantity(), line.offerRevision()))
                        .toList();
        ServerShopOfferCartService.Request request =
                new ServerShopOfferCartService.Request(
                        commit.requestId(), commit.playerId(),
                        commit.shopId(), lines,
                        commit.paymentSource(), 0);
        return matches(request)
                && status == (commit.claimsPending()
                ? ServerShopOfferService.Status.CLAIMS_PENDING
                : ServerShopOfferService.Status.SUCCESS);
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Request", requestId);
        tag.putString("Kind", kind.name());
        tag.putString("Fingerprint", requestFingerprint);
        tag.putString("Status", status.name());
        ListTag usage = new ListTag();
        for (UsageEvidence evidence : usageEvidence) {
            usage.add(evidence.save());
        }
        tag.put("Usage", usage);
        return tag;
    }

    static ServerShopOfferReplayReceipt load(CompoundTag tag) {
        ListTag usage = tag.getList("Usage", Tag.TAG_COMPOUND);
        if (usage.size() > MAXIMUM_USAGE_LINES) {
            throw new IllegalArgumentException(
                    "Server shop offer replay usage limit is exceeded");
        }
        List<UsageEvidence> evidence =
                new ArrayList<>(usage.size());
        for (int index = 0; index < usage.size(); index++) {
            evidence.add(UsageEvidence.load(
                    usage.getCompound(index)));
        }
        return new ServerShopOfferReplayReceipt(
                tag.getUUID("Request"),
                Kind.valueOf(tag.getString("Kind")),
                tag.getString("Fingerprint"),
                ServerShopOfferService.Status.valueOf(
                        tag.getString("Status")),
                evidence);
    }

    public enum Kind {
        SINGLE,
        CART
    }

    public record UsageEvidence(
            UUID requestId,
            UUID playerId,
            String shopId,
            String listingId,
            String optionId,
            OfferAction action,
            int quantity,
            OfferLimitPolicy listingLimits,
            OfferLimitPolicy optionLimits,
            long capacity,
            long committedAtEpoch
    ) {
        public UsageEvidence {
            java.util.Objects.requireNonNull(requestId, "requestId");
            java.util.Objects.requireNonNull(playerId, "playerId");
            shopId = identifier(shopId);
            listingId = identifier(listingId);
            optionId = identifier(optionId);
            java.util.Objects.requireNonNull(action, "action");
            validateLimits(listingLimits);
            validateLimits(optionLimits);
            if (requestId.equals(new UUID(0L, 0L))
                    || playerId.equals(new UUID(0L, 0L))
                    || quantity <= 0 || quantity > 2304
                    || capacity < 0L || committedAtEpoch < 0L
                    || action == OfferAction.ACQUIRE_FROM_SHOP
                    && capacity != 0L) {
                throw new IllegalArgumentException(
                        "Server shop offer replay usage is invalid");
            }
        }

        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("Request", requestId);
            tag.putUUID("Player", playerId);
            tag.putString("Shop", shopId);
            tag.putString("Listing", listingId);
            tag.putString("Option", optionId);
            tag.putString("Action", action.name());
            tag.putInt("Quantity", quantity);
            tag.put("ListingLimits", saveLimits(listingLimits));
            tag.put("OptionLimits", saveLimits(optionLimits));
            tag.putLong("Capacity", capacity);
            tag.putLong("CommittedAt", committedAtEpoch);
            return tag;
        }

        private static UsageEvidence load(CompoundTag tag) {
            return new UsageEvidence(
                    tag.getUUID("Request"),
                    tag.getUUID("Player"),
                    tag.getString("Shop"),
                    tag.getString("Listing"),
                    tag.getString("Option"),
                    OfferAction.valueOf(tag.getString("Action")),
                    tag.getInt("Quantity"),
                    loadLimits(tag.getCompound("ListingLimits")),
                    loadLimits(tag.getCompound("OptionLimits")),
                    tag.getLong("Capacity"),
                    tag.getLong("CommittedAt"));
        }
    }

    private static CompoundTag saveLimits(OfferLimitPolicy limits) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("MaximumPerRequest",
                limits.maximumPerRequest());
        tag.putLong("Lifetime", limits.lifetimeLimit());
        tag.putLong("Period", limits.periodLimit());
        tag.putLong("PeriodSeconds", limits.periodSeconds());
        tag.putLong("CooldownSeconds", limits.cooldownSeconds());
        return tag;
    }

    private static OfferLimitPolicy loadLimits(CompoundTag tag) {
        return new OfferLimitPolicy(
                tag.getInt("MaximumPerRequest"),
                tag.getLong("Lifetime"),
                tag.getLong("Period"),
                tag.getLong("PeriodSeconds"),
                tag.getLong("CooldownSeconds"));
    }

    private static void validateLimits(OfferLimitPolicy limits) {
        java.util.Objects.requireNonNull(limits, "limits");
        if (limits.maximumPerRequest() <= 0
                || limits.maximumPerRequest() > 2304
                || limits.lifetimeLimit() < 0L
                || limits.periodLimit() < 0L
                || limits.periodSeconds() < 0L
                || limits.cooldownSeconds() < 0L
                || limits.periodLimit() > 0L
                && limits.periodSeconds() <= 0L) {
            throw new IllegalArgumentException(
                    "Server shop offer replay limits are invalid");
        }
    }

    private static String identifier(String value) {
        String normalized = java.util.Objects.requireNonNull(
                value, "identifier").strip();
        if (normalized.isEmpty() || normalized.length() > 160) {
            throw new IllegalArgumentException(
                    "Server shop offer replay identifier is invalid");
        }
        return normalized;
    }
}

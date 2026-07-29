package com.enviouse.futureshops.server.shop;

import com.enviouse.futureshops.block.ShopBlockEntity;
import com.enviouse.futureshops.catalog.AdminShopOfferConfigWriter;
import com.enviouse.futureshops.catalog.offer.OfferValidationIssue;
import com.enviouse.futureshops.catalog.offer.OfferValidationResult;
import com.enviouse.futureshops.catalog.offer.ServerShopOfferListing;
import com.enviouse.futureshops.catalog.offer.ServerShopOfferRevision;
import com.enviouse.futureshops.catalog.offer.ServerShopOfferValidator;
import com.enviouse.futureshops.network.ShopPackets;
import com.enviouse.futureshops.network.packets
        .C2SPlayerShopOfferSavePacket;
import com.enviouse.futureshops.network.packets
        .S2CPlayerShopOfferSaveResultPacket;
import com.enviouse.futureshops.server.security.ServerRequestAction;
import com.enviouse.futureshops.server.security
        .ServerRequestSecurityManager;
import com.enviouse.futureshops.server.session.ShopSessionManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

public final class PlayerShopOfferEditorService {
    private static final double MAXIMUM_EDIT_DISTANCE_SQUARED = 64.0D;

    private PlayerShopOfferEditorService() {
    }

    public static void save(
            ServerPlayer player,
            C2SPlayerShopOfferSavePacket packet
    ) {
        if (!player.level().hasChunkAt(packet.shopPos())
                || !(player.level().getBlockEntity(packet.shopPos())
                instanceof ShopBlockEntity shop)) {
            send(player, packet,
                    AdminShopOfferConfigWriter.Status.NOT_FOUND,
                    Optional.empty(), List.of(issue(
                            "shopPos",
                            "offer.player_shop.not_found")));
            return;
        }
        if (shop.getOwnerUuid() == null
                || !shop.getOwnerUuid().equals(player.getUUID())) {
            send(player, packet,
                    AdminShopOfferConfigWriter.Status.CONFLICT,
                    Optional.empty(), List.of(issue(
                            "permission",
                            "offer.player_shop.not_owner")));
            return;
        }
        if (!hasEditAccess(player, packet)) {
            send(player, packet,
                    AdminShopOfferConfigWriter.Status.CONFLICT,
                    Optional.empty(), List.of(issue(
                            "shopPos",
                            "offer.player_shop.out_of_range")));
            return;
        }
        ServerRequestSecurityManager.GateDecision gate =
                ServerRequestSecurityManager.tryAcquire(
                        player,
                        ServerRequestAction.PLAYER_SHOP_OFFER_ADMIN);
        if (!gate.allowed()) {
            send(player, packet,
                    AdminShopOfferConfigWriter.Status.UNAVAILABLE,
                    Optional.empty(), List.of(issue(
                            "requestId",
                            "offer.save.rate_limited")));
            return;
        }

        ReentrantLock lock = PlayerShopBlockService.transactionLock(
                packet.shopPos());
        lock.lock();
        try {
            ShopBlockEntity.Listing listing =
                    shop.getListing(packet.listingIndex());
            if (listing == null
                    || !listing.listingId().equals(packet.listingId())) {
                send(player, packet,
                        AdminShopOfferConfigWriter.Status.NOT_FOUND,
                        Optional.empty(), List.of(issue(
                                "listingId",
                                "offer.player_shop.listing_missing")));
                return;
            }
            Optional<ServerShopOfferListing> current =
                    listing.normalizedOffer();
            if (listing.offerUnavailable() || current.isEmpty()) {
                send(player, packet,
                        AdminShopOfferConfigWriter.Status.UNAVAILABLE,
                        current, List.of(issue(
                                "listingId",
                                "offer.player_shop.offer_unavailable")));
                return;
            }
            MutationValidation validation = validateMutation(
                    current.orElseThrow(), packet.expectedRevision(),
                    packet.listingId(), packet.candidate());
            if (!validation.success()) {
                send(player, packet, validation.status(),
                        validation.snapshot(), validation.issues());
                return;
            }
            ServerShopOfferListing normalized =
                    validation.snapshot().orElseThrow();
            if (!normalized.equals(current.orElseThrow())) {
                listing.setNormalizedOffer(normalized);
                shop.setChanged();
            }
            send(player, packet,
                    AdminShopOfferConfigWriter.Status.SUCCESS,
                    Optional.of(normalized), List.of());
            PlayerShopBlockService.openFor(
                    player, packet.shopPos());
        } catch (RuntimeException exception) {
            send(player, packet,
                    AdminShopOfferConfigWriter.Status.IO_ERROR,
                    Optional.empty(), List.of(issue(
                            "listingId",
                            "offer.player_shop.save_failed")));
        } finally {
            lock.unlock();
        }
    }

    static MutationValidation validateMutation(
            ServerShopOfferListing current,
            long expectedRevision,
            String listingId,
            ServerShopOfferListing candidate
    ) {
        if (!current.listingId().equals(listingId)
                || !candidate.listingId().equals(listingId)) {
            return MutationValidation.failure(
                    AdminShopOfferConfigWriter.Status.CONFLICT,
                    Optional.of(current), List.of(issue(
                            "listingId",
                            "offer.listing.id.change_not_allowed")));
        }
        ServerShopOfferListing normalized =
                candidate.withRevision(
                        ServerShopOfferRevision.compute(candidate));
        OfferValidationResult validation =
                ServerShopOfferValidator.validate(
                        normalized,
                        PlayerShopOfferEditorService::knownItem,
                        PlayerShopOfferEditorService::validNbt,
                        com.enviouse.futureshops.catalog.offer
                                .OfferEscrowFanout
                                ::registeredMaximumStackSize);
        if (!validation.valid()) {
            return MutationValidation.failure(
                    AdminShopOfferConfigWriter.Status.INVALID,
                    Optional.of(current), validation.issues());
        }
        if (expectedRevision != current.revision()) {
            if (current.equals(normalized)) {
                return MutationValidation.success(current);
            }
            return MutationValidation.failure(
                    AdminShopOfferConfigWriter.Status.STALE,
                    Optional.of(current), List.of(issue(
                            "revision",
                            "offer.player_shop.stale")));
        }
        return MutationValidation.success(normalized);
    }

    private static boolean hasEditAccess(
            ServerPlayer player,
            C2SPlayerShopOfferSavePacket packet
    ) {
        boolean session = ShopSessionManager.get(player.getUUID())
                .filter(value -> packet.shopPos().equals(
                        value.shopBlockPos()))
                .filter(value -> value.shopId().equals(
                        "player_shop:" + packet.shopPos().asLong()))
                .isPresent();
        return session || player.getEyePosition().distanceToSqr(
                Vec3.atCenterOf(packet.shopPos()))
                <= MAXIMUM_EDIT_DISTANCE_SQUARED;
    }

    private static boolean knownItem(String itemId) {
        ResourceLocation identifier = ResourceLocation.tryParse(itemId);
        return identifier != null
                && !"minecraft:air".equals(itemId)
                && BuiltInRegistries.ITEM.containsKey(identifier);
    }

    private static boolean validNbt(String nbt) {
        if (nbt == null || nbt.isBlank()) {
            return true;
        }
        try {
            TagParser.parseTag(nbt);
            return true;
        } catch (Exception exception) {
            return false;
        }
    }

    private static OfferValidationIssue issue(
            String path,
            String code
    ) {
        return new OfferValidationIssue(
                OfferValidationIssue.Severity.ERROR,
                path, code);
    }

    private static void send(
            ServerPlayer player,
            C2SPlayerShopOfferSavePacket request,
            AdminShopOfferConfigWriter.Status status,
            Optional<ServerShopOfferListing> snapshot,
            List<OfferValidationIssue> issues
    ) {
        boolean success =
                status == AdminShopOfferConfigWriter.Status.SUCCESS;
        long revision = snapshot.map(
                ServerShopOfferListing::revision).orElse(0L);
        ShopPackets.sendToPlayer(player,
                new S2CPlayerShopOfferSaveResultPacket(
                        request.requestId(), status, success,
                        revision, snapshot, issues));
    }

    record MutationValidation(
            AdminShopOfferConfigWriter.Status status,
            Optional<ServerShopOfferListing> snapshot,
            List<OfferValidationIssue> issues
    ) {
        MutationValidation {
            snapshot = Optional.ofNullable(
                    snapshot.orElse(null));
            issues = List.copyOf(issues);
        }

        boolean success() {
            return status
                    == AdminShopOfferConfigWriter.Status.SUCCESS;
        }

        static MutationValidation success(
                ServerShopOfferListing snapshot
        ) {
            return new MutationValidation(
                    AdminShopOfferConfigWriter.Status.SUCCESS,
                    Optional.of(snapshot), List.of());
        }

        static MutationValidation failure(
                AdminShopOfferConfigWriter.Status status,
                Optional<ServerShopOfferListing> snapshot,
                List<OfferValidationIssue> issues
        ) {
            return new MutationValidation(status, snapshot, issues);
        }
    }
}

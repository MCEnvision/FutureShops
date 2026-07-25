package com.enviouse.futureshops.client.market;

import com.enviouse.futureshops.client.screen.ShopUiUtil;
import com.enviouse.futureshops.network.packets.S2CMarketActionResponsePacket;
import com.enviouse.futureshops.server.market.auction.AuctionOperationStatus;
import com.enviouse.futureshops.server.market.bazaar.BazaarOperationStatus;
import net.minecraft.network.chat.Component;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Localizes {@link S2CMarketActionResponsePacket} results for the market screen (plan §12: the
 * wire carries stable status tokens, never pre-rendered English). Known status tokens map to
 * {@code gui.futureshops.market.action.status.<lowercase_code>}; anything the client does not
 * recognize falls back to a generic key with the raw code as its argument, so a newer server
 * never renders a blank line. Stale-revision family statuses are singled out because plan §15
 * requires stale interfaces to refresh rather than execute.
 */
public final class MarketActionFeedback {
    /** Statuses meaning "the entity moved on since the client rendered it" — refresh, not fail. */
    private static final Set<String> STALE_STATUSES = Set.of(
            "STALE_REVISION", "REVISION_CHANGED", "PRODUCT_VERSION_CHANGED");

    /**
     * Ad-hoc service statuses that ride outside the module operation-status enums but still
     * have dedicated lang lines: ITEMS_MISSING (create/order item pre-checks, including the
     * plan §8 step 7 fingerprint mismatch) and PAYMENT_SOURCE_DENIED (plan §6: the chosen
     * payment source is not accepted by server policy).
     */
    private static final Set<String> EXTRA_STATUSES = Set.of(
            "ITEMS_MISSING", "PAYMENT_SOURCE_DENIED",
            "RECOVERY_REQUIRED");

    /**
     * Status tokens with a dedicated lang key. Built from the module operation-status enums so
     * a new enum constant without a lang line simply falls back to the generic key instead of
     * silently drifting.
     */
    private static final Set<String> KNOWN_STATUSES = knownStatuses();

    private MarketActionFeedback() {
    }

    private static Set<String> knownStatuses() {
        Set<String> known = new HashSet<>();
        for (AuctionOperationStatus status : AuctionOperationStatus.values()) {
            known.add(status.name());
        }
        for (BazaarOperationStatus status : BazaarOperationStatus.values()) {
            known.add(status.name());
        }
        known.addAll(EXTRA_STATUSES);
        return Set.copyOf(known);
    }

    public static boolean stale(String status) {
        return status != null && STALE_STATUSES.contains(status);
    }

    /**
     * Maps a response's module + action tokens to the client action family used for success
     * localization ({@code gui.futureshops.market.action.success.<key>}). Used when the local
     * pending tracker does not know the request (e.g. the screen was rebuilt mid-flight).
     */
    public static String actionKey(String moduleId, String action) {
        String normalized = action == null ? ""
                : action.toLowerCase(Locale.ROOT);
        if ("auction_house".equals(moduleId)) {
            return switch (normalized) {
                case "create" -> "auction_create";
                case "bid" -> "auction_bid";
                case "buy_now" -> "auction_buy_now";
                case "cancel" -> "auction_cancel";
                default -> "generic";
            };
        }
        if ("bazaar".equals(moduleId)) {
            return switch (normalized) {
                case "create", "order" -> "bazaar_order";
                case "register" -> "bazaar_register";
                case "cancel" -> "bazaar_cancel";
                default -> "generic";
            };
        }
        return "generic";
    }

    public static Component successMessage(String actionKey) {
        String key = switch (actionKey) {
            case "auction_create", "auction_bid", "auction_buy_now",
                    "auction_cancel", "bazaar_order", "bazaar_cancel" ->
                    actionKey;
            case "bazaar_register" -> actionKey;
            default -> "generic";
        };
        return Component.translatable(
                "gui.futureshops.market.action.success." + key);
    }

    public static Component successMessage(
            String actionKey,
            S2CMarketActionResponsePacket response
    ) {
        if ("bazaar_cancel".equals(actionKey)) {
            var detail = S2CMarketActionResponsePacket
                    .parseBazaarCancelDetail(response.detail())
                    .filter(value -> value.refundMinorUnits() > 0L);
            if (detail.isPresent()) {
                return Component.translatable(
                        "gui.futureshops.market.action.success."
                                + "bazaar_cancel_refund",
                        ShopUiUtil.formatMinorUnits(
                                detail.orElseThrow().refundMinorUnits()));
            }
        }
        return successMessage(actionKey);
    }

    /** The plan §15 line shown alongside the automatic refresh on a stale-family status. */
    public static Component staleMessage(String status) {
        return Component.translatable(
                "gui.futureshops.market.action.status."
                        + status.toLowerCase(Locale.ROOT));
    }

    public static Component failureMessage(
            S2CMarketActionResponsePacket packet
    ) {
        String status = packet.status();
        if ("ITEMS_MISSING".equals(status)
                && "fingerprint".equals(packet.detail())) {
            // Plan §8 step 7: the live slot content changed between the client's
            // selection-time fingerprint and the server's processing.
            return Component.translatable(
                    "gui.futureshops.market.action.status.items_missing.fingerprint");
        }
        if ("BID_TOO_LOW".equals(status) && !packet.detail().isEmpty()) {
            // The detail slot carries the required minimum in minor units when the
            // server provides it (a machine argument, localized here).
            try {
                long minimumMinor = Long.parseLong(packet.detail());
                return Component.translatable(
                        "gui.futureshops.market.action.status.bid_too_low.minimum",
                        ShopUiUtil.formatMinorUnits(minimumMinor));
            } catch (NumberFormatException ignored) {
                // Fall through to the plain key.
            }
        }
        if (KNOWN_STATUSES.contains(status)) {
            return Component.translatable(
                    "gui.futureshops.market.action.status."
                            + status.toLowerCase(Locale.ROOT));
        }
        return Component.translatable(
                "gui.futureshops.market.action.status.generic", status);
    }

    /**
     * Shown when a request outlives the client timeout. The request stays tracked: the surface
     * offers Retry (resend of the SAME request UUID — the server replays the stored result
     * idempotently) or an explicit give-up that refreshes, so this line must not suggest simply
     * pressing the original button again.
     */
    public static Component timeoutMessage() {
        return Component.translatable(
                "gui.futureshops.market.action.status.timeout_retry");
    }
}

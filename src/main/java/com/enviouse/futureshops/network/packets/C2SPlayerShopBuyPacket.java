package com.enviouse.futureshops.network.packets;

import com.enviouse.futureshops.server.shop.PlayerShopBlockService;
import com.enviouse.futureshops.server.transaction.ShopTransactionUtil;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.Objects;
import java.util.function.Supplier;
import java.util.UUID;

/**
 * Player shop purchase request.
 * Payment method selects money or barter.
 * Payment source selects wallet or inventory cash.
 */
public record C2SPlayerShopBuyPacket(BlockPos shopPos, int listingIndex, int quantity,
                                     String paymentMethod, String paymentSource,
                                     UUID requestId, int responseToken) {
    private static final UUID UNCORRELATED_REQUEST_ID = new UUID(0L, 0L);
    public static final int MAX_PAYMENT_METHOD_LENGTH = 32;
    public static final int MAX_PAYMENT_SOURCE_LENGTH = 32;
    public static final int MAX_LISTING_INDEX = ShopTransactionUtil.MAX_PLAYER_SHOP_LISTING_INDEX;
    public static final int MAX_RESPONSE_TOKEN = ShopTransactionUtil.MAX_PLAYER_SHOP_CART_LINES - 1;

    public C2SPlayerShopBuyPacket {
        shopPos = Objects.requireNonNull(shopPos, "shopPos");
        if (!ShopTransactionUtil.isValidPlayerShopListingIndex(listingIndex)) {
            throw new IllegalArgumentException("listingIndex is outside the wire limit");
        }
        if (!ShopTransactionUtil.isValidBuyQuantity(quantity)) {
            throw new IllegalArgumentException("quantity is outside the buy limit");
        }
        paymentMethod = requireBoundedString(paymentMethod, MAX_PAYMENT_METHOD_LENGTH, "paymentMethod");
        paymentSource = requireBoundedString(paymentSource, MAX_PAYMENT_SOURCE_LENGTH, "paymentSource");
        requestId = Objects.requireNonNull(requestId, "requestId");
        if (UNCORRELATED_REQUEST_ID.equals(requestId)) {
            throw new IllegalArgumentException("requestId must be nonzero");
        }
        if (!ShopTransactionUtil.isValidPlayerShopResponseToken(responseToken)) {
            throw new IllegalArgumentException("responseToken is outside the cart line limit");
        }
    }

    public C2SPlayerShopBuyPacket(BlockPos shopPos, int listingIndex, int quantity,
                                  String paymentMethod, String paymentSource) {
        this(shopPos, listingIndex, quantity, paymentMethod, paymentSource,
                UUID.randomUUID(), 0);
    }

    public static void encode(C2SPlayerShopBuyPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(packet.shopPos());
        buffer.writeVarInt(packet.listingIndex());
        buffer.writeVarInt(packet.quantity());
        buffer.writeUtf(packet.paymentMethod(), MAX_PAYMENT_METHOD_LENGTH);
        buffer.writeUtf(packet.paymentSource(), MAX_PAYMENT_SOURCE_LENGTH);
        buffer.writeUUID(packet.requestId());
        buffer.writeVarInt(packet.responseToken());
    }

    public static C2SPlayerShopBuyPacket decode(FriendlyByteBuf buffer) {
        try {
            BlockPos shopPos = buffer.readBlockPos();
            int listingIndex = buffer.readVarInt();
            if (!ShopTransactionUtil.isValidPlayerShopListingIndex(listingIndex)) {
                throw new DecoderException("player shop listing index is outside the wire limit");
            }
            int quantity = buffer.readVarInt();
            if (!ShopTransactionUtil.isValidBuyQuantity(quantity)) {
                throw new DecoderException("player shop quantity is outside the buy limit");
            }
            String paymentMethod = buffer.readUtf(MAX_PAYMENT_METHOD_LENGTH);
            String paymentSource = buffer.readUtf(MAX_PAYMENT_SOURCE_LENGTH);
            UUID requestId = buffer.readUUID();
            if (UNCORRELATED_REQUEST_ID.equals(requestId)) {
                throw new DecoderException(
                        "player shop request identity must be nonzero");
            }
            int responseToken = buffer.readVarInt();
            if (!ShopTransactionUtil.isValidPlayerShopResponseToken(responseToken)) {
                throw new DecoderException("player shop response token is outside the cart line limit");
            }
            return new C2SPlayerShopBuyPacket(
                    shopPos, listingIndex, quantity, paymentMethod, paymentSource, requestId, responseToken);
        } catch (DecoderException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new DecoderException("malformed player shop buy packet", exception);
        }
    }

    private static String requireBoundedString(String value, int maxLength, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " exceeds the wire limit");
        }
        return value;
    }

    public static void handle(C2SPlayerShopBuyPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                PlayerShopBlockService.buy(player, packet.shopPos(), packet.listingIndex(), packet.quantity(),
                        packet.paymentMethod(), packet.paymentSource(), packet.requestId(), packet.responseToken());
            }
        });
        context.setPacketHandled(true);
    }
}

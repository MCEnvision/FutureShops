package com.enviouse.futureshops.network.packets;

import com.enviouse.futureshops.client.market.MarketModule;
import com.enviouse.futureshops.server.market.MarketPageService;
import com.enviouse.futureshops.server.market.query.MarketPageQuery;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.function.Supplier;

public record C2SMarketPageQueryPacket(
        UUID requestId,
        UUID routeNonce,
        String moduleId,
        String view,
        String search,
        String category,
        String sort,
        int pageIndex,
        int pageSize,
        OptionalLong minimumPriceMinor,
        OptionalLong maximumPriceMinor
) {
    public C2SMarketPageQueryPacket {
        minimumPriceMinor = Objects.requireNonNull(
                minimumPriceMinor, "minimumPriceMinor");
        maximumPriceMinor = Objects.requireNonNull(
                maximumPriceMinor, "maximumPriceMinor");
        MarketModule module = MarketModule.fromId(moduleId);
        MarketPageQuery validated = new MarketPageQuery(requestId,
                routeNonce, module, view, search, category, sort,
                pageIndex, pageSize, minimumPriceMinor,
                maximumPriceMinor, 0L);
        moduleId = validated.module().id();
        view = validated.view();
        search = validated.search();
        category = validated.category();
        sort = validated.sort();
    }

    public static void encode(
            C2SMarketPageQueryPacket packet,
            FriendlyByteBuf buffer
    ) {
        buffer.writeUUID(packet.requestId());
        buffer.writeUUID(packet.routeNonce());
        buffer.writeUtf(packet.moduleId(), 32);
        buffer.writeUtf(packet.view(), 32);
        buffer.writeUtf(packet.search(), 128);
        buffer.writeUtf(packet.category(), 128);
        buffer.writeUtf(packet.sort(), 32);
        buffer.writeVarInt(packet.pageIndex());
        buffer.writeVarInt(packet.pageSize());
        writeOptionalLong(buffer, packet.minimumPriceMinor());
        writeOptionalLong(buffer, packet.maximumPriceMinor());
    }

    public static C2SMarketPageQueryPacket decode(
            FriendlyByteBuf buffer
    ) {
        try {
            return new C2SMarketPageQueryPacket(buffer.readUUID(),
                    buffer.readUUID(), buffer.readUtf(32),
                    buffer.readUtf(32), buffer.readUtf(128),
                    buffer.readUtf(128), buffer.readUtf(32),
                    buffer.readVarInt(), buffer.readVarInt(),
                    readOptionalLong(buffer), readOptionalLong(buffer));
        } catch (RuntimeException exception) {
            throw new DecoderException(
                    "Market page query is invalid", exception);
        }
    }

    public static void handle(
            C2SMarketPageQueryPacket packet,
            Supplier<NetworkEvent.Context> supplier
    ) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                MarketPageService.query(player, packet);
            }
        });
        context.setPacketHandled(true);
    }

    public MarketPageQuery toQuery(long serverTimeMillis) {
        return new MarketPageQuery(requestId, routeNonce,
                MarketModule.fromId(moduleId), view, search, category,
                sort, pageIndex, pageSize, minimumPriceMinor,
                maximumPriceMinor, serverTimeMillis);
    }

    public String fingerprint() {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                writeUuid(output, requestId);
                writeUuid(output, routeNonce);
                writeText(output, moduleId);
                writeText(output, view);
                writeText(output, search);
                writeText(output, category);
                writeText(output, sort);
                output.writeInt(pageIndex);
                output.writeInt(pageSize);
                writeOptionalLong(output, minimumPriceMinor);
                writeOptionalLong(output, maximumPriceMinor);
            }
            return HexFormat.of().formatHex(MessageDigest.getInstance(
                    "SHA-256").digest(bytes.toByteArray()));
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "Market query fingerprint failed", exception);
        }
    }

    private static void writeOptionalLong(
            FriendlyByteBuf buffer,
            OptionalLong value
    ) {
        buffer.writeBoolean(value.isPresent());
        if (value.isPresent()) {
            buffer.writeLong(value.getAsLong());
        }
    }

    private static OptionalLong readOptionalLong(
            FriendlyByteBuf buffer
    ) {
        return buffer.readBoolean()
                ? OptionalLong.of(buffer.readLong())
                : OptionalLong.empty();
    }

    private static void writeUuid(
            DataOutputStream output,
            UUID value
    ) throws IOException {
        output.writeLong(value.getMostSignificantBits());
        output.writeLong(value.getLeastSignificantBits());
    }

    private static void writeText(
            DataOutputStream output,
            String value
    ) throws IOException {
        byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
        output.writeInt(encoded.length);
        output.write(encoded);
    }

    private static void writeOptionalLong(
            DataOutputStream output,
            OptionalLong value
    ) throws IOException {
        output.writeBoolean(value.isPresent());
        if (value.isPresent()) {
            output.writeLong(value.getAsLong());
        }
    }
}

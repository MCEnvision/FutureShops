package com.enviouse.futureshopsp.money;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * Immutable value of the {@code futureshops:coin_data} data component — the 1.21.1
 * replacement for the legacy {@code futureshops:coin_data} ItemStack NBT compound.
 *
 * <p>Field names in {@link #CODEC} intentionally equal the legacy {@link MoneyNbtKeys}
 * sub-keys so the on-disk shape stays stable and the legacy-NBT rescue path (reading an
 * old coin out of {@code minecraft:custom_data}) maps 1:1. The anti-dupe checksum
 * ({@link MoneyChecksumService}) and the world-ledger ({@link SpentMintsSavedData}) are
 * storage-agnostic and unchanged — this record only changes how a coin describes itself.
 */
public record CoinData(long denomination, String mintId, long mintTimestamp,
                       String mintPlayer, String mintServer, int authorizedCount, String checksum) {

    /** Disk/persistent serialization. Keys match the legacy NBT sub-keys. */
    public static final Codec<CoinData> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.LONG.fieldOf(MoneyNbtKeys.DENOMINATION).forGetter(CoinData::denomination),
            Codec.STRING.fieldOf(MoneyNbtKeys.MINT_ID).forGetter(CoinData::mintId),
            Codec.LONG.fieldOf(MoneyNbtKeys.MINT_TIMESTAMP).forGetter(CoinData::mintTimestamp),
            Codec.STRING.fieldOf(MoneyNbtKeys.MINT_PLAYER).forGetter(CoinData::mintPlayer),
            Codec.STRING.fieldOf(MoneyNbtKeys.MINT_SERVER).forGetter(CoinData::mintServer),
            Codec.INT.fieldOf(MoneyNbtKeys.AUTHORIZED_COUNT).forGetter(CoinData::authorizedCount),
            Codec.STRING.fieldOf(MoneyNbtKeys.CHECKSUM).forGetter(CoinData::checksum)
    ).apply(inst, CoinData::new));

    /** Network serialization (7 fields → hand-written; composite() tops out at 6). */
    public static final StreamCodec<RegistryFriendlyByteBuf, CoinData> STREAM_CODEC = StreamCodec.of(
            (buf, coin) -> {
                buf.writeLong(coin.denomination);
                buf.writeUtf(coin.mintId);
                buf.writeLong(coin.mintTimestamp);
                buf.writeUtf(coin.mintPlayer);
                buf.writeUtf(coin.mintServer);
                buf.writeVarInt(coin.authorizedCount);
                buf.writeUtf(coin.checksum);
            },
            buf -> new CoinData(
                    buf.readLong(),
                    buf.readUtf(),
                    buf.readLong(),
                    buf.readUtf(),
                    buf.readUtf(),
                    buf.readVarInt(),
                    buf.readUtf()
            )
    );
}

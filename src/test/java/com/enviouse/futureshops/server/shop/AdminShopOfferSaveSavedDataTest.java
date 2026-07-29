package com.enviouse.futureshops.server.shop;

import com.enviouse.futureshops.catalog.AdminShopOfferConfigWriter;
import com.enviouse.futureshops.network.packets
        .S2CAdminOfferSaveResultPacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;

class AdminShopOfferSaveSavedDataTest {
    @Test
    void duplicateAndMalformedReceiptsFailClosed() {
        UUID requestId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        AdminShopOfferSaveSavedData data =
                new AdminShopOfferSaveSavedData();
        data.record(AdminShopOfferSaveSavedData.Receipt.capture(
                playerId, "a".repeat(64),
                new S2CAdminOfferSaveResultPacket(
                        requestId,
                        AdminShopOfferConfigWriter.Status.SUCCESS,
                        true, 0L, Optional.empty(), List.of())));

        CompoundTag duplicated = data.save(new CompoundTag());
        ListTag duplicateRows = duplicated.getList(
                "Receipts", Tag.TAG_COMPOUND);
        duplicateRows.add(duplicateRows.getCompound(0).copy());
        assertThrows(IllegalArgumentException.class,
                () -> AdminShopOfferSaveSavedData.load(duplicated));

        CompoundTag malformed = data.save(new CompoundTag());
        malformed.getList("Receipts", Tag.TAG_COMPOUND)
                .getCompound(0).putString("Status", "unknown");
        assertThrows(IllegalArgumentException.class,
                () -> AdminShopOfferSaveSavedData.load(malformed));
    }
}

package com.enviouse.futureshops.server.transaction;

import com.enviouse.futureshops.catalog.BarterIngredientDef;
import com.enviouse.futureshops.catalog.BarterRecipeDef;
import com.enviouse.futureshops.catalog.ItemDef;
import com.enviouse.futureshops.server.escrow.runtime.ServerShopBarterCommit;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

final class ShopCatalogEvidenceRevision {
    private ShopCatalogEvidenceRevision() {
    }

    static long item(ItemDef item) {
        Objects.requireNonNull(item, "item");
        return revision(output -> {
            writeText(output, "item v1");
            writeText(output, item.resolutionKey());
            writeText(output, item.itemId());
            writeText(output, item.displayName());
            output.writeLong(item.buyPriceMinorUnits());
            output.writeLong(item.sellPriceMinorUnits());
            output.writeInt(item.stock());
            output.writeBoolean(item.barterEnabled());
            writeText(output, item.categoryId());
            output.writeInt(item.stockRefreshSeconds());
            writeText(output, item.nbtJson());
            output.writeLong(item.expiresAtEpoch());
        });
    }

    static long barter(BarterRecipeDef recipe, ItemDef target) {
        Objects.requireNonNull(recipe, "recipe");
        Objects.requireNonNull(target, "target");
        return revision(output -> {
            writeText(output, "barter v1");
            output.writeLong(item(target));
            writeText(output, recipe.recipeId());
            writeText(output, recipe.targetItemId());
            output.writeInt(recipe.outputCount());
            output.writeInt(recipe.ingredients().size());
            for (BarterIngredientDef ingredient : recipe.ingredients()) {
                writeText(output, ingredient.itemId());
                output.writeInt(ingredient.count());
                writeText(output, ingredient.nbtJson());
            }
        });
    }

    private static long revision(IoWriter writer) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            writer.write(output);
            output.flush();
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(bytes.toByteArray());
            long value = 0L;
            for (int index = 0; index < Long.BYTES; index++) {
                value = value << 8 | digest[index] & 0xffL;
            }
            return Long.remainderUnsigned(value,
                    ServerShopBarterCommit.MAX_REVISION + 1L);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Catalog evidence revision encoding failed", exception);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "Catalog evidence revision hashing is unavailable",
                    exception);
        }
    }

    private static void writeText(
            DataOutputStream output,
            String value
    ) throws IOException {
        byte[] encoded = Objects.requireNonNullElse(value, "")
                .getBytes(StandardCharsets.UTF_8);
        output.writeInt(encoded.length);
        output.write(encoded);
    }

    @FunctionalInterface
    private interface IoWriter {
        void write(DataOutputStream output) throws IOException;
    }
}

package com.enviouse.futureshops.client.editor;

import com.enviouse.futureshops.catalog.offer.ServerShopOfferListing;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class OfferEditorStaleReview {
    private OfferEditorStaleReview() {
    }

    public static List<Change> compare(
            ServerShopOfferListing local,
            ServerShopOfferListing server
    ) {
        Objects.requireNonNull(local, "local");
        Objects.requireNonNull(server, "server");
        List<Change> changes = new ArrayList<>();
        add(changes, "revision", local.revision(), server.revision());
        add(changes, "displayName",
                local.displayName(), server.displayName());
        add(changes, "description",
                local.description(), server.description());
        add(changes, "categoryId",
                local.categoryId(), server.categoryId());
        add(changes, "iconItemId",
                local.iconItemId(), server.iconItemId());
        add(changes, "iconNbt", local.iconNbt(), server.iconNbt());
        add(changes, "active", local.active(), server.active());
        add(changes, "expiresAtEpoch",
                local.expiresAtEpoch(), server.expiresAtEpoch());
        add(changes, "permission",
                local.permissionNode(), server.permissionNode());
        add(changes, "outputs", local.outputs(), server.outputs());
        add(changes, "acquireOptions",
                local.acquireOptions(), server.acquireOptions());
        add(changes, "sellOptions",
                local.sellOptions(), server.sellOptions());
        add(changes, "stock", local.stockPolicy(),
                server.stockPolicy());
        add(changes, "limits", local.limits(), server.limits());
        add(changes, "schedule",
                local.schedule(), server.schedule());
        add(changes, "bundleComparisons",
                local.bundleComparisons(),
                server.bundleComparisons());
        return List.copyOf(changes);
    }

    private static void add(
            List<Change> changes,
            String path,
            Object local,
            Object server
    ) {
        if (!Objects.equals(local, server)) {
            changes.add(new Change(path,
                    Objects.toString(local, ""),
                    Objects.toString(server, "")));
        }
    }

    public record Change(
            String path,
            String localValue,
            String serverValue
    ) {
        public Change {
            path = Objects.requireNonNull(path, "path");
            localValue = Objects.requireNonNull(localValue, "localValue");
            serverValue = Objects.requireNonNull(serverValue, "serverValue");
        }
    }
}

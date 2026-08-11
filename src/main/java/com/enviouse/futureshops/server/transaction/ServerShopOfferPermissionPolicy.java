package com.enviouse.futureshops.server.transaction;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.server.permission.PermissionAPI;
import net.minecraftforge.server.permission.nodes.PermissionNode;
import net.minecraftforge.server.permission.nodes.PermissionTypes;

import java.util.Objects;

public final class ServerShopOfferPermissionPolicy {
    private ServerShopOfferPermissionPolicy() {
    }

    public static boolean allowed(
            ServerPlayer player,
            String permission
    ) {
        Objects.requireNonNull(player, "player");
        String normalized = Objects.requireNonNullElse(
                permission, "").strip();
        if (normalized.isEmpty() || player.hasPermissions(2)) {
            return true;
        }
        String namespace = "futureshops";
        String path = normalized;
        int separator = normalized.indexOf(':');
        if (separator > 0 && separator < normalized.length() - 1) {
            namespace = normalized.substring(0, separator);
            path = normalized.substring(separator + 1);
        }
        try {
            PermissionNode<Boolean> node = new PermissionNode<>(
                    namespace, path, PermissionTypes.BOOLEAN,
                    (subject, playerId, context) -> false);
            return PermissionAPI.getActivePermissionHandler() != null
                    && PermissionAPI.getPermission(player, node);
        } catch (RuntimeException exception) {
            return false;
        }
    }
}

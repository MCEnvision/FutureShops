package com.enviouse.futureshops.client.screen;

public final class ClientNavigationPolicy {
    public enum Action {
        RETURN_TO_PARENT,
        RETURN_TO_GRID,
        CLOSE
    }

    public enum ProfileAction {
        RETURN_TO_PARENT,
        OPEN_DEFAULT_SHOP
    }

    private ClientNavigationPolicy() {
    }

    public static Action storefrontBack(boolean singleItemMode) {
        return singleItemMode ? Action.RETURN_TO_PARENT : Action.RETURN_TO_GRID;
    }

    public static Action storefrontEscape(boolean singleItemMode, boolean detailOpen) {
        return detailOpen ? storefrontBack(singleItemMode) : Action.CLOSE;
    }

    public static Action playerShopBlockEscape(boolean owner) {
        return owner ? Action.CLOSE : Action.RETURN_TO_PARENT;
    }

    public static ProfileAction profileBack() {
        return ProfileAction.RETURN_TO_PARENT;
    }

    public static ProfileAction profileClose() {
        return ProfileAction.OPEN_DEFAULT_SHOP;
    }
}

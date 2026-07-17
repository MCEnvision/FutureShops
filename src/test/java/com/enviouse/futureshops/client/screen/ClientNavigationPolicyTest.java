package com.enviouse.futureshops.client.screen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClientNavigationPolicyTest {
    @Test
    void singleItemBackReturnsToTheOrigin() {
        assertEquals(ClientNavigationPolicy.Action.RETURN_TO_PARENT,
                ClientNavigationPolicy.storefrontBack(true));
    }

    @Test
    void storefrontDetailBackReturnsToTheGrid() {
        assertEquals(ClientNavigationPolicy.Action.RETURN_TO_GRID,
                ClientNavigationPolicy.storefrontBack(false));
    }

    @Test
    void escapeActsAsBackOnlyWhileDetailIsOpen() {
        assertEquals(ClientNavigationPolicy.Action.RETURN_TO_GRID,
                ClientNavigationPolicy.storefrontEscape(false, true));
        assertEquals(ClientNavigationPolicy.Action.CLOSE,
                ClientNavigationPolicy.storefrontEscape(false, false));
    }

    @Test
    void playerShopOwnerEscapeClosesWhileVisitorEscapeReturnsToParent() {
        assertEquals(ClientNavigationPolicy.Action.CLOSE,
                ClientNavigationPolicy.playerShopBlockEscape(true));
        assertEquals(ClientNavigationPolicy.Action.RETURN_TO_PARENT,
                ClientNavigationPolicy.playerShopBlockEscape(false));
    }
}

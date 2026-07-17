package com.enviouse.futureshops.client.screen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminItemSearchPolicyTest {
    private static final String ID = "minecraft:diamond";
    private static final String SEARCH_TEXT =
            "minecraft:diamond diamond";

    @Test
    void namespaceSearchMatchesWhileTheModNameIsStillPartial() {
        assertTrue(AdminItemSearchPolicy.matches(ID, SEARCH_TEXT, "@m"));
        assertTrue(AdminItemSearchPolicy.matches(ID, SEARCH_TEXT, "@mine"));
        assertTrue(AdminItemSearchPolicy.matches(ID, SEARCH_TEXT, "@MINECRAFT"));
    }

    @Test
    void namespaceSearchDoesNotMatchItemNamesOrOtherNamespaces() {
        assertFalse(AdminItemSearchPolicy.matches(ID, SEARCH_TEXT, "@diamond"));
        assertFalse(AdminItemSearchPolicy.matches(ID, SEARCH_TEXT, "@create"));
    }

    @Test
    void plainSearchStillMatchesIdsAndDisplayNames() {
        assertTrue(AdminItemSearchPolicy.matches(ID, SEARCH_TEXT, "diamond"));
        assertTrue(AdminItemSearchPolicy.matches(ID, SEARCH_TEXT, "MINECRAFT"));
        assertFalse(AdminItemSearchPolicy.matches(ID, SEARCH_TEXT, "emerald"));
    }

    @Test
    void blankQueriesIncludeTheWholeRegistry() {
        assertTrue(AdminItemSearchPolicy.matches(ID, SEARCH_TEXT, ""));
        assertTrue(AdminItemSearchPolicy.matches(ID, SEARCH_TEXT, "@"));
    }
}

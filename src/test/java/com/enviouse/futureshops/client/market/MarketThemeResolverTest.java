package com.enviouse.futureshops.client.market;

import com.enviouse.futureshops.ClientConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketThemeResolverTest {
    @Test
    void moduleDefaultsUseRequiredIdentityColors() {
        ClientConfig.Settings settings = ClientConfig.Settings.defaults();

        assertEquals(0xFF9184D9, MarketThemeResolver.resolve(
            MarketModule.SHOP, null, settings).accent());
        assertEquals(0xFF48B978, MarketThemeResolver.resolve(
            MarketModule.BAZAAR, null, settings).accent());
        assertEquals(0xFFD85B68, MarketThemeResolver.resolve(
            MarketModule.AUCTION_HOUSE, null, settings).accent());
    }

    @Test
    void customAccessibilityOverridesWinOverServerBranding() {
        ClientConfig.Settings settings = settings(
            new ClientConfig.Theme("server", true, "#123456"),
            new ClientConfig.Accessibility(true, "none", true),
            new ClientConfig.Motion(100));

        MarketTheme theme = MarketThemeResolver.resolve(MarketModule.BAZAAR, "#010203", settings);

        assertEquals(0xFF123456, theme.accent());
        assertEquals(0xFFF3F5FE, theme.focusHighlight());
        assertTrue(theme.highContrast());
        assertTrue(theme.reducedMotion());
    }

    @Test
    void colorblindModesDoNotReplaceSemanticColors() {
        ClientConfig.Settings normalSettings = ClientConfig.Settings.defaults();
        ClientConfig.Settings accessibleSettings = settings(
            normalSettings.theme(),
            new ClientConfig.Accessibility(false, "deuteranopia", false),
            normalSettings.motion());

        MarketTheme normal = MarketThemeResolver.resolve(MarketModule.AUCTION_HOUSE, null, normalSettings);
        MarketTheme accessible = MarketThemeResolver.resolve(
            MarketModule.AUCTION_HOUSE, null, accessibleSettings);

        assertNotEquals(normal.accent(), accessible.accent());
        assertEquals(normal.semanticSuccess(), accessible.semanticSuccess());
        assertEquals(normal.semanticWarning(), accessible.semanticWarning());
        assertEquals(normal.semanticDanger(), accessible.semanticDanger());
        assertEquals(normal.semanticInformation(), accessible.semanticInformation());
    }

    @Test
    void zeroAnimationSpeedAlsoEnablesReducedMotion() {
        ClientConfig.Settings defaults = ClientConfig.Settings.defaults();
        ClientConfig.Settings settings = settings(
            defaults.theme(), defaults.accessibility(), new ClientConfig.Motion(0));

        assertTrue(MarketThemeResolver.resolve(MarketModule.SHOP, null, settings).reducedMotion());
    }

    @Test
    void brandingAlphaCannotMakeInteractiveColorsTransparent() {
        ClientConfig.Settings settings = settings(
            new ClientConfig.Theme("server", true, "#00123456"),
            ClientConfig.Settings.defaults().accessibility(),
            ClientConfig.Settings.defaults().motion());

        MarketTheme theme = MarketThemeResolver.resolve(MarketModule.SHOP, null, settings);

        assertEquals(0xFF123456, theme.accent());
        assertEquals(0xFF, theme.callToAction() >>> 24);
    }

    private static ClientConfig.Settings settings(
        ClientConfig.Theme theme,
        ClientConfig.Accessibility accessibility,
        ClientConfig.Motion motion
    ) {
        ClientConfig.Settings defaults = ClientConfig.Settings.defaults();
        return new ClientConfig.Settings(
            defaults.presentation(),
            defaults.search(),
            theme,
            accessibility,
            motion,
            defaults.sound(),
            defaults.confirmation()
        );
    }
}

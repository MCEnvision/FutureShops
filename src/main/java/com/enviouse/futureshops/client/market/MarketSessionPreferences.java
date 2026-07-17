package com.enviouse.futureshops.client.market;

import com.enviouse.futureshops.ClientConfig;
import com.enviouse.futureshops.money.PaymentSource;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class MarketSessionPreferences {
    private static final int MAXIMUM_IDENTIFIER_LENGTH = 128;

    public record Policy(
        boolean rememberTab,
        boolean rememberFilter,
        boolean rememberSort,
        boolean rememberPaymentSource
    ) {
        public static Policy from(ClientConfig.Presentation presentation) {
            ClientConfig.Presentation source = Objects.requireNonNull(
                presentation, "presentation");
            return new Policy(source.rememberTab(), source.rememberFilter(),
                source.rememberSort(), source.rememberPaymentSource());
        }
    }

    public record ModulePreference(
        String viewId,
        String categoryId,
        String filterId,
        String sortId
    ) {
        public ModulePreference {
            viewId = text(viewId, false, "Market preference view");
            categoryId = text(categoryId, true, "Market preference category");
            filterId = text(filterId, true, "Market preference filter");
            sortId = text(sortId, true, "Market preference sort");
        }
    }

    private final EnumMap<MarketModule, ModulePreference> modules =
        new EnumMap<>(MarketModule.class);
    private final EnumSet<MarketModule> rememberedTabs =
        EnumSet.noneOf(MarketModule.class);
    private Policy policy;
    private PaymentSource paymentSource;

    public MarketSessionPreferences(Policy policy) {
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    public static MarketSessionPreferences from(ClientConfig.Settings settings) {
        return new MarketSessionPreferences(
            Policy.from(Objects.requireNonNull(settings, "settings")
                .presentation()));
    }

    public synchronized void updatePolicy(Policy next) {
        policy = Objects.requireNonNull(next, "next");
        if (!policy.rememberTab() && !policy.rememberFilter()
            && !policy.rememberSort()) {
            modules.clear();
        } else {
            modules.replaceAll((module, preference) -> new ModulePreference(
                policy.rememberTab() ? preference.viewId() : module.rootView(),
                policy.rememberFilter() ? preference.categoryId() : "",
                policy.rememberFilter() ? preference.filterId() : "",
                policy.rememberSort() ? preference.sortId() : ""));
        }
        if (!policy.rememberPaymentSource()) {
            paymentSource = null;
        }
        if (!policy.rememberTab()) {
            rememberedTabs.clear();
        }
    }

    public synchronized void applySettings(ClientConfig.Settings settings) {
        updatePolicy(Policy.from(Objects.requireNonNull(settings, "settings")
            .presentation()));
    }

    public synchronized void rememberTab(MarketModule module, String viewId) {
        MarketModule safeModule = Objects.requireNonNull(module, "module");
        if (!policy.rememberTab()) {
            return;
        }
        ModulePreference previous = preference(safeModule);
        modules.put(safeModule, new ModulePreference(
            text(viewId, false, "Market preference view"),
            previous.categoryId(), previous.filterId(), previous.sortId()));
        rememberedTabs.add(safeModule);
    }

    public synchronized void rememberRouteState(MarketRoute route) {
        MarketRoute safeRoute = Objects.requireNonNull(route, "route");
        MarketModule module = safeRoute.module();
        ModulePreference previous = preference(module);
        modules.put(module, new ModulePreference(
            previous.viewId(),
            policy.rememberFilter() ? safeRoute.categoryId()
                : previous.categoryId(),
            policy.rememberFilter() ? safeRoute.filterId()
                : previous.filterId(),
            policy.rememberSort() ? safeRoute.sortId()
                : previous.sortId()));
    }

    public synchronized void rememberRoute(MarketRoute route,
                                             boolean rememberAsTab) {
        Objects.requireNonNull(route, "route");
        if (rememberAsTab) {
            rememberTab(route.module(), route.viewId());
        }
        rememberRouteState(route);
    }

    public synchronized ModulePreference preference(MarketModule module) {
        MarketModule safeModule = Objects.requireNonNull(module, "module");
        ModulePreference value = modules.get(safeModule);
        if (value == null) {
            return new ModulePreference(safeModule.rootView(), "", "", "");
        }
        return new ModulePreference(
            policy.rememberTab() ? value.viewId() : safeModule.rootView(),
            policy.rememberFilter() ? value.categoryId() : "",
            policy.rememberFilter() ? value.filterId() : "",
            policy.rememberSort() ? value.sortId() : "");
    }

    public synchronized boolean hasRememberedTab(MarketModule module) {
        return policy.rememberTab()
            && rememberedTabs.contains(Objects.requireNonNull(module, "module"));
    }

    public synchronized MarketRoute entryRoute(MarketModule module,
                                                UUID routeNonce) {
        MarketModule safeModule = Objects.requireNonNull(module, "module");
        ModulePreference value = preference(safeModule);
        return new MarketRoute(safeModule, value.viewId(),
            value.categoryId(), "", value.filterId(), value.sortId(),
            0, 0, "", routeNonce);
    }

    public synchronized void rememberPaymentSource(PaymentSource source) {
        Objects.requireNonNull(source, "source");
        if (policy.rememberPaymentSource()) {
            paymentSource = source;
        }
    }

    public synchronized Optional<PaymentSource> paymentSource() {
        return policy.rememberPaymentSource()
            ? Optional.ofNullable(paymentSource) : Optional.empty();
    }

    public synchronized Map<MarketModule, ModulePreference> snapshot() {
        EnumMap<MarketModule, ModulePreference> snapshot =
            new EnumMap<>(MarketModule.class);
        for (MarketModule module : MarketModule.values()) {
            snapshot.put(module, preference(module));
        }
        return Map.copyOf(snapshot);
    }

    public synchronized void reset() {
        modules.clear();
        rememberedTabs.clear();
        paymentSource = null;
    }

    private static String text(String value, boolean allowEmpty,
                               String label) {
        if (value == null) {
            throw new IllegalArgumentException(label + " is required.");
        }
        String normalized = value.strip();
        if ((!allowEmpty && normalized.isEmpty())
            || normalized.length() > MAXIMUM_IDENTIFIER_LENGTH) {
            throw new IllegalArgumentException(label + " has an invalid length.");
        }
        return normalized;
    }
}

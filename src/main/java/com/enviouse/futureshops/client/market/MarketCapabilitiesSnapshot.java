package com.enviouse.futureshops.client.market;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record MarketCapabilitiesSnapshot(
    UUID requestId,
    long revision,
    boolean showNavigation,
    MarketModule defaultModule,
    long walletBalanceMinorUnits,
    boolean walletBalanceKnown,
    String currencyName,
    int currencyDecimals,
    List<Long> auctionDurationPresetSeconds,
    List<MarketModuleCapability> modules
) {
    public static final int MAXIMUM_CURRENCY_NAME_LENGTH = 64;

    public MarketCapabilitiesSnapshot {
        if (requestId == null || requestId.equals(new UUID(0L, 0L))) {
            throw new IllegalArgumentException("Market capability request identifier is required.");
        }
        if (revision < 0L) {
            throw new IllegalArgumentException("Market capability revision must not be negative.");
        }
        Objects.requireNonNull(defaultModule, "defaultModule");
        currencyName = boundedCurrencyName(currencyName);
        if (!walletBalanceKnown && walletBalanceMinorUnits != 0L
                || currencyDecimals < 0 || currencyDecimals > 6) {
            throw new IllegalArgumentException(
                    "Market wallet presentation is invalid.");
        }
        auctionDurationPresetSeconds = List.copyOf(Objects.requireNonNull(
                auctionDurationPresetSeconds,
                "auctionDurationPresetSeconds"));
        if (auctionDurationPresetSeconds.isEmpty()
                || auctionDurationPresetSeconds.size() > 8
                || auctionDurationPresetSeconds.stream().anyMatch(
                value -> value == null || value <= 0L
                        || value > 5_184_000L)
                || auctionDurationPresetSeconds.stream().distinct().count()
                != auctionDurationPresetSeconds.size()) {
            throw new IllegalArgumentException(
                    "Auction duration presets are invalid.");
        }
        modules = List.copyOf(Objects.requireNonNull(modules, "modules"));
        if (modules.isEmpty() || modules.size() > MarketModule.values().length) {
            throw new IllegalArgumentException("Market capabilities must contain a bounded module set.");
        }
        EnumMap<MarketModule, MarketModuleCapability> unique = new EnumMap<>(MarketModule.class);
        for (MarketModuleCapability capability : modules) {
            if (unique.put(capability.module(), capability) != null) {
                throw new IllegalArgumentException("Market capabilities contain a duplicate module.");
            }
        }
        MarketModuleCapability defaultCapability = unique.get(defaultModule);
        if (defaultCapability == null
                || !defaultCapability.availability().allowsBrowse()
                && !(defaultCapability.availability().allowsClaims()
                && defaultCapability.openClaims() > 0L)) {
            defaultModule = unique.values().stream()
                .filter(capability -> capability.availability().allowsBrowse())
                .map(MarketModuleCapability::module)
                .findFirst()
                .orElseGet(() -> unique.values().stream()
                    .filter(capability -> capability.availability().allowsClaims())
                    .map(MarketModuleCapability::module)
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("At least one market module must be openable.")));
        }
    }

    public MarketCapabilitiesSnapshot(
            UUID requestId, long revision, boolean showNavigation,
            MarketModule defaultModule, long walletBalanceMinorUnits,
            boolean walletBalanceKnown, String currencyName,
            int currencyDecimals,
            List<MarketModuleCapability> modules) {
        this(requestId, revision, showNavigation, defaultModule,
                walletBalanceMinorUnits, walletBalanceKnown,
                currencyName, currencyDecimals,
                List.of(3_600L, 21_600L, 86_400L, 259_200L,
                        604_800L), modules);
    }

    public MarketCapabilitiesSnapshot(
        UUID requestId,
        long revision,
        boolean showNavigation,
        MarketModule defaultModule,
        List<MarketModuleCapability> modules
    ) {
        this(requestId, revision, showNavigation, defaultModule,
            0L, false, "Coins", 2,
                List.of(3_600L, 21_600L, 86_400L, 259_200L,
                        604_800L), modules);
    }

    public Map<MarketModule, MarketModuleCapability> byModule() {
        EnumMap<MarketModule, MarketModuleCapability> result = new EnumMap<>(MarketModule.class);
        for (MarketModuleCapability capability : modules) {
            result.put(capability.module(), capability);
        }
        return Map.copyOf(result);
    }

    private static String boundedCurrencyName(String value) {
        String name = Objects.requireNonNull(value,
                "currencyName");
        if (name.isEmpty()
                || name.length() > MAXIMUM_CURRENCY_NAME_LENGTH
                || !name.equals(name.strip())) {
            throw new IllegalArgumentException(
                    "Market currency name is invalid.");
        }
        for (int index = 0; index < name.length(); index++) {
            char character = name.charAt(index);
            if (Character.isHighSurrogate(character)) {
                if (index + 1 >= name.length()
                        || !Character.isLowSurrogate(
                        name.charAt(index + 1))) {
                    throw new IllegalArgumentException(
                            "Market currency name is invalid.");
                }
                index++;
            } else if (Character.isLowSurrogate(character)
                    || Character.isISOControl(character)) {
                throw new IllegalArgumentException(
                        "Market currency name is invalid.");
            }
        }
        return name;
    }
}

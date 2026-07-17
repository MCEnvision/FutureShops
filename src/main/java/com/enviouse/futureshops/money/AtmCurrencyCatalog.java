package com.enviouse.futureshops.money;

import com.enviouse.futureshops.Config;
import com.enviouse.futureshops.server.economy.EconomyProvider;
import com.enviouse.futureshops.server.escrow.runtime.AtmBillSelection;
import com.enviouse.futureshops.server.escrow.runtime.ForeignCashClaimPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

public record AtmCurrencyCatalog(
        int schemaVersion,
        String providerId,
        AtmCurrencyRoute route,
        String currencyName,
        int decimalPlaces,
        String protectedConfigurationRevision,
        List<Denomination> denominations,
        String signature
) {
    public static final int CURRENT_SCHEMA = 1;
    public static final int MAXIMUM_DENOMINATIONS = 32;
    public static final int MAXIMUM_BILLS = 4096;
    public static final int MAXIMUM_CLAIM_STACKS = 64;
    public static final int MAXIMUM_TEXT_LENGTH = 256;

    private static final Pattern HEX_DIGEST = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern ITEM_ID = Pattern.compile(
            "[a-z0-9_.-]+:[a-z0-9/._-]+");

    public AtmCurrencyCatalog {
        providerId = requireText(providerId, 128, "providerId");
        Objects.requireNonNull(route, "route");
        currencyName = requireText(
                currencyName, MAXIMUM_TEXT_LENGTH, "currencyName");
        protectedConfigurationRevision = requireDigest(
                protectedConfigurationRevision,
                "protectedConfigurationRevision");
        signature = requireDigest(signature, "signature");
        denominations = List.copyOf(Objects.requireNonNull(
                denominations, "denominations"));
        if (schemaVersion != CURRENT_SCHEMA
                || decimalPlaces < 0
                || decimalPlaces > 6
                || denominations.isEmpty()
                || denominations.size() > MAXIMUM_DENOMINATIONS
                || route == AtmCurrencyRoute.PROTECTED_ESCROW
                != providerId.equals(InternalCurrencyAdapter.ID)) {
            throw new IllegalArgumentException(
                    "ATM currency catalog identity is invalid");
        }
        Set<String> foreignItems = new HashSet<>();
        for (int index = 0; index < denominations.size(); index++) {
            Denomination denomination = denominations.get(index);
            if (denomination.index() != index) {
                throw new IllegalArgumentException(
                        "ATM denomination indexes are not contiguous");
            }
            if (route == AtmCurrencyRoute.FOREIGN_UNPROTECTED
                    && !foreignItems.add(denomination.itemId())) {
                throw new IllegalArgumentException(
                        "Foreign ATM item is duplicated");
            }
            if (route == AtmCurrencyRoute.FOREIGN_UNPROTECTED
                    && ForeignCashClaimPayload.PROTECTED_ITEM_ID.equals(
                    denomination.itemId())) {
                throw new IllegalArgumentException(
                        "Foreign ATM cannot advertise protected currency");
            }
        }
        String expected = calculateSignature(
                schemaVersion, providerId, route, currencyName,
                decimalPlaces, protectedConfigurationRevision,
                denominations);
        if (!signature.equals(expected)) {
            throw new IllegalArgumentException(
                    "ATM currency catalog signature is invalid");
        }
    }

    public static AtmCurrencyCatalog capture(
            PhysicalCurrencyAdapter currency,
            EconomyProvider economy
    ) {
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(economy, "economy");
        List<Denomination> denominations = new ArrayList<>();
        List<PhysicalCurrencyAdapter.Denomination> configured =
                currency.denominations();
        int advertised = Math.min(
                configured.size(), MAXIMUM_DENOMINATIONS);
        for (int index = 0; index < advertised; index++) {
            PhysicalCurrencyAdapter.Denomination value =
                    configured.get(index);
            ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(
                    value.item());
            if (itemId == null) {
                throw new IllegalStateException(
                        "ATM denomination item is not registered");
            }
            denominations.add(new Denomination(
                    index, itemId.toString(), value.valueMinor(),
                    Math.max(1, new ItemStack(value.item())
                            .getMaxStackSize())));
        }
        AtmCurrencyRoute route = currency.isInternal()
                ? AtmCurrencyRoute.PROTECTED_ESCROW
                : AtmCurrencyRoute.FOREIGN_UNPROTECTED;
        return create(currency.id(), route,
                economy.getCurrencyName(), economy.getDecimalPlaces(),
                protectedConfigurationRevision(route), denominations);
    }

    public static AtmCurrencyCatalog create(
            String providerId,
            AtmCurrencyRoute route,
            String currencyName,
            int decimalPlaces,
            String protectedConfigurationRevision,
            List<Denomination> denominations
    ) {
        String signature = calculateSignature(
                CURRENT_SCHEMA, providerId, route, currencyName,
                decimalPlaces, protectedConfigurationRevision,
                denominations);
        return new AtmCurrencyCatalog(
                CURRENT_SCHEMA, providerId, route, currencyName,
                decimalPlaces, protectedConfigurationRevision,
                denominations, signature);
    }

    public AtmSelectionPlan plan(List<Integer> counts) {
        if (counts == null || counts.size() != denominations.size()) {
            return AtmSelectionPlan.failed(
                    AtmSelectionPlan.Failure.INVALID_PLAN);
        }
        List<AtmBillSelection> selections = new ArrayList<>();
        int totalBills = 0;
        int totalClaimStacks = 0;
        long totalValue = 0L;
        try {
            for (int index = 0; index < counts.size(); index++) {
                Integer boxed = counts.get(index);
                if (boxed == null || boxed < 0 || boxed > MAXIMUM_BILLS) {
                    return AtmSelectionPlan.failed(
                            AtmSelectionPlan.Failure.INVALID_PLAN);
                }
                int count = boxed;
                totalBills = Math.addExact(totalBills, count);
                if (totalBills > MAXIMUM_BILLS) {
                    return AtmSelectionPlan.failed(
                            AtmSelectionPlan.Failure.INVALID_PLAN);
                }
                if (count == 0) {
                    continue;
                }
                Denomination denomination = denominations.get(index);
                int stackSize = Math.min(
                        64, denomination.maximumStackSize());
                int stacks = Math.floorDiv(
                        Math.addExact(count, stackSize - 1), stackSize);
                totalClaimStacks = Math.addExact(
                        totalClaimStacks, stacks);
                if (totalClaimStacks > MAXIMUM_CLAIM_STACKS) {
                    return AtmSelectionPlan.failed(
                            AtmSelectionPlan.Failure.INVALID_PLAN);
                }
                totalValue = Math.addExact(totalValue, Math.multiplyExact(
                        denomination.valueMinorUnits(), (long) count));
                selections.add(new AtmBillSelection(
                        index, denomination.valueMinorUnits(), count));
            }
        } catch (ArithmeticException exception) {
            return AtmSelectionPlan.failed(
                    AtmSelectionPlan.Failure.INVALID_PLAN);
        }
        if (totalBills == 0 || totalValue <= 0L) {
            return AtmSelectionPlan.failed(
                    AtmSelectionPlan.Failure.INVALID_AMOUNT);
        }
        return AtmSelectionPlan.success(
                selections, totalValue, totalBills);
    }

    private static String protectedConfigurationRevision(
            AtmCurrencyRoute route
    ) {
        if (route == AtmCurrencyRoute.FOREIGN_UNPROTECTED) {
            return sha256("foreign currency has no protected mint revision");
        }
        return sha256(Objects.toString(Config.moneyMintServerId, "")
                + "\u0000" + Objects.toString(Config.moneyChecksumSalt, ""));
    }

    private static String calculateSignature(
            int schemaVersion,
            String providerId,
            AtmCurrencyRoute route,
            String currencyName,
            int decimalPlaces,
            String protectedConfigurationRevision,
            List<Denomination> denominations
    ) {
        Objects.requireNonNull(route, "route");
        Objects.requireNonNull(denominations, "denominations");
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeInt(schemaVersion);
            output.writeUTF(Objects.requireNonNull(providerId, "providerId"));
            output.writeUTF(route.name());
            output.writeUTF(Objects.requireNonNull(
                    currencyName, "currencyName"));
            output.writeInt(decimalPlaces);
            output.writeUTF(Objects.requireNonNull(
                    protectedConfigurationRevision,
                    "protectedConfigurationRevision"));
            output.writeInt(denominations.size());
            for (Denomination denomination : denominations) {
                Objects.requireNonNull(denomination, "denomination");
                output.writeInt(denomination.index());
                output.writeUTF(denomination.itemId());
                output.writeLong(denomination.valueMinorUnits());
                output.writeInt(denomination.maximumStackSize());
            }
            output.flush();
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(
                            bytes.toByteArray()));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to fingerprint ATM currency catalog", exception);
        }
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(
                            value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String requireText(String value, int maximumLength,
                                      String name) {
        String normalized = Objects.requireNonNull(value, name).strip();
        if (normalized.isEmpty() || normalized.length() > maximumLength) {
            throw new IllegalArgumentException(
                    "ATM currency catalog text is invalid");
        }
        return normalized;
    }

    private static String requireDigest(String value, String name) {
        String normalized = Objects.requireNonNull(value, name);
        if (!HEX_DIGEST.matcher(normalized).matches()) {
            throw new IllegalArgumentException(
                    "ATM currency catalog digest is invalid");
        }
        return normalized;
    }

    public record Denomination(
            int index,
            String itemId,
            long valueMinorUnits,
            int maximumStackSize
    ) {
        public Denomination {
            itemId = Objects.requireNonNull(itemId, "itemId");
            if (index < 0 || index >= MAXIMUM_DENOMINATIONS
                    || !ITEM_ID.matcher(itemId).matches()
                    || valueMinorUnits <= 0L
                    || maximumStackSize <= 0
                    || maximumStackSize > MAXIMUM_BILLS) {
                throw new IllegalArgumentException(
                        "ATM denomination is invalid");
            }
        }
    }
}

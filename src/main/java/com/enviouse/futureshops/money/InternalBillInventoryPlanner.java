package com.enviouse.futureshops.money;

import com.enviouse.futureshops.server.escrow.mint.ProtectedMintSavedData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class InternalBillInventoryPlanner {
    private final InternalBillAuthorityRouter router;

    public InternalBillInventoryPlanner(ProtectedMintSavedData protectedMints,
                                        SpentMintsSavedData legacyMints) {
        this(new InternalBillAuthorityRouter(protectedMints, legacyMints));
    }

    InternalBillInventoryPlanner(InternalBillAuthorityRouter router) {
        this.router = Objects.requireNonNull(router, "router");
    }

    public ExactPlan planExact(ServerPlayer player, long requestedMinorUnits) {
        Objects.requireNonNull(player, "player");
        return planExact(player.getInventory().items, player.getInventory().offhand,
                requestedMinorUnits, null);
    }

    public ExactPlan planExactSlot(ServerPlayer player,
                                   SlotIdentity slot,
                                   long requestedMinorUnits) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(slot, "slot");
        return planExact(player.getInventory().items,
                player.getInventory().offhand, requestedMinorUnits, slot);
    }

    ExactPlan planExact(List<ItemStack> mainInventory,
                        List<ItemStack> offhand,
                        long requestedMinorUnits) {
        Objects.requireNonNull(mainInventory, "mainInventory");
        Objects.requireNonNull(offhand, "offhand");
        return planExact(mainInventory, offhand, requestedMinorUnits, null);
    }

    private ExactPlan planExact(List<ItemStack> mainInventory,
                                List<ItemStack> offhand,
                                long requestedMinorUnits,
                                SlotIdentity onlySlot) {
        Objects.requireNonNull(mainInventory, "mainInventory");
        Objects.requireNonNull(offhand, "offhand");
        if (requestedMinorUnits < 0L) {
            return ExactPlan.failed(PlanStatus.INVALID_AMOUNT, requestedMinorUnits);
        }
        if (requestedMinorUnits == 0L) {
            return ExactPlan.zero();
        }

        List<Candidate> candidates = candidates(mainInventory, offhand,
                onlySlot);
        Selection protectedSelection = select(candidates, requestedMinorUnits,
                InternalBillAuthorityRouter.Authority.PROTECTED);
        if (protectedSelection != null) {
            return ExactPlan.success(requestedMinorUnits,
                    InternalBillAuthorityRouter.Authority.PROTECTED,
                    protectedSelection.portions());
        }
        Selection legacySelection = select(candidates, requestedMinorUnits,
                InternalBillAuthorityRouter.Authority.LEGACY);
        if (legacySelection != null) {
            return ExactPlan.success(requestedMinorUnits,
                    InternalBillAuthorityRouter.Authority.LEGACY,
                    legacySelection.portions());
        }
        Selection combined = select(candidates, requestedMinorUnits, null);
        if (combined == null) {
            return ExactPlan.failed(PlanStatus.NO_EXACT_SELECTION,
                    requestedMinorUnits);
        }
        InternalBillAuthorityRouter.Authority combinedAuthority = authorityOf(
                combined.portions());
        if (combinedAuthority != InternalBillAuthorityRouter.Authority.NONE) {
            return ExactPlan.success(requestedMinorUnits, combinedAuthority,
                    combined.portions());
        }
        return ExactPlan.failed(PlanStatus.MIXED_AUTHORITIES_REQUIRED,
                requestedMinorUnits);
    }

    public InventoryFacts inspect(ServerPlayer player,
                                  SlotIdentity onlySlot) {
        Objects.requireNonNull(player, "player");
        List<ItemStack> main = player.getInventory().items;
        List<ItemStack> offhand = player.getInventory().offhand;
        List<Candidate> candidates = candidates(main, offhand, onlySlot);
        long protectedValue = 0L;
        int protectedBills = 0;
        long legacyValue = 0L;
        int legacyBills = 0;
        List<LegacyBillFact> legacy = new ArrayList<>();
        for (Candidate candidate : candidates) {
            long value = Math.multiplyExact(
                    candidate.resolution().denominationMinorUnits(),
                    (long) candidate.availableCount());
            if (candidate.resolution().authority()
                    == InternalBillAuthorityRouter.Authority.PROTECTED) {
                protectedValue = Math.addExact(protectedValue, value);
                protectedBills = Math.addExact(protectedBills,
                        candidate.availableCount());
            } else if (candidate.resolution().authority()
                    == InternalBillAuthorityRouter.Authority.LEGACY) {
                legacyValue = Math.addExact(legacyValue, value);
                legacyBills = Math.addExact(legacyBills,
                        candidate.availableCount());
                legacy.add(new LegacyBillFact(candidate.slot(),
                        candidate.resolution().mintId(),
                        candidate.resolution().denominationMinorUnits(),
                        candidate.resolution().authorizedCount(),
                        candidate.availableCount(),
                        candidate.originalStackCount(),
                        candidate.exactStackSnapshot()));
            }
        }
        boolean collision = hasCollision(main, offhand, onlySlot);
        return new InventoryFacts(protectedValue, protectedBills,
                legacyValue, legacyBills, legacy, collision);
    }

    private List<Candidate> candidates(List<ItemStack> mainInventory,
                                       List<ItemStack> offhand,
                                       SlotIdentity onlySlot) {
        List<Candidate> candidates = new ArrayList<>();
        Map<AuthorityKey, Integer> budgets = new HashMap<>();
        collect(mainInventory, Container.MAIN, onlySlot,
                candidates, budgets);
        collect(offhand, Container.OFFHAND, onlySlot,
                candidates, budgets);
        return List.copyOf(candidates);
    }

    private void collect(List<ItemStack> stacks,
                         Container container,
                         SlotIdentity onlySlot,
                         List<Candidate> candidates,
                         Map<AuthorityKey, Integer> budgets) {
        for (int index = 0; index < stacks.size(); index++) {
            SlotIdentity slot = new SlotIdentity(container, index);
            if (onlySlot != null && !onlySlot.equals(slot)) {
                continue;
            }
            ItemStack stack = stacks.get(index);
            InternalBillAuthorityRouter.Resolution resolution = router.resolve(stack);
            if (!resolution.spendable()) {
                continue;
            }
            AuthorityKey key = new AuthorityKey(resolution.authority(),
                    resolution.mintId());
            int remaining = budgets.computeIfAbsent(key,
                    ignored -> resolution.authorityAvailableCount());
            int available = Math.min(stack.getCount(), remaining);
            if (available <= 0) {
                continue;
            }
            byte[] snapshot;
            try {
                snapshot = ItemStackSnapshotCodec.encode(stack);
            } catch (RuntimeException exception) {
                continue;
            }
            budgets.put(key, remaining - available);
            candidates.add(new Candidate(slot,
                    resolution, available, stack.getCount(), snapshot));
        }
    }

    private boolean hasCollision(List<ItemStack> main,
                                 List<ItemStack> offhand,
                                 SlotIdentity onlySlot) {
        for (int index = 0; index < main.size(); index++) {
            SlotIdentity slot = new SlotIdentity(Container.MAIN, index);
            if ((onlySlot == null || onlySlot.equals(slot))
                    && router.resolve(main.get(index)).status()
                    == InternalBillAuthorityRouter.Status
                    .CROSS_STORE_COLLISION) {
                return true;
            }
        }
        for (int index = 0; index < offhand.size(); index++) {
            SlotIdentity slot = new SlotIdentity(Container.OFFHAND, index);
            if ((onlySlot == null || onlySlot.equals(slot))
                    && router.resolve(offhand.get(index)).status()
                    == InternalBillAuthorityRouter.Status
                    .CROSS_STORE_COLLISION) {
                return true;
            }
        }
        return false;
    }

    private static Selection select(List<Candidate> candidates,
                                    long requestedMinorUnits,
                                    InternalBillAuthorityRouter.Authority authority) {
        Map<Long, Integer> availableByDenomination = new LinkedHashMap<>();
        candidates.stream()
                .filter(candidate -> authority == null
                        || candidate.resolution().authority() == authority)
                .map(candidate -> candidate.resolution().denominationMinorUnits())
                .distinct()
                .sorted(Comparator.reverseOrder())
                .forEach(value -> availableByDenomination.put(value, 0));
        for (Candidate candidate : candidates) {
            if (authority != null && candidate.resolution().authority() != authority) {
                continue;
            }
            availableByDenomination.computeIfPresent(
                    candidate.resolution().denominationMinorUnits(),
                    (ignored, current) -> Math.addExact(current, candidate.availableCount()));
        }
        if (availableByDenomination.isEmpty()) {
            return null;
        }
        long[] values = new long[availableByDenomination.size()];
        int[] available = new int[availableByDenomination.size()];
        int denominationIndex = 0;
        for (Map.Entry<Long, Integer> entry : availableByDenomination.entrySet()) {
            values[denominationIndex] = entry.getKey();
            available[denominationIndex] = entry.getValue();
            denominationIndex++;
        }
        long[] selected = CurrencyMath.exactBoundedCounts(
                requestedMinorUnits, values, available);
        if (selected == null) {
            return null;
        }

        List<Portion> portions = new ArrayList<>();
        for (int valueIndex = 0; valueIndex < values.length; valueIndex++) {
            int remaining = Math.toIntExact(selected[valueIndex]);
            if (remaining == 0) {
                continue;
            }
            for (Candidate candidate : candidates) {
                if (remaining == 0) {
                    break;
                }
                if ((authority != null
                        && candidate.resolution().authority() != authority)
                        || candidate.resolution().denominationMinorUnits()
                        != values[valueIndex]) {
                    continue;
                }
                int selectedCount = Math.min(remaining, candidate.availableCount());
                portions.add(candidate.portion(selectedCount));
                remaining -= selectedCount;
            }
            if (remaining != 0) {
                throw new IllegalStateException("Exact bill selection did not allocate");
            }
        }
        return new Selection(List.copyOf(portions));
    }

    private static InternalBillAuthorityRouter.Authority authorityOf(
            List<Portion> portions) {
        InternalBillAuthorityRouter.Authority found = null;
        for (Portion portion : portions) {
            if (found == null) {
                found = portion.authority();
            } else if (found != portion.authority()) {
                return InternalBillAuthorityRouter.Authority.NONE;
            }
        }
        return found == null ? InternalBillAuthorityRouter.Authority.NONE : found;
    }

    public enum Container {
        MAIN,
        OFFHAND
    }

    public enum PlanStatus {
        SUCCESS,
        INVALID_AMOUNT,
        NO_EXACT_SELECTION,
        MIXED_AUTHORITIES_REQUIRED
    }

    public record SlotIdentity(Container container, int index)
            implements Comparable<SlotIdentity> {
        public SlotIdentity {
            Objects.requireNonNull(container, "container");
            if (index < 0) {
                throw new IllegalArgumentException("Bill slot index is invalid");
            }
        }

        @Override
        public int compareTo(SlotIdentity other) {
            int byContainer = container.compareTo(other.container);
            return byContainer != 0 ? byContainer : Integer.compare(index, other.index);
        }
    }

    public record Portion(SlotIdentity slot,
                          InternalBillAuthorityRouter.Authority authority,
                          String mintId,
                          long denominationMinorUnits,
                          int authorizedCount,
                          int originalStackCount,
                          int selectedCount,
                          byte[] exactStackSnapshot) {
        public Portion {
            Objects.requireNonNull(slot, "slot");
            Objects.requireNonNull(authority, "authority");
            mintId = Objects.requireNonNull(mintId, "mintId");
            exactStackSnapshot = Objects.requireNonNull(
                    exactStackSnapshot, "exactStackSnapshot").clone();
            if (authority == InternalBillAuthorityRouter.Authority.NONE
                    || mintId.isEmpty() || denominationMinorUnits <= 0L
                    || authorizedCount <= 0 || originalStackCount <= 0
                    || selectedCount <= 0 || selectedCount > originalStackCount
                    || exactStackSnapshot.length == 0) {
                throw new IllegalArgumentException("Bill payment portion is invalid");
            }
        }

        @Override
        public byte[] exactStackSnapshot() {
            return exactStackSnapshot.clone();
        }

        public long valueMinorUnits() {
            return Math.multiplyExact(denominationMinorUnits, (long) selectedCount);
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }
            if (!(object instanceof Portion other)) {
                return false;
            }
            return denominationMinorUnits == other.denominationMinorUnits
                    && authorizedCount == other.authorizedCount
                    && originalStackCount == other.originalStackCount
                    && selectedCount == other.selectedCount
                    && slot.equals(other.slot)
                    && authority == other.authority
                    && mintId.equals(other.mintId)
                    && Arrays.equals(exactStackSnapshot, other.exactStackSnapshot);
        }

        @Override
        public int hashCode() {
            int result = Objects.hash(slot, authority, mintId,
                    denominationMinorUnits, authorizedCount,
                    originalStackCount, selectedCount);
            return 31 * result + Arrays.hashCode(exactStackSnapshot);
        }
    }

    public record ExactPlan(PlanStatus status,
                            long requestedMinorUnits,
                            long selectedMinorUnits,
                            InternalBillAuthorityRouter.Authority authority,
                            List<Portion> portions) {
        public ExactPlan {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(authority, "authority");
            portions = List.copyOf(Objects.requireNonNull(portions, "portions"));
            if (status == PlanStatus.SUCCESS) {
                long calculated = portions.stream().mapToLong(Portion::valueMinorUnits)
                        .reduce(0L, Math::addExact);
                boolean zero = requestedMinorUnits == 0L;
                if (requestedMinorUnits < 0L || selectedMinorUnits != requestedMinorUnits
                        || calculated != selectedMinorUnits
                        || zero != portions.isEmpty()
                        || zero != (authority == InternalBillAuthorityRouter.Authority.NONE)
                        || !zero && portions.stream().anyMatch(
                        portion -> portion.authority() != authority)) {
                    throw new IllegalArgumentException("Successful bill plan is invalid");
                }
            } else if (selectedMinorUnits != 0L || !portions.isEmpty()
                    || authority != InternalBillAuthorityRouter.Authority.NONE) {
                throw new IllegalArgumentException("Failed bill plan must be empty");
            }
        }

        public boolean successful() {
            return status == PlanStatus.SUCCESS;
        }

        private static ExactPlan success(long requestedMinorUnits,
                                         InternalBillAuthorityRouter.Authority authority,
                                         List<Portion> portions) {
            return new ExactPlan(PlanStatus.SUCCESS, requestedMinorUnits,
                    requestedMinorUnits, authority, portions);
        }

        private static ExactPlan zero() {
            return new ExactPlan(PlanStatus.SUCCESS, 0L, 0L,
                    InternalBillAuthorityRouter.Authority.NONE, List.of());
        }

        private static ExactPlan failed(PlanStatus status, long requestedMinorUnits) {
            return new ExactPlan(status, requestedMinorUnits, 0L,
                    InternalBillAuthorityRouter.Authority.NONE, List.of());
        }
    }

    public record InventoryFacts(
            long protectedAvailableMinorUnits,
            int protectedBillCount,
            long legacyAvailableMinorUnits,
            int legacyBillCount,
            List<LegacyBillFact> legacyBills,
            boolean crossStoreCollision
    ) {
        public InventoryFacts {
            legacyBills = List.copyOf(Objects.requireNonNull(
                    legacyBills, "legacyBills"));
            if (protectedAvailableMinorUnits < 0L
                    || protectedBillCount < 0
                    || legacyAvailableMinorUnits < 0L
                    || legacyBillCount < 0
                    || legacyBills.size() > 37
                    || legacyBills.stream().mapToLong(
                    LegacyBillFact::valueMinorUnits).sum()
                    != legacyAvailableMinorUnits
                    || legacyBills.stream().mapToInt(
                    LegacyBillFact::availableCount).sum()
                    != legacyBillCount) {
                throw new IllegalArgumentException(
                        "Bill inventory facts are invalid");
            }
        }
    }

    public record LegacyBillFact(
            SlotIdentity slot,
            String mintId,
            long denominationMinorUnits,
            int authorizedCount,
            int availableCount,
            int originalStackCount,
            byte[] exactStackSnapshot
    ) {
        public LegacyBillFact {
            Objects.requireNonNull(slot, "slot");
            mintId = Objects.requireNonNull(mintId, "mintId");
            exactStackSnapshot = Objects.requireNonNull(
                    exactStackSnapshot, "exactStackSnapshot").clone();
            if (mintId.isEmpty() || denominationMinorUnits <= 0L
                    || authorizedCount <= 0 || availableCount <= 0
                    || originalStackCount <= 0
                    || availableCount > originalStackCount
                    || exactStackSnapshot.length == 0) {
                throw new IllegalArgumentException(
                        "Legacy bill migration fact is invalid");
            }
        }

        @Override
        public byte[] exactStackSnapshot() {
            return exactStackSnapshot.clone();
        }

        public long valueMinorUnits() {
            return Math.multiplyExact(denominationMinorUnits,
                    (long) availableCount);
        }

        @Override
        public boolean equals(Object object) {
            return object instanceof LegacyBillFact other
                    && slot.equals(other.slot)
                    && mintId.equals(other.mintId)
                    && denominationMinorUnits
                    == other.denominationMinorUnits
                    && authorizedCount == other.authorizedCount
                    && availableCount == other.availableCount
                    && originalStackCount == other.originalStackCount
                    && Arrays.equals(exactStackSnapshot,
                    other.exactStackSnapshot);
        }

        @Override
        public int hashCode() {
            return 31 * Objects.hash(slot, mintId,
                    denominationMinorUnits, authorizedCount,
                    availableCount, originalStackCount)
                    + Arrays.hashCode(exactStackSnapshot);
        }
    }

    private record AuthorityKey(InternalBillAuthorityRouter.Authority authority,
                                String mintId) {
    }

    private record Candidate(SlotIdentity slot,
                             InternalBillAuthorityRouter.Resolution resolution,
                             int availableCount,
                             int originalStackCount,
                             byte[] exactStackSnapshot) {
        private Portion portion(int selectedCount) {
            return new Portion(slot, resolution.authority(), resolution.mintId(),
                    resolution.denominationMinorUnits(), resolution.authorizedCount(),
                    originalStackCount, selectedCount, exactStackSnapshot);
        }
    }

    private record Selection(List<Portion> portions) {
    }
}

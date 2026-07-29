package com.enviouse.futureshops.client.editor;

import com.enviouse.futureshops.catalog.offer.OfferValidationIssue;
import com.enviouse.futureshops.catalog.offer.ServerShopOfferListing;
import com.enviouse.futureshops.catalog.offer.ServerShopOfferRevision;
import com.enviouse.futureshops.catalog.offer.ServerShopOfferValidator;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;

public final class OfferEditorDraft {
    private ServerShopOfferListing baseline;
    private ServerShopOfferListing candidate;
    private final LinkedHashSet<String> dirtyPaths =
            new LinkedHashSet<>();
    private final LinkedHashMap<String, String> rawFieldValues =
            new LinkedHashMap<>();
    private final LinkedHashSet<String> invalidNumberPaths =
            new LinkedHashSet<>();
    private List<OfferValidationIssue> serverIssues = List.of();
    private Section section = Section.GENERAL;
    private final EnumMap<Section, Integer> scrollPositions =
            new EnumMap<>(Section.class);
    private String focusedPath = "";
    private final LinkedHashSet<String> expandedCards =
            new LinkedHashSet<>();
    private boolean helpVisible;

    public OfferEditorDraft(ServerShopOfferListing baseline) {
        this.baseline = Objects.requireNonNull(baseline, "baseline");
        this.candidate = baseline;
    }

    public synchronized void update(
            String path,
            UnaryOperator<ServerShopOfferListing> mutation
    ) {
        String normalizedPath = Objects.requireNonNull(
                path, "path").strip();
        if (normalizedPath.isEmpty()) {
            throw new IllegalArgumentException(
                    "Offer draft path cannot be empty");
        }
        ServerShopOfferListing next = Objects.requireNonNull(
                mutation.apply(candidate), "candidate");
        ServerShopOfferListing revised = next.withRevision(
                ServerShopOfferRevision.compute(next));
        if (candidate.equals(revised)) {
            return;
        }
        candidate = revised;
        dirtyPaths.add(normalizedPath);
        serverIssues = List.of();
        if (candidate.equals(baseline)) {
            dirtyPaths.clear();
            rawFieldValues.clear();
            invalidNumberPaths.clear();
        }
    }

    public synchronized void replace(
            String path,
            ServerShopOfferListing replacement
    ) {
        serverIssues = List.of();
        update(path, ignored -> replacement);
        rawFieldValues.clear();
        invalidNumberPaths.clear();
    }

    public synchronized void acknowledge(
            ServerShopOfferListing snapshot
    ) {
        baseline = Objects.requireNonNull(snapshot, "snapshot");
        candidate = snapshot;
        dirtyPaths.clear();
        rawFieldValues.clear();
        invalidNumberPaths.clear();
        serverIssues = List.of();
    }

    public synchronized void revert() {
        candidate = baseline;
        dirtyPaths.clear();
        rawFieldValues.clear();
        invalidNumberPaths.clear();
        serverIssues = List.of();
    }

    public synchronized void resetSection(Section resetSection) {
        Objects.requireNonNull(resetSection, "resetSection");
        candidate = copySection(candidate, baseline, resetSection);
        dirtyPaths.removeIf(path ->
                resetSection.owns(path));
        rawFieldValues.keySet().removeIf(resetSection::owns);
        invalidNumberPaths.removeIf(resetSection::owns);
        serverIssues = List.of();
        candidate = candidate.withRevision(
                ServerShopOfferRevision.compute(candidate));
        if (candidate.equals(baseline)) {
            dirtyPaths.clear();
        }
    }

    public synchronized ServerShopOfferListing candidate() {
        return candidate;
    }

    public synchronized ServerShopOfferListing baseline() {
        return baseline;
    }

    public synchronized Set<String> dirtyPaths() {
        return Set.copyOf(dirtyPaths);
    }

    public synchronized boolean dirty() {
        return !dirtyPaths.isEmpty();
    }

    public synchronized boolean sectionDirty(Section section) {
        Objects.requireNonNull(section, "section");
        return dirtyPaths.stream().anyMatch(section::owns)
                || rawFieldValues.keySet().stream().anyMatch(section::owns)
                || invalidNumberPaths.stream().anyMatch(section::owns);
    }

    public synchronized List<OfferValidationIssue> issues() {
        List<OfferValidationIssue> issues = new ArrayList<>(
                ServerShopOfferValidator.validate(candidate).issues());
        for (String path : invalidNumberPaths) {
            issues.add(new OfferValidationIssue(
                    OfferValidationIssue.Severity.ERROR,
                    path, "offer.field.invalid_number"));
        }
        issues.addAll(serverIssues);
        return List.copyOf(issues);
    }

    public synchronized boolean valid() {
        return issues().stream().noneMatch(issue ->
                issue.severity()
                        == OfferValidationIssue.Severity.ERROR);
    }

    public synchronized List<OfferValidationIssue> issues(
            Section issueSection
    ) {
        Objects.requireNonNull(issueSection, "issueSection");
        return issues().stream().filter(issue ->
                issueSection.owns(issue.path())
                        || Section.forPath(issue.path())
                        == issueSection).toList();
    }

    public synchronized List<OfferValidationIssue> issues(
            String path
    ) {
        String normalized = Objects.requireNonNullElse(path, "");
        return issues().stream().filter(issue ->
                issue.path().equals(normalized)
                        || issue.path().startsWith(normalized + ".")
                        || normalized.startsWith(issue.path() + "."))
                .toList();
    }

    public synchronized String fieldValue(
            String path,
            String canonicalValue
    ) {
        return rawFieldValues.getOrDefault(path, canonicalValue);
    }

    public synchronized void recordFieldValue(
            String path,
            String value,
            boolean exactInteger
    ) {
        String normalizedPath = Objects.requireNonNull(
                path, "path").strip();
        String preservedValue = Objects.requireNonNullElse(value, "");
        rawFieldValues.put(normalizedPath, preservedValue);
        if (exactInteger && !preservedValue.matches("-?[0-9]+")) {
            invalidNumberPaths.add(normalizedPath);
        } else {
            invalidNumberPaths.remove(normalizedPath);
        }
    }

    public synchronized void acceptFieldValue(
            String path,
            String value
    ) {
        String normalizedPath = Objects.requireNonNull(
                path, "path").strip();
        rawFieldValues.put(normalizedPath,
                Objects.requireNonNullElse(value, ""));
        invalidNumberPaths.remove(normalizedPath);
    }

    public synchronized void clearFieldValues(String pathPrefix) {
        String normalizedPrefix = Objects.requireNonNull(
                pathPrefix, "pathPrefix").strip();
        rawFieldValues.keySet().removeIf(path ->
                path.equals(normalizedPrefix)
                        || path.startsWith(normalizedPrefix + "."));
        invalidNumberPaths.removeIf(path ->
                path.equals(normalizedPrefix)
                        || path.startsWith(normalizedPrefix + "."));
    }

    public synchronized Optional<String> firstInvalidFieldPath(
            String pathPrefix
    ) {
        String normalizedPrefix = Objects.requireNonNull(
                pathPrefix, "pathPrefix").strip();
        return invalidNumberPaths.stream().filter(path ->
                normalizedPrefix.isEmpty()
                        || path.equals(normalizedPrefix)
                        || path.startsWith(
                        normalizedPrefix + ".")).findFirst();
    }

    public synchronized void reject(
            List<OfferValidationIssue> issues
    ) {
        serverIssues = List.copyOf(Objects.requireNonNull(
                issues, "issues"));
        serverIssues.stream().filter(issue -> !issue.path().isBlank())
                .findFirst().ifPresent(issue -> {
                    focusedPath = issue.path();
                    section = Section.forPath(issue.path());
                });
    }

    public synchronized List<OfferValidationIssue> serverIssues() {
        return serverIssues;
    }

    public synchronized void clearServerIssues() {
        serverIssues = List.of();
    }

    public synchronized Section section() {
        return section;
    }

    public synchronized void section(Section section) {
        this.section = Objects.requireNonNull(section, "section");
    }

    public synchronized int scrollPosition() {
        return scrollPositions.getOrDefault(section, 0);
    }

    public synchronized void scrollPosition(int scrollPosition) {
        scrollPositions.put(section, Math.max(0, scrollPosition));
    }

    public synchronized String focusedPath() {
        return focusedPath;
    }

    public synchronized void focusedPath(String focusedPath) {
        this.focusedPath = Objects.requireNonNullElse(
                focusedPath, "");
    }

    public synchronized Set<String> expandedCards() {
        return Set.copyOf(expandedCards);
    }

    public synchronized void setExpanded(
            String cardId,
            boolean expanded
    ) {
        if (expanded) {
            expandedCards.add(cardId);
        } else {
            expandedCards.remove(cardId);
        }
    }

    public synchronized boolean helpVisible() {
        return helpVisible;
    }

    public synchronized void helpVisible(boolean helpVisible) {
        this.helpVisible = helpVisible;
    }

    private static ServerShopOfferListing copySection(
            ServerShopOfferListing current,
            ServerShopOfferListing baseline,
            Section section
    ) {
        return switch (section) {
            case GENERAL -> new ServerShopOfferListing(
                    current.listingId(), current.revision(),
                    baseline.displayName(), baseline.description(),
                    baseline.categoryId(), baseline.iconItemId(),
                    baseline.iconNbt(), baseline.active(),
                    baseline.expiresAtEpoch(),
                    baseline.permissionNode(), current.outputs(),
                    current.acquireOptions(), current.sellOptions(),
                    current.stockPolicy(), current.limits(),
                    current.schedule(), current.bundleComparisons());
            case OUTPUTS -> new ServerShopOfferListing(
                    current.listingId(), current.revision(),
                    current.displayName(), current.description(),
                    current.categoryId(), current.iconItemId(),
                    current.iconNbt(), current.active(),
                    current.expiresAtEpoch(),
                    current.permissionNode(), baseline.outputs(),
                    current.acquireOptions(), current.sellOptions(),
                    current.stockPolicy(), current.limits(),
                    current.schedule(), current.bundleComparisons());
            case GET_OPTIONS -> replaceOptions(
                    current, baseline.acquireOptions(),
                    current.sellOptions());
            case SELL_OPTIONS -> replaceOptions(
                    current, current.acquireOptions(),
                    baseline.sellOptions());
            case STOCK_AND_LIMITS -> new ServerShopOfferListing(
                    current.listingId(), current.revision(),
                    current.displayName(), current.description(),
                    current.categoryId(), current.iconItemId(),
                    current.iconNbt(), current.active(),
                    current.expiresAtEpoch(),
                    current.permissionNode(), current.outputs(),
                    current.acquireOptions(), current.sellOptions(),
                    baseline.stockPolicy(), baseline.limits(),
                    current.schedule(), current.bundleComparisons());
            case SCHEDULE_AND_PERMISSIONS ->
                    new ServerShopOfferListing(
                            current.listingId(), current.revision(),
                            current.displayName(), current.description(),
                            current.categoryId(), current.iconItemId(),
                            current.iconNbt(), current.active(),
                            current.expiresAtEpoch(),
                            baseline.permissionNode(), current.outputs(),
                            current.acquireOptions(),
                            current.sellOptions(),
                            current.stockPolicy(), current.limits(),
                            baseline.schedule(),
                            current.bundleComparisons());
            case BUNDLE_VALUE -> new ServerShopOfferListing(
                    current.listingId(), current.revision(),
                    current.displayName(), current.description(),
                    current.categoryId(), current.iconItemId(),
                    current.iconNbt(), current.active(),
                    current.expiresAtEpoch(),
                    current.permissionNode(), current.outputs(),
                    current.acquireOptions(), current.sellOptions(),
                    current.stockPolicy(), current.limits(),
                    current.schedule(), baseline.bundleComparisons());
            case PREVIEW -> current;
        };
    }

    private static ServerShopOfferListing replaceOptions(
            ServerShopOfferListing current,
            java.util.List<com.enviouse.futureshops.catalog.offer
                    .AcquireOfferOption> acquire,
            java.util.List<com.enviouse.futureshops.catalog.offer
                    .SellOfferOption> sell
    ) {
        return new ServerShopOfferListing(
                current.listingId(), current.revision(),
                current.displayName(), current.description(),
                current.categoryId(), current.iconItemId(),
                current.iconNbt(), current.active(),
                current.expiresAtEpoch(), current.permissionNode(),
                current.outputs(), acquire, sell,
                current.stockPolicy(), current.limits(),
                current.schedule(), current.bundleComparisons());
    }

    public enum Section {
        GENERAL("general"),
        OUTPUTS("outputs"),
        GET_OPTIONS("acquireOptions"),
        SELL_OPTIONS("sellOptions"),
        STOCK_AND_LIMITS("stock"),
        SCHEDULE_AND_PERMISSIONS("schedule"),
        BUNDLE_VALUE("bundleComparisons"),
        PREVIEW("preview");

        private final String pathPrefix;

        Section(String pathPrefix) {
            this.pathPrefix = pathPrefix;
        }

        boolean owns(String path) {
            if (this == GENERAL) {
                return path.startsWith("general")
                        || path.startsWith("display")
                        || path.startsWith("description")
                        || path.startsWith("category")
                        || path.startsWith("icon")
                        || path.startsWith("active");
            }
            if (this == STOCK_AND_LIMITS) {
                return path.startsWith("stock")
                        || path.startsWith("limits");
            }
            if (this == SCHEDULE_AND_PERMISSIONS) {
                return path.startsWith("schedule")
                        || path.startsWith("permission")
                        || path.startsWith("expires");
            }
            return path.startsWith(pathPrefix);
        }

        public static Section forPath(String path) {
            String normalized = Objects.requireNonNullElse(path, "");
            for (Section section : values()) {
                if (section != PREVIEW && section.owns(normalized)) {
                    return section;
                }
            }
            if (normalized.startsWith("display")
                    || normalized.startsWith("description")
                    || normalized.startsWith("category")
                    || normalized.startsWith("icon")
                    || normalized.startsWith("active")
                    || normalized.startsWith("listing")) {
                return GENERAL;
            }
            return GENERAL;
        }
    }
}

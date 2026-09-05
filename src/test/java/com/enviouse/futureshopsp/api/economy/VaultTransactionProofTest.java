package com.enviouse.futureshopsp.api.economy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Proof fixture for the separately installed Vault bridge transaction contract. */
class VaultTransactionProofTest {
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000410");

    @TempDir
    Path directory;

    @BeforeEach
    void setUp() {
        EconomyProviderRegistry.resetForTests();
    }

    @AfterEach
    void tearDown() {
        EconomyProviderRegistry.resetForTests();
    }

    @Test
    void registersThroughPublicVaultBoundaryAndCommitsBalanceWithReceipt() throws IOException {
        DurableVaultBackend backend = new DurableVaultBackend(directory, 100L);
        EconomyProvider provider = new DurableVaultProvider(backend);

        RegistrationResult registration = EconomyProviderRegistry.registerVault(
                EconomyApi.COMPATIBILITY_VERSION, ignored -> provider);

        assertEquals(RegistrationStatus.ACCEPTED, registration.status());
        assertTrue(EconomyProviderRegistry.snapshot().containsKey(EconomyApi.VAULT_PROVIDER_ID));

        MutationRequest request = MutationRequest.forPlayer(RequestId.random(), PLAYER, 25L,
                MutationKind.WITHDRAW);
        ProviderResult<MutationReceipt> result = provider.withdraw(request);

        assertTrue(result.confirmed());
        assertEquals(75L, provider.balance(PLAYER).value().orElseThrow().balanceMinorUnits());
        assertEquals(result.receipt(), provider.lookup(request.requestId()).receipt());
        assertEquals(result.receipt(), provider.retry(request).receipt());
        assertEquals(75L, provider.balance(PLAYER).value().orElseThrow().balanceMinorUnits());

        String persisted = Files.readString(backend.statePath(PLAYER));
        assertTrue(persisted.contains("balance=75\n"));
        assertTrue(persisted.contains("receipt=" + request.requestId().value()));
    }

    @Test
    void interruptedAtomicCommitLeavesPriorStateAndRetryIsSafe() {
        DurableVaultBackend backend = new DurableVaultBackend(directory, 100L);
        EconomyProvider provider = new DurableVaultProvider(backend);
        MutationRequest request = MutationRequest.forPlayer(RequestId.random(), PLAYER, 10L,
                MutationKind.WITHDRAW);

        backend.interruptBeforeCommit(true);
        ProviderResult<MutationReceipt> interrupted = provider.withdraw(request);
        assertEquals(ProviderResultStatus.RECOVERY_REQUIRED, interrupted.status());

        DurableVaultBackend reopened = new DurableVaultBackend(directory, 100L);
        EconomyProvider reopenedProvider = new DurableVaultProvider(reopened);
        assertEquals(100L, reopenedProvider.balance(PLAYER).value().orElseThrow().balanceMinorUnits());
        assertEquals(ProviderError.RECEIPT_NOT_FOUND, reopenedProvider.lookup(request.requestId()).error());

        reopened.interruptBeforeCommit(false);
        ProviderResult<MutationReceipt> retry = reopenedProvider.retry(request);
        assertTrue(retry.confirmed());
        assertEquals(90L, reopenedProvider.balance(PLAYER).value().orElseThrow().balanceMinorUnits());
        assertEquals(retry.receipt(), reopenedProvider.lookup(request.requestId()).receipt());
    }

    @Test
    void rejectsConflictingIdentityAndDeduplicatesConcurrentRequests() throws Exception {
        DurableVaultBackend backend = new DurableVaultBackend(directory, 100L);
        EconomyProvider provider = new DurableVaultProvider(backend);
        RequestId requestId = RequestId.random();
        MutationRequest request = MutationRequest.forPlayer(requestId, PLAYER, 25L, MutationKind.WITHDRAW);

        ProviderResult<MutationReceipt> first = provider.withdraw(request);
        assertTrue(first.confirmed());
        assertEquals(ProviderError.INVALID_REQUEST, provider.withdraw(
                MutationRequest.forPlayer(requestId, PLAYER, 30L, MutationKind.WITHDRAW)).error());
        assertEquals(ProviderError.INVALID_REQUEST, provider.deposit(
                MutationRequest.forPlayer(requestId, PLAYER, 25L, MutationKind.DEPOSIT)).error());
        UUID otherPlayer = UUID.fromString("00000000-0000-0000-0000-000000000411");
        assertEquals(ProviderError.INVALID_REQUEST, provider.withdraw(
                MutationRequest.forPlayer(requestId, otherPlayer, 25L, MutationKind.WITHDRAW)).error());

        MutationRequest insufficient = MutationRequest.forPlayer(RequestId.random(), PLAYER, 1_000L,
                MutationKind.WITHDRAW);
        assertEquals(ProviderError.INSUFFICIENT_FUNDS, provider.withdraw(insufficient).error());
        assertEquals(ProviderError.INSUFFICIENT_FUNDS, provider.retry(insufficient).error());

        DurableVaultBackend concurrentBackend = new DurableVaultBackend(directory.resolve("concurrent"), 100L);
        EconomyProvider concurrentProvider = new DurableVaultProvider(concurrentBackend);
        MutationRequest concurrentRequest = MutationRequest.forPlayer(RequestId.random(), PLAYER, 25L,
                MutationKind.WITHDRAW);
        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            List<Future<ProviderResult<MutationReceipt>>> futures = new ArrayList<>();
            for (int index = 0; index < 4; index++) {
                futures.add(executor.submit(() -> concurrentProvider.withdraw(concurrentRequest)));
            }
            for (Future<ProviderResult<MutationReceipt>> future : futures) {
                ProviderResult<MutationReceipt> result = future.get();
                assertTrue(result.confirmed());
                assertEquals(concurrentRequest.requestId(), result.receipt().orElseThrow().requestId());
            }
        } finally {
            executor.shutdownNow();
        }
        assertEquals(75L, concurrentProvider.balance(PLAYER).value().orElseThrow().balanceMinorUnits());
    }

    private static final class DurableVaultProvider implements EconomyProvider {
        private final DurableVaultBackend backend;

        private DurableVaultProvider(DurableVaultBackend backend) {
            this.backend = backend;
        }

        @Override
        public String providerId() {
            return EconomyApi.VAULT_PROVIDER_ID;
        }

        @Override
        public int compatibilityVersion() {
            return EconomyApi.COMPATIBILITY_VERSION;
        }

        @Override
        public CurrencyMetadata currency() {
            return new CurrencyMetadata("PokeDollar", "PokeDollars", 0);
        }

        @Override
        public ProviderCapabilities capabilities() {
            return ProviderCapabilities.all();
        }

        @Override
        public ProviderReadiness readiness() {
            return new ProviderReadiness(ProviderLifecycle.READY, "");
        }

        @Override
        public ProviderResult<BalanceSnapshot> balance(UUID playerId) {
            return ProviderResult.confirmed(new BalanceSnapshot(playerId, backend.balance(playerId)));
        }

        @Override
        public ProviderResult<BalanceSnapshot> precheck(MutationRequest request) {
            if (request == null || request.amountMinorUnits() <= 0L) {
                return ProviderResult.rejected(ProviderError.INVALID_REQUEST, "request is invalid");
            }
            long balance = backend.balance(request.actor());
            return balance >= request.amountMinorUnits()
                    ? ProviderResult.confirmed(new BalanceSnapshot(request.actor(), balance))
                    : ProviderResult.rejected(ProviderError.INSUFFICIENT_FUNDS, "balance is insufficient");
        }

        @Override
        public ProviderResult<MutationReceipt> withdraw(MutationRequest request) {
            return backend.mutate(request, false);
        }

        @Override
        public ProviderResult<MutationReceipt> deposit(MutationRequest request) {
            return backend.mutate(request, true);
        }

        @Override
        public ProviderResult<MutationReceipt> lookup(RequestId requestId) {
            return backend.lookup(requestId);
        }

        @Override
        public ProviderResult<MutationReceipt> retry(MutationRequest request) {
            return backend.mutate(request, request != null && isCredit(request.kind()));
        }

        private static boolean isCredit(MutationKind kind) {
            return kind == MutationKind.DEPOSIT || kind == MutationKind.REFUND
                    || kind == MutationKind.TRANSFER_CREDIT || kind == MutationKind.COMPENSATION;
        }
    }

    private static final class DurableVaultBackend {
        private final Path directory;
        private final long startingBalance;
        private volatile boolean interruptBeforeCommit;

        private DurableVaultBackend(Path directory, long startingBalance) {
            this.directory = directory;
            this.startingBalance = startingBalance;
        }

        private void interruptBeforeCommit(boolean value) {
            interruptBeforeCommit = value;
        }

        private Path statePath(UUID playerId) {
            return directory.resolve(playerId + ".vault-state");
        }

        private synchronized long balance(UUID playerId) {
            return read(playerId).balance;
        }

        private synchronized ProviderResult<MutationReceipt> mutate(MutationRequest request, boolean credit) {
            if (request == null || request.amountMinorUnits() <= 0L || request.actor() == null) {
                return ProviderResult.rejected(ProviderError.INVALID_REQUEST, "request is invalid");
            }
            State current = read(request.actor());
            LocatedReceipt prior = findReceipt(request.requestId());
            if (prior != null) {
                if (!request.actor().equals(prior.owner())) {
                    return ProviderResult.rejected(ProviderError.INVALID_REQUEST,
                            "request identity conflicts with its durable receipt");
                }
                ReceiptRecord record = prior.record();
                if (record.kind != request.kind() || record.amount != request.amountMinorUnits()) {
                    return ProviderResult.rejected(ProviderError.INVALID_REQUEST,
                            "request conflicts with its durable receipt");
                }
                return ProviderResult.confirmed(record.receipt());
            }
            long next;
            try {
                next = credit ? Math.addExact(current.balance, request.amountMinorUnits())
                        : Math.subtractExact(current.balance, request.amountMinorUnits());
            } catch (ArithmeticException exception) {
                return ProviderResult.rejected(ProviderError.INSUFFICIENT_FUNDS, "balance arithmetic failed");
            }
            if (!credit && next < 0L) {
                return ProviderResult.rejected(ProviderError.INSUFFICIENT_FUNDS, "balance is insufficient");
            }
            MutationReceipt receipt = new MutationReceipt(request.requestId(), request.kind(),
                    request.amountMinorUnits(), "vault:" + request.requestId().value(), OptionalLong.of(next));
            Map<RequestId, ReceiptRecord> receipts = new LinkedHashMap<>(current.receipts);
            receipts.put(request.requestId(), new ReceiptRecord(request.kind(), request.amountMinorUnits(), receipt));
            try {
                writeAtomically(request.actor(), new State(next, receipts));
                return ProviderResult.confirmed(receipt);
            } catch (IOException exception) {
                return ProviderResult.recoveryRequired("vault transaction commit was interrupted");
            }
        }

        private LocatedReceipt findReceipt(RequestId requestId) {
            try {
                if (!Files.exists(directory)) {
                    return null;
                }
                try (var paths = Files.list(directory)) {
                    return paths.filter(path -> path.getFileName().toString().endsWith(".vault-state"))
                            .map(path -> {
                                try {
                                    UUID owner = UUID.fromString(path.getFileName().toString()
                                            .substring(0, path.getFileName().toString().length() - ".vault-state".length()));
                                    ReceiptRecord record = readPath(path).receipts.get(requestId);
                                    return record == null ? null : new LocatedReceipt(owner, record);
                                } catch (IOException | RuntimeException exception) {
                                    throw new IllegalStateException(exception);
                                }
                            })
                            .filter(java.util.Objects::nonNull)
                            .findFirst().orElse(null);
                }
            } catch (IOException exception) {
                throw new IllegalStateException(exception);
            }
        }

        private synchronized ProviderResult<MutationReceipt> lookup(RequestId requestId) {
            if (requestId == null) {
                return ProviderResult.rejected(ProviderError.INVALID_REQUEST, "receipt request is required");
            }
            for (State state : allStates()) {
                ReceiptRecord record = state.receipts.get(requestId);
                if (record != null) {
                    return ProviderResult.confirmed(record.receipt());
                }
            }
            return ProviderResult.rejected(ProviderError.RECEIPT_NOT_FOUND, "vault receipt not found");
        }

        private List<State> allStates() {
            try {
                if (!Files.exists(directory)) {
                    return List.of();
                }
                List<State> states = new ArrayList<>();
                try (var paths = Files.list(directory)) {
                    paths.filter(path -> path.getFileName().toString().endsWith(".vault-state"))
                            .sorted().forEach(path -> {
                                try {
                                    states.add(readPath(path));
                                } catch (IOException exception) {
                                    throw new IllegalStateException(exception);
                                }
                            });
                }
                return states;
            } catch (IOException exception) {
                throw new IllegalStateException(exception);
            }
        }

        private void writeAtomically(UUID playerId, State state) throws IOException {
            Files.createDirectories(directory);
            Path destination = statePath(playerId);
            Path temporary = Files.createTempFile(directory, playerId.toString(), ".pending");
            boolean moved = false;
            try {
                byte[] bytes = serialize(state).getBytes(StandardCharsets.UTF_8);
                try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                    channel.write(ByteBuffer.wrap(bytes));
                    channel.force(true);
                }
                if (interruptBeforeCommit) {
                    throw new IOException("injected interruption before atomic commit");
                }
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
                moved = true;
                forceDirectory();
            } finally {
                if (!moved) {
                    Files.deleteIfExists(temporary);
                }
            }
        }

        private void forceDirectory() {
            try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) {
                channel.force(true);
            } catch (IOException ignored) {
                // Directory fsync is platform specific. The state file is already forced and atomically renamed.
            }
        }

        private State read(UUID playerId) {
            try {
                Path path = statePath(playerId);
                return Files.exists(path) ? readPath(path) : new State(startingBalance, Map.of());
            } catch (IOException exception) {
                throw new IllegalStateException(exception);
            }
        }

        private static State readPath(Path path) throws IOException {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            long balance = Long.MIN_VALUE;
            Map<RequestId, ReceiptRecord> receipts = new LinkedHashMap<>();
            for (String line : lines) {
                if (line.startsWith("balance=")) {
                    balance = Long.parseLong(line.substring("balance=".length()));
                    continue;
                }
                if (!line.startsWith("receipt=")) {
                    continue;
                }
                String[] fields = line.substring("receipt=".length()).split("\\|", -1);
                if (fields.length != 5) {
                    throw new IOException("invalid receipt record");
                }
                RequestId requestId = new RequestId(UUID.fromString(fields[0]));
                MutationKind kind = MutationKind.valueOf(fields[1]);
                long amount = Long.parseLong(fields[2]);
                long resulting = Long.parseLong(fields[4]);
                MutationReceipt receipt = new MutationReceipt(requestId, kind, amount, fields[3],
                        OptionalLong.of(resulting));
                if (receipts.put(requestId, new ReceiptRecord(kind, amount, receipt)) != null) {
                    throw new IOException("duplicate receipt record");
                }
            }
            if (balance == Long.MIN_VALUE) {
                throw new IOException("balance record is missing");
            }
            return new State(balance, receipts);
        }

        private static String serialize(State state) {
            StringBuilder output = new StringBuilder().append("balance=").append(state.balance).append('\n');
            state.receipts.entrySet().stream().sorted(Map.Entry.comparingByKey(Comparator.comparing(id -> id.value().toString())))
                    .forEach(entry -> {
                        MutationReceipt receipt = entry.getValue().receipt();
                        output.append("receipt=").append(receipt.requestId().value()).append('|')
                                .append(receipt.kind()).append('|').append(receipt.amountMinorUnits()).append('|')
                                .append(receipt.externalOperationId()).append('|')
                                .append(receipt.resultingBalanceMinorUnits().orElseThrow()).append('\n');
                    });
            return output.toString();
        }

        private record State(long balance, Map<RequestId, ReceiptRecord> receipts) {
        }

        private record ReceiptRecord(MutationKind kind, long amount, MutationReceipt receipt) {
        }

        private record LocatedReceipt(UUID owner, ReceiptRecord record) {
        }
    }
}

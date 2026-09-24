package fixture;

import com.enviouse.futureshops.api.economy.BalanceSnapshot;
import com.enviouse.futureshops.api.economy.CurrencyMetadata;
import com.enviouse.futureshops.api.economy.EconomyApi;
import com.enviouse.futureshops.api.economy.EconomyProvider;
import com.enviouse.futureshops.api.economy.EconomyProviderRegistry;
import com.enviouse.futureshops.api.economy.FactoryV1;
import com.enviouse.futureshops.api.economy.MutationKind;
import com.enviouse.futureshops.api.economy.MutationReceipt;
import com.enviouse.futureshops.api.economy.MutationRequest;
import com.enviouse.futureshops.api.economy.ProviderCapabilities;
import com.enviouse.futureshops.api.economy.ProviderError;
import com.enviouse.futureshops.api.economy.ProviderReadiness;
import com.enviouse.futureshops.api.economy.ProviderResult;
import com.enviouse.futureshops.api.economy.ProviderResultStatus;
import com.enviouse.futureshops.api.economy.ProviderLifecycle;
import com.enviouse.futureshops.api.economy.RequestId;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.OptionalLong;
import java.util.UUID;

/** Independently compiled API registrant used only by the phase conformance fixture. */
public final class TransactionalProviderRegistrant {
    private TransactionalProviderRegistrant() {
    }

    public static FactoryV1 factory(Path database) {
        return context -> new TransactionalProvider(database);
    }

    public static final class TransactionalProvider implements EconomyProvider {
        private final Path database;

        public TransactionalProvider(Path database) {
            this.database = database;
        }

        @Override
        public String providerId() {
            return "fixture_tx";
        }

        @Override
        public int compatibilityVersion() {
            return EconomyApi.COMPATIBILITY_VERSION;
        }

        @Override
        public CurrencyMetadata currency() {
            return new CurrencyMetadata("Coin", "Coins", 2);
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
            try {
                return ProviderResult.confirmed(new BalanceSnapshot(playerId, readBalance()));
            } catch (IOException exception) {
                return ProviderResult.unavailable(ProviderError.PROVIDER_EXCEPTION,
                        "transactional fixture read failed");
            }
        }

        @Override
        public ProviderResult<BalanceSnapshot> precheck(MutationRequest request) {
            return balance(request.actor());
        }

        @Override
        public ProviderResult<MutationReceipt> withdraw(MutationRequest request) {
            return mutate(request, -request.amountMinorUnits());
        }

        @Override
        public ProviderResult<MutationReceipt> deposit(MutationRequest request) {
            return mutate(request, request.amountMinorUnits());
        }

        @Override
        public ProviderResult<MutationReceipt> lookup(RequestId requestId) {
            try {
                String line = Files.exists(database) ? Files.readString(database) : "";
                if (!line.startsWith(requestId.value().toString() + "|")) {
                    return ProviderResult.rejected(ProviderError.RECEIPT_NOT_FOUND,
                            "transactional fixture receipt not found");
                }
                String[] fields = line.trim().split("\\|", -1);
                MutationKind kind = MutationKind.valueOf(fields[1]);
                long amount = Long.parseLong(fields[2]);
                long balance = Long.parseLong(fields[3]);
                return ProviderResult.confirmed(new MutationReceipt(requestId, kind, amount,
                        "fixture:" + requestId.value(), OptionalLong.of(balance)));
            } catch (IOException | RuntimeException exception) {
                return ProviderResult.unavailable(ProviderError.PROVIDER_EXCEPTION,
                        "transactional fixture lookup failed");
            }
        }

        @Override
        public ProviderResult<MutationReceipt> retry(MutationRequest request) {
            ProviderResult<MutationReceipt> found = lookup(request.requestId());
            return found.status() == ProviderResultStatus.CONFIRMED
                    ? found : mutate(request, request.kind() == MutationKind.WITHDRAW
                    ? -request.amountMinorUnits() : request.amountMinorUnits());
        }

        private synchronized ProviderResult<MutationReceipt> mutate(MutationRequest request,
                                                                       long delta) {
            ProviderResult<MutationReceipt> existing = lookup(request.requestId());
            if (existing.status() == ProviderResultStatus.CONFIRMED) {
                return existing;
            }
            try {
                long current = readBalance();
                long next = Math.addExact(current, delta);
                if (next < 0L) {
                    return ProviderResult.rejected(ProviderError.INSUFFICIENT_FUNDS,
                            "transactional fixture balance is insufficient");
                }
                String record = request.requestId().value() + "|" + request.kind()
                        + "|" + request.amountMinorUnits() + "|" + next + System.lineSeparator();
                Path temporary = database.resolveSibling(database.getFileName() + ".tmp");
                Files.createDirectories(database.toAbsolutePath().getParent());
                try (FileChannel channel = FileChannel.open(temporary,
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                        StandardOpenOption.WRITE)) {
                    channel.write(ByteBuffer.wrap(record.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                    channel.force(true);
                }
                Files.move(temporary, database, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
                return ProviderResult.confirmed(new MutationReceipt(request.requestId(),
                        request.kind(), request.amountMinorUnits(),
                        "fixture:" + request.requestId().value(), OptionalLong.of(next)));
            } catch (IOException | ArithmeticException exception) {
                return ProviderResult.ambiguous("transactional fixture commit failed");
            }
        }

        private long readBalance() throws IOException {
            if (!Files.exists(database)) {
                return 10000L;
            }
            String[] fields = Files.readString(database).trim().split("\\|", -1);
            return Long.parseLong(fields[3]);
        }
    }
}

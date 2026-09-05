package com.enviouse.futureshopsp.vaultproof;

import com.enviouse.futureshopsp.api.economy.MutationKind;
import com.enviouse.futureshopsp.api.economy.MutationReceipt;
import com.enviouse.futureshopsp.api.economy.MutationRequest;
import com.enviouse.futureshopsp.api.economy.ProviderError;
import com.enviouse.futureshopsp.api.economy.ProviderResult;
import com.enviouse.futureshopsp.api.economy.RequestId;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.OptionalLong;
import java.util.UUID;

/** First party SQLite backend used only by the separately packaged Vault proof fixture. */
public final class SqliteVaultProofBackend {
    private static final Object TRANSACTION_LOCK = new Object();

    private final Path directory;
    private final Path databasePath;
    private final long startingBalance;
    private volatile boolean interruptAfterBalanceUpdate;
    private volatile boolean interruptAfterReceiptInsert;
    private volatile boolean interruptBeforeCommit;
    private volatile boolean interruptAfterCommit;

    public SqliteVaultProofBackend(Path directory, long startingBalance) {
        this.directory = directory;
        this.databasePath = directory.resolve("vault-proof.sqlite");
        this.startingBalance = startingBalance;
        initialize();
    }

    public void interruptAfterBalanceUpdate(boolean value) {
        interruptAfterBalanceUpdate = value;
    }

    public void interruptAfterReceiptInsert(boolean value) {
        interruptAfterReceiptInsert = value;
    }

    public void interruptBeforeCommit(boolean value) {
        interruptBeforeCommit = value;
    }

    public void interruptAfterCommit(boolean value) {
        interruptAfterCommit = value;
    }

    public Path databasePath() {
        return databasePath;
    }

    public String journalMode() {
        return pragma("journal_mode");
    }

    public String synchronousMode() {
        String value = pragma("synchronous");
        return switch (value) {
            case "0" -> "off";
            case "1" -> "normal";
            case "2" -> "full";
            case "3" -> "extra";
            default -> value;
        };
    }

    public int receiptCount() {
        try (Connection connection = openConnection();
                PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM receipts");
                ResultSet result = statement.executeQuery()) {
            return result.next() ? result.getInt(1) : 0;
        } catch (SQLException exception) {
            throw failure(exception);
        }
    }

    public long balance(UUID playerId) {
        try (Connection connection = openConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT balance FROM accounts WHERE account_id = ?")) {
            statement.setString(1, playerId.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getLong(1) : startingBalance;
            }
        } catch (SQLException exception) {
            throw failure(exception);
        }
    }

    ProviderResult<MutationReceipt> mutate(MutationRequest request, boolean credit) {
        if (request == null || request.amountMinorUnits() <= 0L || request.actor() == null) {
            return ProviderResult.rejected(ProviderError.INVALID_REQUEST, "request is invalid");
        }
        synchronized (TRANSACTION_LOCK) {
            return mutateLocked(request, credit);
        }
    }

    private ProviderResult<MutationReceipt> mutateLocked(MutationRequest request, boolean credit) {
        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);
            ReceiptRecord prior = findReceipt(connection, request.requestId());
            if (prior != null) {
                if (!request.actor().equals(prior.actor())) {
                    rollback(connection);
                    return ProviderResult.rejected(ProviderError.INVALID_REQUEST,
                            "request identity conflicts with its durable receipt");
                }
                if (prior.kind() != request.kind() || prior.amount() != request.amountMinorUnits()) {
                    rollback(connection);
                    return ProviderResult.rejected(ProviderError.INVALID_REQUEST,
                            "request conflicts with its durable receipt");
                }
                rollback(connection);
                return ProviderResult.confirmed(prior.receipt());
            }

            ensureAccount(connection, request.actor());
            long current = readBalance(connection, request.actor());
            long next;
            try {
                next = credit ? Math.addExact(current, request.amountMinorUnits())
                        : Math.subtractExact(current, request.amountMinorUnits());
            } catch (ArithmeticException exception) {
                rollback(connection);
                return ProviderResult.rejected(ProviderError.INSUFFICIENT_FUNDS, "balance arithmetic failed");
            }
            if (!credit && next < 0L) {
                rollback(connection);
                return ProviderResult.rejected(ProviderError.INSUFFICIENT_FUNDS, "balance is insufficient");
            }

            MutationReceipt receipt = new MutationReceipt(request.requestId(), request.kind(),
                    request.amountMinorUnits(), "vault:" + request.requestId().value(), OptionalLong.of(next));
            updateBalance(connection, request.actor(), next);
            if (interruptAfterBalanceUpdate) {
                rollback(connection);
                return ProviderResult.recoveryRequired("vault transaction interrupted after balance update");
            }
            insertReceipt(connection, request, receipt);
            if (interruptAfterReceiptInsert) {
                rollback(connection);
                return ProviderResult.recoveryRequired("vault transaction interrupted after receipt insert");
            }
            if (interruptBeforeCommit) {
                rollback(connection);
                return ProviderResult.recoveryRequired("vault transaction interrupted before commit");
            }
            connection.commit();
            if (interruptAfterCommit) {
                return ProviderResult.recoveryRequired("vault transaction interrupted after durable commit");
            }
            return ProviderResult.confirmed(receipt);
        } catch (SQLException | RuntimeException exception) {
            return ProviderResult.recoveryRequired("vault transaction failed before durable acknowledgement");
        }
    }

    ProviderResult<MutationReceipt> lookup(RequestId requestId) {
        if (requestId == null) {
            return ProviderResult.rejected(ProviderError.INVALID_REQUEST, "receipt request is required");
        }
        try (Connection connection = openConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT actor, kind, amount, external_id, resulting_balance FROM receipts "
                                + "WHERE request_id = ?")) {
            statement.setString(1, requestId.value().toString());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return ProviderResult.rejected(ProviderError.RECEIPT_NOT_FOUND, "vault receipt not found");
                }
                return ProviderResult.confirmed(readReceipt(requestId, result));
            }
        } catch (SQLException | RuntimeException exception) {
            return ProviderResult.recoveryRequired("vault receipt lookup failed");
        }
    }

    private static ReceiptRecord findReceipt(Connection connection, RequestId requestId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT actor, kind, amount, external_id, resulting_balance FROM receipts WHERE request_id = ?")) {
            statement.setString(1, requestId.value().toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? readReceiptRecord(requestId, result) : null;
            }
        }
    }

    private static ReceiptRecord readReceiptRecord(RequestId requestId, ResultSet result) throws SQLException {
        UUID actor = UUID.fromString(result.getString("actor"));
        MutationKind kind = MutationKind.valueOf(result.getString("kind"));
        long amount = result.getLong("amount");
        long resultingBalance = result.getLong("resulting_balance");
        MutationReceipt receipt = new MutationReceipt(requestId, kind, amount,
                result.getString("external_id"), OptionalLong.of(resultingBalance));
        return new ReceiptRecord(actor, kind, amount, receipt);
    }

    private static MutationReceipt readReceipt(RequestId requestId, ResultSet result) throws SQLException {
        return readReceiptRecord(requestId, result).receipt();
    }

    private void initialize() {
        try {
            Files.createDirectories(directory);
            try (Connection connection = openConnection(); Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA journal_mode = DELETE");
                statement.execute("PRAGMA synchronous = FULL");
                statement.execute("CREATE TABLE IF NOT EXISTS accounts ("
                        + "account_id TEXT PRIMARY KEY, balance INTEGER NOT NULL)");
                statement.execute("CREATE TABLE IF NOT EXISTS receipts ("
                        + "request_id TEXT PRIMARY KEY, actor TEXT NOT NULL, kind TEXT NOT NULL, "
                        + "amount INTEGER NOT NULL, external_id TEXT NOT NULL, "
                        + "resulting_balance INTEGER NOT NULL)");
            }
        } catch (Exception exception) {
            throw failure(exception);
        }
    }

    private Connection openConnection() throws SQLException {
        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath.toAbsolutePath());
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA busy_timeout = 10000");
            statement.execute("PRAGMA synchronous = FULL");
        }
        return connection;
    }

    private String pragma(String name) {
        try (Connection connection = openConnection(); Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("PRAGMA " + name)) {
            return result.next() ? result.getString(1).toLowerCase() : "";
        } catch (SQLException exception) {
            throw failure(exception);
        }
    }

    private void ensureAccount(Connection connection, UUID playerId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT OR IGNORE INTO accounts(account_id, balance) VALUES (?, ?)")) {
            statement.setString(1, playerId.toString());
            statement.setLong(2, startingBalance);
            statement.executeUpdate();
        }
    }

    private static long readBalance(Connection connection, UUID playerId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT balance FROM accounts WHERE account_id = ?")) {
            statement.setString(1, playerId.toString());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new SQLException("account row missing after initialization");
                }
                return result.getLong(1);
            }
        }
    }

    private static void updateBalance(Connection connection, UUID playerId, long balance) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE accounts SET balance = ? WHERE account_id = ?")) {
            statement.setLong(1, balance);
            statement.setString(2, playerId.toString());
            if (statement.executeUpdate() != 1) {
                throw new SQLException("account update did not affect one row");
            }
        }
    }

    private static void insertReceipt(Connection connection, MutationRequest request,
            MutationReceipt receipt) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO receipts(request_id, actor, kind, amount, external_id, resulting_balance) "
                        + "VALUES (?, ?, ?, ?, ?, ?)")) {
            statement.setString(1, request.requestId().value().toString());
            statement.setString(2, request.actor().toString());
            statement.setString(3, request.kind().name());
            statement.setLong(4, request.amountMinorUnits());
            statement.setString(5, receipt.externalOperationId());
            statement.setLong(6, receipt.resultingBalanceMinorUnits().orElseThrow());
            statement.executeUpdate();
        }
    }

    private static void rollback(Connection connection) {
        try {
            connection.rollback();
        } catch (SQLException ignored) {
            // The transaction is already being rejected and no committed receipt is acknowledged.
        }
    }

    private static IllegalStateException failure(Exception exception) {
        return new IllegalStateException("vault proof backend failure", exception);
    }

    private record ReceiptRecord(UUID actor, MutationKind kind, long amount, MutationReceipt receipt) {
    }
}

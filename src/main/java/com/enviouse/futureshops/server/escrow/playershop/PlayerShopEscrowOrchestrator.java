package com.enviouse.futureshops.server.escrow.playershop;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public final class PlayerShopEscrowOrchestrator {
    private final PlayerShopEscrowBackend backend;
    private final Clock clock;

    public PlayerShopEscrowOrchestrator(
            PlayerShopEscrowBackend backend,
            Clock clock
    ) {
        this.backend = Objects.requireNonNull(backend, "backend");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public Result execute(Command command) {
        Objects.requireNonNull(command, "command");
        PlayerShopPacketResponseIdentity responseIdentity =
                PlayerShopPacketResponseIdentity.from(
                        command.requestIdentity());
        try {
            PlayerShopConservationValidator.requireConserved(command.intent());
        } catch (RuntimeException exception) {
            return Result.failure(responseIdentity, Status.REJECTED,
                    "CONSERVATION_REJECTED", exception.getMessage());
        }
        try {
            Optional<PlayerShopExecutionSnapshot> loaded = backend.load(
                    command.requestIdentity().requestId());
            if (loaded.isPresent()) {
                PlayerShopExecutionSnapshot snapshot = loaded.orElseThrow();
                if (!matches(command, snapshot)) {
                    return Result.failure(responseIdentity, Status.CONFLICT,
                            "REQUEST_CONFLICT",
                            "Request id was already used for different player shop evidence");
                }
                return resume(snapshot, snapshot.commit() != null);
            }
            PlayerShopExecutionSnapshot snapshot =
                    PlayerShopExecutionSnapshot.intentOnly(
                            command.requestIdentity(), command.intent(),
                            command.settlementImport());
            backend.persistIntent(snapshot);
            return resume(snapshot, false);
        } catch (PlayerShopBackendException exception) {
            return handleBackendFailure(command, responseIdentity, exception);
        } catch (RuntimeException exception) {
            return handleUnexpectedFailure(command, responseIdentity,
                    exception);
        }
    }

    private Result resume(
            PlayerShopExecutionSnapshot initial,
            boolean committedReplay
    ) {
        PlayerShopExecutionSnapshot snapshot = initial;
        PlayerShopPreparedExecution preparation = snapshot.preparation();
        if (preparation == null) {
            preparation = backend.prepare(snapshot.requestIdentity(),
                    snapshot.intent());
            if (!preparation.requestIdentity().equals(
                    snapshot.requestIdentity())
                    || !preparation.intent().equals(snapshot.intent())) {
                throw new PlayerShopBackendException(
                        PlayerShopBackendException.Kind.CONFLICT,
                        "Backend returned preparation for different player shop evidence");
            }
            backend.persistPreparation(preparation);
            snapshot = snapshot.withPreparation(preparation);
        }

        PlayerShopFundingEvidence funding = snapshot.funding();
        if (funding == null
                || funding.status()
                == PlayerShopFundingEvidence.Status.RECOVERY_REQUIRED) {
            funding = backend.commitFunding(preparation);
            if (!funding.requestId().equals(snapshot.intent().requestId())) {
                throw new PlayerShopBackendException(
                        PlayerShopBackendException.Kind.CONFLICT,
                        "Backend returned funding for a different request");
            }
            backend.persistFunding(funding);
            snapshot = snapshot.withFunding(funding);
        }
        if (funding.status() != PlayerShopFundingEvidence.Status.COMPLETE
                || !funding.completeFor(preparation)) {
            return recover(snapshot, funding.status()
                    == PlayerShopFundingEvidence.Status.QUARANTINED);
        }

        PlayerShopClaimCreationEvidence claims = snapshot.claimCreation();
        if (claims == null) {
            claims = backend.createClaims(preparation, funding);
            if (!claims.completeFor(snapshot.intent())) {
                return recover(snapshot,
                        claims.status()
                                == PlayerShopClaimCreationEvidence.Status.QUARANTINED);
            }
            backend.persistClaimCreation(claims);
            snapshot = snapshot.withClaims(claims);
        }

        PlayerShopAtomicCommit commit = snapshot.commit();
        if (commit == null) {
            Instant committedAt = clock.instant();
            if (committedAt.isBefore(preparation.preparedAt())) {
                committedAt = preparation.preparedAt();
            }
            commit = PlayerShopAtomicCommit.create(snapshot.intent(),
                    committedAt, funding.moneyReceipts(),
                    funding.itemReceipts(), funding.storageReceipts());
            backend.persistCommit(commit);
            snapshot = snapshot.withCommit(commit);
        }

        if (snapshot.settlementImport() != null) {
            backend.markSettlementImported(snapshot.settlementImport(), commit);
        }
        PlayerShopEscrowBackend.DeliveryResult delivery =
                backend.deliverClaims(commit, preparation);
        return deliveryResult(snapshot.requestIdentity(), commit, delivery,
                committedReplay);
    }

    private Result deliveryResult(
            PlayerShopRequestIdentity request,
            PlayerShopAtomicCommit commit,
            PlayerShopEscrowBackend.DeliveryResult delivery,
            boolean committedReplay
    ) {
        PlayerShopPacketResponseIdentity response =
                PlayerShopPacketResponseIdentity.from(request);
        return switch (delivery.status()) {
            case DELIVERED -> Result.success(response,
                    committedReplay ? Status.REPLAYED : Status.COMMITTED,
                    commit, "OK", "");
            case CLAIMS_PENDING -> Result.success(response,
                    Status.COMMITTED_WITH_PENDING_DELIVERY, commit,
                    "CLAIMS_PENDING", delivery.detail());
            case RECOVERY_REQUIRED -> Result.success(response,
                    Status.RECOVERY_REQUIRED, commit,
                    "DELIVERY_RECOVERY_REQUIRED", delivery.detail());
            case QUARANTINED -> Result.success(response, Status.QUARANTINED,
                    commit, "DELIVERY_QUARANTINED", delivery.detail());
        };
    }

    private Result recover(
            PlayerShopExecutionSnapshot snapshot,
            boolean alreadyQuarantined
    ) {
        PlayerShopEscrowBackend.RecoveryResult recovery =
                backend.recover(snapshot);
        boolean quarantined = alreadyQuarantined
                || recovery.status()
                == PlayerShopEscrowBackend.RecoveryStatus.QUARANTINED;
        return Result.failure(PlayerShopPacketResponseIdentity.from(
                        snapshot.requestIdentity()),
                quarantined ? Status.QUARANTINED : Status.RECOVERY_REQUIRED,
                quarantined ? "QUARANTINED" : "RECOVERY_REQUIRED",
                recovery.detail());
    }

    private Result handleBackendFailure(
            Command command,
            PlayerShopPacketResponseIdentity response,
            PlayerShopBackendException exception
    ) {
        if (exception.kind() == PlayerShopBackendException.Kind.CONFLICT) {
            return Result.failure(response, Status.CONFLICT,
                    "BACKEND_CONFLICT", exception.getMessage());
        }
        if (exception.kind() == PlayerShopBackendException.Kind.REJECTED) {
            return Result.failure(response, Status.REJECTED,
                    "BACKEND_REJECTED", exception.getMessage());
        }
        Optional<PlayerShopExecutionSnapshot> snapshot = backend.load(
                command.requestIdentity().requestId());
        if (snapshot.isPresent() && matches(command, snapshot.orElseThrow())) {
            return recover(snapshot.orElseThrow(), exception.kind()
                    == PlayerShopBackendException.Kind.QUARANTINED);
        }
        return Result.failure(response,
                exception.kind() == PlayerShopBackendException.Kind.QUARANTINED
                        ? Status.QUARANTINED : Status.RECOVERY_REQUIRED,
                exception.kind() == PlayerShopBackendException.Kind.QUARANTINED
                        ? "QUARANTINED" : "RECOVERY_REQUIRED",
                exception.getMessage());
    }

    private Result handleUnexpectedFailure(
            Command command,
            PlayerShopPacketResponseIdentity response,
            RuntimeException exception
    ) {
        try {
            Optional<PlayerShopExecutionSnapshot> snapshot = backend.load(
                    command.requestIdentity().requestId());
            if (snapshot.isPresent()
                    && matches(command, snapshot.orElseThrow())) {
                return recover(snapshot.orElseThrow(), false);
            }
        } catch (RuntimeException ignored) {
            return Result.failure(response, Status.RECOVERY_REQUIRED,
                    "UNEXPECTED_FAILURE", safeDetail(exception));
        }
        return Result.failure(response, Status.RECOVERY_REQUIRED,
                "UNEXPECTED_FAILURE", safeDetail(exception));
    }

    private static boolean matches(
            Command command,
            PlayerShopExecutionSnapshot snapshot
    ) {
        return snapshot.requestIdentity().equals(command.requestIdentity())
                && snapshot.intent().intentFingerprint().equals(
                command.intent().intentFingerprint())
                && Objects.equals(snapshot.settlementImport(),
                command.settlementImport());
    }

    private static String safeDetail(RuntimeException exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return "Unexpected player shop backend failure";
        }
        String normalized = message.strip();
        return normalized.length() <= PlayerShopEscrowConstants.MAX_TEXT_LENGTH
                ? normalized : normalized.substring(0,
                PlayerShopEscrowConstants.MAX_TEXT_LENGTH);
    }

    public record Command(
            PlayerShopRequestIdentity requestIdentity,
            PlayerShopEscrowIntent intent,
            PlayerShopSettlementImportEvidence settlementImport
    ) {
        public Command {
            requestIdentity = Objects.requireNonNull(requestIdentity,
                    "requestIdentity");
            intent = Objects.requireNonNull(intent, "intent");
            if (!requestIdentity.matches(intent)
                    || settlementImport != null
                    && !settlementImport.matches(intent)
                    || intent.operation() == PlayerShopOperation.SETTLEMENT_CLAIM
                    != (settlementImport != null)) {
                throw new IllegalArgumentException("Player shop command identity is invalid");
            }
        }

        public static Command of(
                PlayerShopEscrowIntent intent,
                int responseToken
        ) {
            return new Command(PlayerShopRequestIdentity.from(intent,
                    responseToken), intent, null);
        }

        public static Command settlement(
                PlayerShopEscrowIntent intent,
                int responseToken,
                PlayerShopSettlementImportEvidence settlement
        ) {
            return new Command(PlayerShopRequestIdentity.from(intent,
                    responseToken), intent, settlement);
        }
    }

    public record Result(
            PlayerShopPacketResponseIdentity responseIdentity,
            Status status,
            PlayerShopAtomicCommit commit,
            String code,
            String detail
    ) {
        public Result {
            responseIdentity = Objects.requireNonNull(responseIdentity,
                    "responseIdentity");
            status = Objects.requireNonNull(status, "status");
            code = PlayerShopBinarySupport.requireString(code, 64,
                    "orchestrator result code");
            detail = PlayerShopBinarySupport.optionalString(detail,
                    PlayerShopEscrowConstants.MAX_TEXT_LENGTH,
                    "orchestrator result detail");
            if (status.hasCommit() && commit == null
                    || !status.hasCommit() && commit != null
                    && status != Status.RECOVERY_REQUIRED
                    && status != Status.QUARANTINED) {
                throw new IllegalArgumentException("Player shop result commit is invalid");
            }
            if (commit != null && !commit.commitId().equals(
                    responseIdentity.requestId())) {
                throw new IllegalArgumentException("Player shop result identity is invalid");
            }
        }

        static Result success(
                PlayerShopPacketResponseIdentity identity,
                Status status,
                PlayerShopAtomicCommit commit,
                String code,
                String detail
        ) {
            return new Result(identity, status, commit, code, detail);
        }

        static Result failure(
                PlayerShopPacketResponseIdentity identity,
                Status status,
                String code,
                String detail
        ) {
            return new Result(identity, status, null, code,
                    detail == null ? "" : detail);
        }

        public Optional<PlayerShopAtomicCommit> committedValue() {
            return Optional.ofNullable(commit);
        }
    }

    public enum Status {
        COMMITTED(true),
        REPLAYED(true),
        COMMITTED_WITH_PENDING_DELIVERY(true),
        RECOVERY_REQUIRED(false),
        QUARANTINED(false),
        CONFLICT(false),
        REJECTED(false);

        private final boolean hasCommit;

        Status(boolean hasCommit) {
            this.hasCommit = hasCommit;
        }

        public boolean hasCommit() {
            return hasCommit;
        }
    }
}

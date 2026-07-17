package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.server.escrow.admin.MaintenanceRepairCommand;

public interface MaintenanceRuntimeMutationHandler {
    MaintenanceRuntimeSnapshot snapshot();

    MaintenanceRuntimeSnapshot plan(MaintenanceRepairCommand command);

    void apply(MaintenanceRepairCommand command, MaintenanceRuntimeSnapshot result);

    boolean isCurrent(MaintenanceRuntimeSnapshot result);

    boolean wasApplied(MaintenanceRuntimeSnapshot result);

    static MaintenanceRuntimeMutationHandler unavailable() {
        return UnavailableHolder.INSTANCE;
    }

    final class UnavailableHolder {
        private static final MaintenanceRuntimeMutationHandler INSTANCE =
                new MaintenanceRuntimeMutationHandler() {
                    @Override
                    public MaintenanceRuntimeSnapshot snapshot() {
                        throw unavailable();
                    }

                    @Override
                    public MaintenanceRuntimeSnapshot plan(MaintenanceRepairCommand command) {
                        throw unavailable();
                    }

                    @Override
                    public void apply(MaintenanceRepairCommand command,
                                      MaintenanceRuntimeSnapshot result) {
                        throw unavailable();
                    }

                    @Override
                    public boolean isCurrent(MaintenanceRuntimeSnapshot result) {
                        return false;
                    }

                    @Override
                    public boolean wasApplied(MaintenanceRuntimeSnapshot result) {
                        return false;
                    }

                    private EscrowRuntimeException unavailable() {
                        return new EscrowRuntimeException(
                                "Maintenance runtime mutation handler is unavailable");
                    }
                };

        private UnavailableHolder() {
        }
    }
}

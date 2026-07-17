package com.enviouse.futureshops.server.escrow.custody;

@FunctionalInterface
public interface CustodyPrepareCommitter {
    void commit(CustodyPreparedOperation intent);
}

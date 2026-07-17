package com.enviouse.futureshops.server.escrow.custody;

@FunctionalInterface
public interface CustodyBatchCommitter {
    void commit(CustodyBatchCommit commit);
}

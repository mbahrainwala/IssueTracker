package behrainwala.issuetracker.service;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Defers work until the surrounding transaction commits.
 * <p>
 * Used for deleting files. Unlinking inline would destroy the bytes for good if the
 * transaction then rolled back, leaving rows pointing at nothing; waiting for the commit
 * inverts the failure, so a failed unlink merely orphans a file - wasted disk, nothing lost.
 * Outside a transaction the work runs immediately.
 */
final class AfterCommit {

    private AfterCommit() {
    }

    static void run(Runnable work) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            work.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                work.run();
            }
        });
    }
}

package com.socialnetwork.chat_service.support;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Defers side effects that must not happen when the surrounding transaction rolls back. */
public final class TransactionSupport {

  private TransactionSupport() {}

  /**
   * Runs {@code action} after the current transaction commits, or immediately when there is no
   * transaction in progress.
   */
  public static void afterCommit(Runnable action) {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              action.run();
            }
          });
    } else {
      action.run();
    }
  }
}

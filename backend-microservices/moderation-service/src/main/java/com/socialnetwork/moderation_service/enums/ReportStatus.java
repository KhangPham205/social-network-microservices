package com.socialnetwork.moderation_service.enums;

/** Lifecycle of a report. */
public enum ReportStatus {
  PENDING,
  /** A moderator confirmed the violation. */
  APPROVED,
  /** A moderator found no violation. */
  REJECTED
}

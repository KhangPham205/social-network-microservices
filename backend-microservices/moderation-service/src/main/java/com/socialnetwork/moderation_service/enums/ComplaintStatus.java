package com.socialnetwork.moderation_service.enums;

/** Lifecycle of a complaint filed by the owner of moderated content. */
public enum ComplaintStatus {
  PENDING,
  /** Accepted: the content is restored. */
  APPROVED,
  /** Rejected: the content stays hidden. */
  REJECTED
}

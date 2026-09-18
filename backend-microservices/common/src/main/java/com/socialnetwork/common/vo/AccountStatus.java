package com.socialnetwork.common.vo;

/** Lifecycle of a credential, owned by auth-service and mirrored read-only by other services. */
public enum AccountStatus {
  /** Registered, waiting for user-service to create the profile (saga in flight). */
  WAITING,
  /** Profile created, e-mail not verified yet. */
  PENDING,
  ACTIVE,
  /** Blocked by moderation. */
  BLOCKED,
  NOT_AUTHORIZED,
  NOT_SOLVED
}

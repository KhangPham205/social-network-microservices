package com.socialnetwork.common.vo;

/** State of the (directional) friendship row between two users. */
public enum FriendshipStatus {
  /** Request sent, waiting for the receiver. */
  PENDING,
  FRIEND,
  /** Not persisted: rejecting deletes the row. Kept for API responses. */
  REJECTED,
  /** sender blocked receiver. */
  BLOCKED,
  /** Not persisted: "no relationship" marker for API responses. */
  NONE
}

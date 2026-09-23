package com.socialnetwork.moderation_service.enums;

/** The action a {@code ModerationLog} row records. Stored as a string. */
public enum ModerationLogAction {
  /** The AI pipeline hid the content without any human involvement. */
  AUTO_BAN,
  /** An administrator hid a post, comment or message. */
  ADMIN_BLOCK,
  /** An administrator restored a post, comment or message. */
  ADMIN_UNBLOCK,
  /** An administrator blocked a user account. */
  USER_BLOCK,
  /** An administrator unblocked a user account. */
  USER_UNBLOCK
}

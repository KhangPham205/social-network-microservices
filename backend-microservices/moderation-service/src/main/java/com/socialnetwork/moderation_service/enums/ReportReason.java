package com.socialnetwork.moderation_service.enums;

/** Why a piece of content was reported. */
public enum ReportReason {
  SPAM,
  HATE_SPEECH,
  HARASSMENT,
  NUDITY,
  VIOLENCE,
  TERRORISM,
  COPYRIGHT_VIOLATION,
  /** Raised by the automated AI moderation pipeline, never chosen by a human reporter. */
  AI_DETECTED,
  OTHER
}

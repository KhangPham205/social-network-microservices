package com.socialnetwork.notification_service.infra.websocket;

/** Names shared between the handshake and the STOMP layer. */
public final class WebSocketAttributes {

  private WebSocketAttributes() {}

  /** Session attribute holding the authenticated user id (Long) set during the handshake. */
  public static final String USER_ID = "userId";

  /** Query parameter accepted as an alternative token carrier for non-browser clients. */
  public static final String TOKEN_PARAM = "token";
}

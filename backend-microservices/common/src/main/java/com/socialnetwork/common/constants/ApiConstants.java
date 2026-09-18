package com.socialnetwork.common.constants;

/** Base REST paths shared by the services and the API gateway. */
public final class ApiConstants {

  private ApiConstants() {}

  public static final String API_V1 = "/api/v1";

  /** auth-service. */
  public static final String AUTH = API_V1 + "/auth";

  /** user-service. */
  public static final String USERS = API_V1 + "/users";

  /** media-service (posts, comments, reacts, recommendations). */
  public static final String MEDIA = API_V1 + "/media";

  /** chat-service. */
  public static final String CHAT = API_V1 + "/chat";

  public static final String CONVERSATIONS = CHAT + "/conversations";
  public static final String MESSAGES = CHAT + "/messages";

  /** notification-service. */
  public static final String NOTIFICATIONS = API_V1 + "/notifications";

  /** moderation-service. */
  public static final String MODERATION = API_V1 + "/moderation";

  /**
   * Sub-path used by every service for service-to-service endpoints. These must never be reachable
   * through the public gateway.
   */
  public static final String INTERNAL = "/internal";

  /** STOMP WebSocket handshake prefix. */
  public static final String WEBSOCKET = "/ws";

  /** Paths every service leaves open for OpenAPI documentation. */
  public static final String[] SWAGGER_WHITELIST = {
    "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html"
  };
}

package com.socialnetwork.common.constants;

/** Names shared between the token issuer (auth-service) and every resource server. */
public final class SecurityConstants {

  private SecurityConstants() {}

  public static final String JWT_COOKIE = "jwt";
  public static final String REFRESH_TOKEN_COOKIE = "refreshToken";
  public static final String BEARER_PREFIX = "Bearer ";

  public static final String CLAIM_USER_ID = "userId";
  public static final String CLAIM_ROLES = "roles";

  public static final String ROLE_PREFIX = "ROLE_";
  public static final String ROLE_ADMIN = "ADMIN";
  public static final String ROLE_USER = "USER";

  /** Header carrying the shared secret for service-to-service calls to /internal/** endpoints. */
  public static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

  public static final String ROLE_INTERNAL = "INTERNAL";
  public static final String INTERNAL_PRINCIPAL = "internal-service";
}

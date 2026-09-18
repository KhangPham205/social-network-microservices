package com.socialnetwork.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

class GlobalExceptionHandlerTest {

  private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
  private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/things/1");

  @Test
  void businessExceptionsKeepTheirStatus() {
    assertThat(handler.handleApi(new ResourceNotFoundException("gone"), request).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(handler.handleApi(new AccessDeniedException("no"), request).getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(handler.handleApi(new InvalidCredentialsException("bad"), request).getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(handler.handleApi(new BadRequestException("bad"), request).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(handler.handleApi(new ConflictException("dup"), request).getStatusCode())
        .isEqualTo(HttpStatus.CONFLICT);
  }

  @Test
  void unexpectedExceptionIsGenericAndCarriesPath() {
    ResponseEntity<ErrorResponse> response =
        handler.handleUnexpected(new RuntimeException("secret sql text"), request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().message()).doesNotContain("secret sql text");
    assertThat(response.getBody().path()).isEqualTo("/api/v1/things/1");
    assertThat(response.getBody().statusCode()).isEqualTo(500);
  }

  @Test
  void springAccessDeniedIsForbidden() {
    assertThat(
            handler
                .handleSpringAccessDenied(
                    new org.springframework.security.access.AccessDeniedException("x"), request)
                .getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
  }
}

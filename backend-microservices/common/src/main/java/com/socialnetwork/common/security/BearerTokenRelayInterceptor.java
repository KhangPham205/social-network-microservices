package com.socialnetwork.common.security;

import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/**
 * Forwards the caller's access token as {@code Authorization: Bearer} to a downstream service, so
 * the downstream applies the same user's permissions. No-op outside a request (e.g. Kafka threads).
 */
public class BearerTokenRelayInterceptor implements ClientHttpRequestInterceptor {

  @Override
  public ClientHttpResponse intercept(
      HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
    SecurityUtils.getCurrentToken()
        .ifPresent(t -> request.getHeaders().setBearerAuth(t));
    return execution.execute(request, body);
  }
}

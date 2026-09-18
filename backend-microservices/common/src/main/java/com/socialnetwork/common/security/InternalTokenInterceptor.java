package com.socialnetwork.common.security;

import static com.socialnetwork.common.constants.SecurityConstants.INTERNAL_TOKEN_HEADER;

import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/** Adds {@code X-Internal-Token} to outgoing {@code RestClient} calls between services. */
@RequiredArgsConstructor
public class InternalTokenInterceptor implements ClientHttpRequestInterceptor {

  private final String token;

  @Override
  public ClientHttpResponse intercept(
      HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
    request.getHeaders().set(INTERNAL_TOKEN_HEADER, token);
    return execution.execute(request, body);
  }
}

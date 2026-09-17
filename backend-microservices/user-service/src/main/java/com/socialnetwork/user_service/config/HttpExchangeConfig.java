package com.socialnetwork.user_service.config;

import com.socialnetwork.user_service.client.AuthClient;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

@Configuration
public class HttpExchangeConfig {

  @Bean
  @Primary
  public RestClient.Builder defaultRestClientBuilder() {
    return RestClient.builder();
  }

  @Bean(name = "microserviceBuilder")
  @LoadBalanced
  public RestClient.Builder loadBalancedRestClientBuilder() {

    RequestConfig requestConfig =
        RequestConfig.custom()
            .setConnectionRequestTimeout(Timeout.ofSeconds(2)) // timeout connect
            .setResponseTimeout(Timeout.ofSeconds(5)) // timeout read response
            .build();

    CloseableHttpClient httpClient =
        HttpClients.custom().setDefaultRequestConfig(requestConfig).build();

    HttpComponentsClientHttpRequestFactory factory =
        new HttpComponentsClientHttpRequestFactory(httpClient);

    return RestClient.builder()
        .requestFactory(factory)
        .requestInterceptor(
            (request, body, execution) -> {
              ServletRequestAttributes attributes =
                  (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
              if (attributes != null) {
                HttpServletRequest servletRequest = attributes.getRequest();
                String authHeader = servletRequest.getHeader(HttpHeaders.AUTHORIZATION);
                if (authHeader != null && authHeader.startsWith("Bearer ")) {
                  request.getHeaders().set(HttpHeaders.AUTHORIZATION, authHeader);
                } else if (servletRequest.getCookies() != null) {
                  for (Cookie cookie : servletRequest.getCookies()) {
                    if ("jwt".equals(cookie.getName())) {
                      request.getHeaders().add(HttpHeaders.COOKIE, "jwt=" + cookie.getValue());
                      request.getHeaders().setBearerAuth(cookie.getValue());
                      break;
                    }
                  }
                }
              }
              return execution.execute(request, body);
            });
  }

  @Bean
  public AuthClient authClient(@Qualifier("microserviceBuilder") RestClient.Builder builder) {
    RestClient restClient = builder.baseUrl("http://auth-service").build();
    RestClientAdapter adapter = RestClientAdapter.create(restClient);
    return HttpServiceProxyFactory.builderFor(adapter).build().createClient(AuthClient.class);
  }
}

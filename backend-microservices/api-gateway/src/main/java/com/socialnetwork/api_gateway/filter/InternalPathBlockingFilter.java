package com.socialnetwork.api_gateway.filter;

import java.util.Locale;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.PathContainer;
import org.springframework.http.server.RequestPath;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Rejects every request that targets a service-to-service endpoint ({@code
 * /api/v1/<svc>/internal/**} or any path containing an {@code internal} segment) with {@code 404
 * Not Found}.
 *
 * <p>Implemented as a {@link WebFilter} with the highest precedence so that it runs before CORS
 * handling and before route matching: no route, predicate or downstream security rule can expose
 * an internal endpoint through the public entry point. Segments are compared on their decoded
 * value (so {@code %69nternal} is caught) and path-traversal segments are rejected as well.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class InternalPathBlockingFilter implements WebFilter {

  private static final String INTERNAL_SEGMENT = "internal";

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    if (isBlocked(exchange.getRequest().getPath())) {
      exchange.getResponse().setStatusCode(HttpStatus.NOT_FOUND);
      return exchange.getResponse().setComplete();
    }
    return chain.filter(exchange);
  }

  static boolean isBlocked(RequestPath path) {
    for (PathContainer.Element element : path.pathWithinApplication().elements()) {
      if (element instanceof PathContainer.PathSegment segment) {
        String value = segment.valueToMatch().toLowerCase(Locale.ROOT);
        if (value.equals(INTERNAL_SEGMENT)
            || value.contains("/" + INTERNAL_SEGMENT)
            || value.equals("..")) {
          return true;
        }
      }
    }
    return path.value().toLowerCase(Locale.ROOT).contains("/" + INTERNAL_SEGMENT + "/");
  }
}

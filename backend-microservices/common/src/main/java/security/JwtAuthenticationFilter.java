package security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private final JwtValidator jwtValidator;

  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull FilterChain filterChain)
      throws ServletException, IOException {

    String path = request.getRequestURI();

    log.info("Cookies = {}", Arrays.toString(request.getCookies()));

    Cookie[] cookies = request.getCookies();

    if (cookies != null) {
      log.info("Cookies = {}", Arrays.toString(cookies));
      for (Cookie cookie : cookies) {
        log.info("Cookie name = {}, value = {}", cookie.getName(), cookie.getValue());
      }
    } else {
      log.info("Cookies = null (No cookies found in request)");
    }

    if (path.startsWith("/ws")) {
      filterChain.doFilter(request, response);
      return;
    }

    String token = getJwtFromCookies(request);

    if (token != null
        && jwtValidator.validateToken(token)
        && SecurityContextHolder.getContext().getAuthentication() == null) {

      Long userId = jwtValidator.extractUserId(token);

      UsernamePasswordAuthenticationToken authToken =
          new UsernamePasswordAuthenticationToken(userId.toString(), null, new ArrayList<>());

      authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
      SecurityContextHolder.getContext().setAuthentication(authToken);
    }

    filterChain.doFilter(request, response);
  }

  private String getJwtFromCookies(HttpServletRequest request) {
    // 1. Kiểm tra Authorization Header trước (Authorization: Bearer <token>)
    String authHeader = request.getHeader("Authorization");
    if (authHeader != null && authHeader.startsWith("Bearer ")) {
      return authHeader.substring(7);
    }

    // 2. Nếu không có, kiểm tra Cookies
    if (request.getCookies() != null) {
      for (Cookie cookie : request.getCookies()) {
        if ("jwt".equals(cookie.getName())) {
          return cookie.getValue();
        }
      }
    }
    return null;
  }
}

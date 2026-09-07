package com.agc.cms.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String AUTH_USER_ATTR = "authenticatedUser";
    private final JwtTokenProvider jwtTokenProvider;

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();

        // Allow CORS pre-flight requests
        if ("OPTIONS".equalsIgnoreCase(method)) {
            return true;
        }

        // Public endpoints
        return path.equals("/api/auth/login") ||
               path.equals("/api/health") ||
               path.startsWith("/uploads/") ||
               !path.startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String token = extractToken(request);

        if (token != null) {
            AuthenticatedUser user = jwtTokenProvider.validateAndExtractUser(token);
            if (user != null) {
                request.setAttribute(AUTH_USER_ATTR, user);
                filterChain.doFilter(request, response);
                return;
            }
        }

        // Token missing or invalid on protected API route
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"error\": \"Unauthorized: Missing or invalid authentication token\", \"status\": 401}");
    }

    private String extractToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7).trim();
        }

        // Fallback: Check cookies for agc_jwt
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie c : cookies) {
                if ("agc_jwt".equals(c.getName())) {
                    return c.getValue();
                }
            }
        }

        return null;
    }
}

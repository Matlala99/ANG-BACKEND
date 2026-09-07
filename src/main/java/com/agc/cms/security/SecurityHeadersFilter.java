package com.agc.cms.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SecurityHeadersFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // Content Security Policy
        response.setHeader("Content-Security-Policy",
                "default-src 'self'; " +
                "script-src 'self' 'unsafe-inline'; " +
                "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; " +
                "font-src 'self' https://fonts.gstatic.com data:; " +
                "img-src 'self' data: blob: http: https:; " +
                "connect-src 'self' http://localhost:8097 http://127.0.0.1:8097 http://localhost:4200 http://127.0.0.1:4200 http://localhost:5173 http://127.0.0.1:5173; " +
                "frame-ancestors 'none';");

        // Prevent MIME type sniffing
        response.setHeader("X-Content-Type-Options", "nosniff");

        // Clickjacking defense
        response.setHeader("X-Frame-Options", "DENY");

        // Legacy XSS Protection
        response.setHeader("X-XSS-Protection", "1; mode=block");

        // Referrer policy
        response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");

        // Permissions Policy (disable unneeded hardware access)
        response.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=(), payment=()");

        // Cache control for API data
        if (request.getRequestURI().startsWith("/api/")) {
            response.setHeader("Cache-Control", "no-cache, no-store, max-age=0, must-revalidate");
            response.setHeader("Pragma", "no-cache");
            response.setHeader("Expires", "0");
        }

        filterChain.doFilter(request, response);
    }
}

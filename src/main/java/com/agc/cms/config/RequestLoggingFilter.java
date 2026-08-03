package com.agc.cms.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        long startTime = System.currentTimeMillis();
        String timestamp = LocalDateTime.now().format(FORMATTER);
        String method = request.getMethod();
        String uri = request.getRequestURI();
        String queryString = request.getQueryString();

        System.out.println(String.format("[%s] 🚀 [REQUEST] %s %s%s",
                timestamp, method, uri, (queryString != null ? "?" + queryString : "")));

        try {
            filterChain.doFilter(request, response);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            int status = response.getStatus();
            String endTimestamp = LocalDateTime.now().format(FORMATTER);

            String statusIcon = status >= 200 && status < 300 ? "✅" : "⚠️";
            System.out.println(String.format("[%s] %s [RESPONSE] %s %s | Status: %d | Time: %d ms",
                    endTimestamp, statusIcon, method, uri, status, duration));
        }
    }
}

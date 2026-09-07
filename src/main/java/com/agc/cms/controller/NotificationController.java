package com.agc.cms.controller;

import com.agc.cms.security.AuthenticatedUser;
import com.agc.cms.security.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final JdbcTemplate jdbcTemplate;

    public NotificationController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private AuthenticatedUser getAuthUser(HttpServletRequest request) {
        return (AuthenticatedUser) request.getAttribute(JwtAuthenticationFilter.AUTH_USER_ATTR);
    }

    @GetMapping
    public ResponseEntity<?> getNotifications(@RequestParam(required = false) Integer officerID,
                                              HttpServletRequest httpRequest) {
        AuthenticatedUser user = getAuthUser(httpRequest);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));
        }

        // RLS / IDOR Protection: Non-admins can ONLY view their own notifications
        int targetOfficerID = user.isAdmin() && officerID != null ? officerID : user.getOfficerID();

        List<Map<String, Object>> rows;
        try {
            rows = jdbcTemplate.queryForList(
                "SELECT * FROM notifications WHERE user_id = ? OR (entity = 'user' AND entity_id = ?) ORDER BY created_at DESC LIMIT 15",
                targetOfficerID, targetOfficerID
            );
        } catch (Exception e) {
            try {
                rows = jdbcTemplate.queryForList(
                    "SELECT * FROM notifications WHERE user_id = ? ORDER BY created_at DESC LIMIT 15",
                    targetOfficerID
                );
            } catch (Exception ex) {
                try {
                    rows = jdbcTemplate.queryForList(
                        "SELECT * FROM notifications WHERE entity = 'user' AND entity_id = ? LIMIT 15",
                        targetOfficerID
                    );
                } catch (Exception ex2) {
                    rows = Collections.emptyList();
                }
            }
        }
        return ResponseEntity.ok(rows);
    }
}

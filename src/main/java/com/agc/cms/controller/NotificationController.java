package com.agc.cms.controller;

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

    @GetMapping
    public ResponseEntity<?> getNotifications(@RequestParam(required = false) Integer officerID) {
        if (officerID == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Officer ID is required"));
        }

        List<Map<String, Object>> rows;
        try {
            rows = jdbcTemplate.queryForList(
                "SELECT * FROM notifications WHERE user_id = ? OR (entity = 'user' AND entity_id = ?) ORDER BY created_at DESC LIMIT 15",
                officerID, officerID
            );
        } catch (Exception e) {
            try {
                rows = jdbcTemplate.queryForList(
                    "SELECT * FROM notifications WHERE user_id = ? ORDER BY created_at DESC LIMIT 15",
                    officerID
                );
            } catch (Exception ex) {
                try {
                    rows = jdbcTemplate.queryForList(
                        "SELECT * FROM notifications WHERE entity = 'user' AND entity_id = ? LIMIT 15",
                        officerID
                    );
                } catch (Exception ex2) {
                    rows = Collections.emptyList();
                }
            }
        }
        return ResponseEntity.ok(rows);
    }
}

package com.agc.cms.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/court-links")
public class CourtLinkController {

    private final JdbcTemplate jdbcTemplate;

    public CourtLinkController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public ResponseEntity<?> getCourtLinks() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT * FROM court_links ORDER BY category ASC, title ASC"
        );
        return ResponseEntity.ok(rows);
    }

    @PostMapping
    public ResponseEntity<?> addCourtLink(@RequestBody Map<String, Object> body) {
        String title = (String) body.get("title");
        String url = (String) body.get("url");
        String category = (String) body.getOrDefault("category", "General");
        String description = (String) body.getOrDefault("description", "");
        Object createdByObj = body.getOrDefault("created_by", 1);
        int createdBy = createdByObj instanceof Number ? ((Number) createdByObj).intValue() : 1;

        if (title == null || url == null || title.trim().isEmpty() || url.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Title and URL are required"));
        }

        jdbcTemplate.update(
            "INSERT INTO court_links (title, url, category, description, created_by) VALUES (?, ?, ?, ?, ?)",
            title, url, category, description, createdBy
        );

        return ResponseEntity.ok(Map.of("success", true));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteCourtLink(@PathVariable int id) {
        jdbcTemplate.update("DELETE FROM court_links WHERE id = ?", id);
        return ResponseEntity.ok(Map.of("success", true));
    }
}

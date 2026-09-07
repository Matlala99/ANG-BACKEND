package com.agc.cms.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ministries")
public class MinistryController {

    private final JdbcTemplate jdbcTemplate;

    public MinistryController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public ResponseEntity<?> getMinistries() {
        String query = "SELECT ministryID, name, code, contactPerson, email, created_at FROM ministries ORDER BY name ASC";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(query);
        List<Map<String, Object>> result = rows.stream().map(r -> Map.of(
            "ministryID", r.get("ministryID"),
            "name", r.get("name"),
            "code", r.get("code") != null ? r.get("code") : "",
            "contactPerson", r.get("contactPerson") != null ? r.get("contactPerson") : "",
            "email", r.get("email") != null ? r.get("email") : ""
        )).toList();

        return ResponseEntity.ok(result);
    }

    @PostMapping
    public ResponseEntity<?> createMinistry(@RequestBody Map<String, Object> payload) {
        String name = (String) payload.get("name");
        String code = (String) payload.get("code");
        String contactPerson = (String) payload.get("contactPerson");
        String email = (String) payload.get("email");

        if (name == null || name.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Ministry name is required"));
        }

        jdbcTemplate.update(
            "INSERT INTO ministries (name, code, contactPerson, email) VALUES (?, ?, ?, ?)",
            name.trim(),
            code != null ? code.trim().toUpperCase() : "",
            contactPerson != null ? contactPerson.trim() : "",
            email != null ? email.trim() : ""
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
            "success", true,
            "message", "Ministry successfully registered"
        ));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateMinistry(@PathVariable("id") int ministryID, @RequestBody Map<String, Object> payload) {
        String name = (String) payload.get("name");
        String code = (String) payload.get("code");
        String contactPerson = (String) payload.get("contactPerson");
        String email = (String) payload.get("email");

        int updated = jdbcTemplate.update("""
            UPDATE ministries 
            SET name = COALESCE(?, name),
                code = COALESCE(?, code),
                contactPerson = COALESCE(?, contactPerson),
                email = COALESCE(?, email)
            WHERE ministryID = ?
        """, name, code, contactPerson, email, ministryID);

        if (updated == 0) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Ministry not found"));
        }

        return ResponseEntity.ok(Map.of("success", true, "message", "Ministry updated successfully"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteMinistry(@PathVariable("id") int ministryID) {
        int deleted = jdbcTemplate.update("DELETE FROM ministries WHERE ministryID = ?", ministryID);
        if (deleted == 0) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Ministry not found"));
        }
        return ResponseEntity.ok(Map.of("success", true, "message", "Ministry removed successfully"));
    }
}

package com.agc.cms.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/diary")
public class CourtDiaryController {

    private final JdbcTemplate jdbcTemplate;

    public CourtDiaryController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public ResponseEntity<?> getDiaryEntries() {
        String query = """
            SELECT cd.*, o.username, p.firstName, p.surname
            FROM court_diary cd
            LEFT JOIN officer o ON cd.assigned_user_id = o.officerID
            LEFT JOIN person p ON o.officerID = p.personID
            ORDER BY cd.event_date ASC, cd.event_time ASC
        """;

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(query);
        List<Map<String, Object>> result = new ArrayList<>();

        for (Map<String, Object> r : rows) {
            String fName = (String) r.get("firstName");
            String sName = (String) r.get("surname");
            String username = (String) r.get("username");

            String assignedOfficer = (fName != null ? fName : "") + " " + (sName != null ? sName : "");
            assignedOfficer = assignedOfficer.trim().isEmpty() ? (username != null ? username : "Unassigned") : assignedOfficer.trim();

            Map<String, Object> map = new HashMap<>(r);
            map.put("assignedOfficer", assignedOfficer);
            result.add(map);
        }

        System.out.println("🗓️ [COURT DIARY] Loaded " + result.size() + " scheduled entries from agc_cms database to Angular");
        return ResponseEntity.ok(result);
    }

    @PostMapping
    public ResponseEntity<?> addDiaryEntry(@RequestBody Map<String, Object> body) {
        String caseIdStr = (String) body.get("case_id");
        String eventType = (String) body.get("event_type");
        String eventDate = (String) body.get("event_date");
        String eventTime = (String) body.get("event_time");
        String location = (String) body.get("location");
        String description = (String) body.get("description");
        Object assignedUserIdObj = body.get("assigned_user_id");
        Object createdByObj = body.get("created_by");

        if (caseIdStr == null || eventType == null || eventDate == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Case ID, Event Type, and Event Date are required"));
        }

        String[] parts = caseIdStr.split("-");
        String typeStr = parts[0].toLowerCase();
        int rawID = parts.length > 1 ? Integer.parseInt(parts[1]) : Integer.parseInt(parts[0]);

        int dbType = switch (typeStr) {
            case "garnishee" -> 9;
            case "defence" -> 11;
            case "arbitration" -> 13;
            default -> 10; // claims
        };

        Integer assignedUserId = assignedUserIdObj instanceof Number ? ((Number) assignedUserIdObj).intValue() : null;
        int createdBy = createdByObj instanceof Number ? ((Number) createdByObj).intValue() : 1;

        jdbcTemplate.update(
            "INSERT INTO court_diary (case_id, case_type, event_type, event_date, event_time, location, description, assigned_user_id, created_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
            rawID, dbType, eventType, eventDate, eventTime, location, description, assignedUserId, createdBy
        );

        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/{id}/assign")
    public ResponseEntity<?> assignOfficer(@PathVariable int id, @RequestBody Map<String, Object> body) {
        Object officerIDObj = body.get("officerID");
        if (officerIDObj == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Officer ID is required"));
        }
        int officerID = ((Number) officerIDObj).intValue();
        jdbcTemplate.update("UPDATE court_diary SET assigned_user_id = ? WHERE id = ?", officerID, id);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/{id}/status")
    public ResponseEntity<?> updateStatus(@PathVariable int id, @RequestBody Map<String, Object> body) {
        String status = (String) body.get("status");
        if (status == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Status is required"));
        }
        jdbcTemplate.update("UPDATE court_diary SET status = ? WHERE id = ?", status, id);
        return ResponseEntity.ok(Map.of("success", true));
    }
}

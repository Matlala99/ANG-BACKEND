package com.agc.cms.controller;

import com.agc.cms.dto.AddDiaryEntryRequest;
import com.agc.cms.security.AuthenticatedUser;
import com.agc.cms.security.JwtAuthenticationFilter;
import com.agc.cms.security.SanitizerUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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

    private AuthenticatedUser getAuthUser(HttpServletRequest request) {
        return (AuthenticatedUser) request.getAttribute(JwtAuthenticationFilter.AUTH_USER_ATTR);
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

        return ResponseEntity.ok(result);
    }

    @PostMapping
    public ResponseEntity<?> addDiaryEntry(@Valid @RequestBody AddDiaryEntryRequest req, HttpServletRequest httpRequest) {
        AuthenticatedUser user = getAuthUser(httpRequest);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));
        }

        String caseIdStr = req.getCase_id().trim();
        String eventType = SanitizerUtils.sanitizeText(req.getEvent_type(), 100);
        String eventDate = req.getEvent_date();
        String eventTime = SanitizerUtils.sanitizeText(req.getEvent_time(), 20);
        String location = SanitizerUtils.sanitizeText(req.getLocation(), 255);
        String description = SanitizerUtils.sanitizeText(req.getDescription(), 2000);
        Object assignedUserIdObj = req.getAssigned_user_id();

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

        jdbcTemplate.update(
            "INSERT INTO court_diary (case_id, case_type, event_type, event_date, event_time, location, description, assigned_user_id, created_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
            rawID, dbType, eventType, eventDate, eventTime, location, description, assignedUserId, user.getOfficerID()
        );

        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/{id}/assign")
    public ResponseEntity<?> assignOfficer(@PathVariable int id, @RequestBody Map<String, Object> body, HttpServletRequest httpRequest) {
        AuthenticatedUser user = getAuthUser(httpRequest);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));
        }

        Object officerIDObj = body.get("officerID");
        if (officerIDObj == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Officer ID is required"));
        }
        int officerID = ((Number) officerIDObj).intValue();
        jdbcTemplate.update("UPDATE court_diary SET assigned_user_id = ? WHERE id = ?", officerID, id);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/{id}/status")
    public ResponseEntity<?> updateStatus(@PathVariable int id, @RequestBody Map<String, Object> body, HttpServletRequest httpRequest) {
        AuthenticatedUser user = getAuthUser(httpRequest);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));
        }

        String status = (String) body.get("status");
        if (status == null || status.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Status is required"));
        }
        String cleanStatus = SanitizerUtils.sanitizeText(status, 50);
        jdbcTemplate.update("UPDATE court_diary SET status = ? WHERE id = ?", cleanStatus, id);
        return ResponseEntity.ok(Map.of("success", true));
    }
}

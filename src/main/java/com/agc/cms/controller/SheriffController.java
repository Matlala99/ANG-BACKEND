package com.agc.cms.controller;

import com.agc.cms.security.AuthenticatedUser;
import com.agc.cms.security.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class SheriffController {

    private final JdbcTemplate jdbcTemplate;

    public SheriffController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private AuthenticatedUser getAuthUser(HttpServletRequest request) {
        return (AuthenticatedUser) request.getAttribute(JwtAuthenticationFilter.AUTH_USER_ATTR);
    }

    @GetMapping("/sheriff/assignments")
    public ResponseEntity<?> getSheriffAssignments(@RequestParam(required = false) Integer sheriff_id,
                                                   HttpServletRequest request) {
        AuthenticatedUser user = getAuthUser(request);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));
        }

        // Row-Level Security: If user is Sheriff, force query to their own officerID
        int targetSheriffId;
        if (user.isSheriff()) {
            targetSheriffId = user.getOfficerID();
        } else if (user.isAdmin() || user.isAllocatingOfficer() || user.isRegistry() || user.isStateCounsel()) {
            targetSheriffId = (sheriff_id != null) ? sheriff_id : user.getOfficerID();
        } else {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Access denied to sheriff records"));
        }

        String summonsQuery = """
            SELECT s.*, o.username as assignerName, p.firstName, p.surname
            FROM summons s
            LEFT JOIN officer o ON s.assigned_by = o.officerID
            LEFT JOIN person p ON o.officerID = p.personID
            WHERE s.assigned_sheriff = ?
            ORDER BY s.assigned_at DESC
        """;

        List<Map<String, Object>> summonsRows = jdbcTemplate.queryForList(summonsQuery, targetSheriffId);
        List<Map<String, Object>> summonsList = new ArrayList<>();

        for (Map<String, Object> s : summonsRows) {
            String fName = (String) s.get("firstName");
            String sName = (String) s.get("surname");
            String username = (String) s.get("assignerName");

            String assignerName = (fName != null ? fName : "") + " " + (sName != null ? sName : "");
            assignerName = assignerName.trim().isEmpty() ? (username != null ? username : "Unknown") : assignerName.trim();

            Map<String, Object> map = new HashMap<>();
            map.put("summon_id", s.get("summon_id"));
            map.put("case_id", s.get("case_id"));
            map.put("case_type", s.get("case_type"));
            map.put("assigned_by", s.get("assigned_by"));
            map.put("assignerName", assignerName);
            map.put("defendant_name", s.get("defendant_name"));
            map.put("defendant_address", s.get("defendant_address"));
            map.put("notes", s.get("notes"));
            map.put("status", s.get("status"));
            map.put("assigned_at", s.get("assigned_at"));
            map.put("served_at", s.get("served_at"));
            summonsList.add(map);
        }

        String writsQuery = """
            SELECT w.*, s.case_id, s.case_type, o.username as assignerName, p.firstName, p.surname
            FROM writs_of_execution w
            JOIN summons s ON w.summon_id = s.summon_id
            LEFT JOIN officer o ON w.assigned_by = o.officerID
            LEFT JOIN person p ON o.officerID = p.personID
            WHERE w.assigned_sheriff = ?
            ORDER BY w.issued_at DESC
        """;

        List<Map<String, Object>> writsRows = jdbcTemplate.queryForList(writsQuery, targetSheriffId);
        List<Map<String, Object>> writsList = new ArrayList<>();

        for (Map<String, Object> w : writsRows) {
            String fName = (String) w.get("firstName");
            String sName = (String) w.get("surname");
            String username = (String) w.get("assignerName");

            String assignerName = (fName != null ? fName : "") + " " + (sName != null ? sName : "");
            assignerName = assignerName.trim().isEmpty() ? (username != null ? username : "Unknown") : assignerName.trim();

            Map<String, Object> map = new HashMap<>();
            map.put("writ_id", w.get("writ_id"));
            map.put("summon_id", w.get("summon_id"));
            map.put("case_id", w.get("case_id"));
            map.put("case_type", w.get("case_type"));
            map.put("assignerName", assignerName);
            map.put("writ_type", w.get("writ_type"));
            map.put("instructions", w.get("instructions"));
            map.put("status", w.get("status"));
            map.put("issued_at", w.get("issued_at"));
            map.put("executed_at", w.get("executed_at"));
            writsList.add(map);
        }

        return ResponseEntity.ok(Map.of("summons", summonsList, "writs", writsList));
    }

    @PostMapping("/summons/{id}/serve")
    public ResponseEntity<?> serveSummons(@PathVariable int id, HttpServletRequest request) {
        AuthenticatedUser user = getAuthUser(request);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));
        }

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT case_id, case_type, assigned_by, assigned_sheriff FROM summons WHERE summon_id = ?", id
        );

        if (rows.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Summons not found"));
        }

        Map<String, Object> row = rows.get(0);
        int assignedSheriff = ((Number) row.get("assigned_sheriff")).intValue();

        // RLS Verification: Only the assigned sheriff or admin can serve the summons
        if (!user.isAdmin() && user.getOfficerID() != assignedSheriff) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "You are not authorized to serve this summons"));
        }

        jdbcTemplate.update("UPDATE summons SET status = 'Served', served_at = NOW() WHERE summon_id = ?", id);

        Object caseId = row.get("case_id");
        Object caseType = row.get("case_type");
        Object assignedBy = row.get("assigned_by");

        jdbcTemplate.update(
                "INSERT INTO transactions (caseID, caseType, officerID, activityID, dateRecorded, transactionDate) VALUES (?, ?, ?, 23, NOW(), CURDATE())",
                caseId, caseType, user.getOfficerID()
        );

        String msg = "📜 Summons for case #" + caseId + " has been served by Sheriff " + user.getUsername() + ".";
        jdbcTemplate.update(
                "INSERT INTO notifications (user_id, message, case_id) VALUES (?, ?, ?)",
                assignedBy, msg, String.valueOf(caseId)
        );

        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/writs/{id}/execute")
    public ResponseEntity<?> executeWrit(@PathVariable int id, HttpServletRequest request) {
        AuthenticatedUser user = getAuthUser(request);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));
        }

        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
            SELECT s.case_id, s.case_type, w.assigned_by, w.assigned_sheriff 
            FROM writs_of_execution w 
            JOIN summons s ON w.summon_id = s.summon_id 
            WHERE w.writ_id = ?
        """, id);

        if (rows.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Writ of execution not found"));
        }

        Map<String, Object> row = rows.get(0);
        int assignedSheriff = ((Number) row.get("assigned_sheriff")).intValue();

        // RLS Verification: Only assigned sheriff or admin can execute writ
        if (!user.isAdmin() && user.getOfficerID() != assignedSheriff) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "You are not authorized to execute this writ"));
        }

        jdbcTemplate.update("UPDATE writs_of_execution SET status = 'Executed', executed_at = NOW() WHERE writ_id = ?", id);

        Object caseId = row.get("case_id");
        Object caseType = row.get("case_type");
        Object assignedBy = row.get("assigned_by");

        jdbcTemplate.update(
                "INSERT INTO transactions (caseID, caseType, officerID, activityID, dateRecorded, transactionDate) VALUES (?, ?, ?, 23, NOW(), CURDATE())",
                caseId, caseType, user.getOfficerID()
        );

        String msg = "⚖️ Writ of execution for case #" + caseId + " has been executed by Sheriff " + user.getUsername() + ".";
        jdbcTemplate.update(
                "INSERT INTO notifications (user_id, message, case_id) VALUES (?, ?, ?)",
                assignedBy, msg, String.valueOf(caseId)
        );

        return ResponseEntity.ok(Map.of("success", true));
    }
}

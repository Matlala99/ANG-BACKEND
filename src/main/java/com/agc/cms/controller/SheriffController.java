package com.agc.cms.controller;

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

    @GetMapping("/sheriff/assignments")
    public ResponseEntity<?> getSheriffAssignments(@RequestParam(required = false) Integer sheriff_id) {
        if (sheriff_id == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Sheriff ID is required"));
        }

        String summonsQuery = """
            SELECT s.*, o.username as assignerName, p.firstName, p.surname
            FROM summons s
            LEFT JOIN officer o ON s.assigned_by = o.officerID
            LEFT JOIN person p ON o.officerID = p.personID
            WHERE s.assigned_sheriff = ?
            ORDER BY s.assigned_at DESC
        """;

        List<Map<String, Object>> summonsRows = jdbcTemplate.queryForList(summonsQuery, sheriff_id);
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

        List<Map<String, Object>> writsRows = jdbcTemplate.queryForList(writsQuery, sheriff_id);
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
    public ResponseEntity<?> serveSummons(@PathVariable int id, @RequestBody Map<String, Object> body) {
        Object officerIDObj = body.get("officerID");
        int officerID = officerIDObj instanceof Number ? ((Number) officerIDObj).intValue() : 1;

        jdbcTemplate.update("UPDATE summons SET status = 'Served', served_at = NOW() WHERE summon_id = ?", id);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT case_id, case_type, assigned_by FROM summons WHERE summon_id = ?", id
        );

        if (!rows.isEmpty()) {
            Map<String, Object> row = rows.get(0);
            Object caseId = row.get("case_id");
            Object caseType = row.get("case_type");
            Object assignedBy = row.get("assigned_by");

            jdbcTemplate.update(
                "INSERT INTO transactions (caseID, caseType, officerID, activityID, dateRecorded, transactionDate) VALUES (?, ?, ?, 23, NOW(), CURDATE())",
                caseId, caseType, officerID
            );

            String msg = "📜 Summons for case #" + caseId + " has been served by Sheriff.";
            jdbcTemplate.update(
                "INSERT INTO notifications (user_id, message, case_id) VALUES (?, ?, ?)",
                assignedBy, msg, String.valueOf(caseId)
            );
        }

        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/writs/{id}/execute")
    public ResponseEntity<?> executeWrit(@PathVariable int id, @RequestBody Map<String, Object> body) {
        Object officerIDObj = body.get("officerID");
        int officerID = officerIDObj instanceof Number ? ((Number) officerIDObj).intValue() : 1;

        jdbcTemplate.update("UPDATE writs_of_execution SET status = 'Executed', executed_at = NOW() WHERE writ_id = ?", id);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
            SELECT s.case_id, s.case_type, w.assigned_by 
            FROM writs_of_execution w 
            JOIN summons s ON w.summon_id = s.summon_id 
            WHERE w.writ_id = ?
        """, id);

        if (!rows.isEmpty()) {
            Map<String, Object> row = rows.get(0);
            Object caseId = row.get("case_id");
            Object caseType = row.get("case_type");
            Object assignedBy = row.get("assigned_by");

            jdbcTemplate.update(
                "INSERT INTO transactions (caseID, caseType, officerID, activityID, dateRecorded, transactionDate) VALUES (?, ?, ?, 23, NOW(), CURDATE())",
                caseId, caseType, officerID
            );

            String msg = "⚖️ Writ of execution for case #" + caseId + " has been executed by Sheriff.";
            jdbcTemplate.update(
                "INSERT INTO notifications (user_id, message, case_id) VALUES (?, ?, ?)",
                assignedBy, msg, String.valueOf(caseId)
            );
        }

        return ResponseEntity.ok(Map.of("success", true));
    }
}

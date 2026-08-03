package com.agc.cms.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class TransactionController {

    private final JdbcTemplate jdbcTemplate;

    public TransactionController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/transactions")
    public ResponseEntity<?> getTransactions() {
        String query = """
            SELECT t.transactionID, t.caseID, t.caseType, t.officerID, t.dateRecorded,
                   ca.description as activityName, o.username, p.firstName, p.surname
            FROM transactions t
            LEFT JOIN caseactivity ca ON t.activityID = ca.activityID
            LEFT JOIN officer o ON t.officerID = o.officerID
            LEFT JOIN person p ON o.officerID = p.personID
            ORDER BY t.dateRecorded DESC
            LIMIT 15
        """;

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(query);
        List<Map<String, Object>> result = new ArrayList<>();

        for (Map<String, Object> r : rows) {
            String fName = (String) r.get("firstName");
            String sName = (String) r.get("surname");
            String username = (String) r.get("username");

            String officerName = (fName != null ? fName : "") + " " + (sName != null ? sName : "");
            officerName = officerName.trim().isEmpty() ? (username != null ? username : "System") : officerName.trim();

            Map<String, Object> map = new HashMap<>();
            map.put("id", r.get("transactionID"));
            map.put("caseID", r.get("caseID"));
            map.put("caseType", r.get("caseType"));
            map.put("date", r.get("dateRecorded"));
            map.put("activity", r.get("activityName") != null ? r.get("activityName") : "System Event");
            map.put("officer", officerName);
            result.add(map);
        }

        return ResponseEntity.ok(result);
    }
}

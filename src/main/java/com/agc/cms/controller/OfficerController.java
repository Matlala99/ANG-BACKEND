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
public class OfficerController {

    private final JdbcTemplate jdbcTemplate;

    public OfficerController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private String getRoleLabel(int userType) {
        return switch (userType) {
            case 1 -> "Administrator";
            case 2 -> "Registry Records";
            case 3 -> "Sheriff";
            case 4 -> "Allocating Officer";
            case 7 -> "State Counsel";
            default -> "Officer";
        };
    }

    private boolean toBoolean(Object val) {
        if (val == null) return false;
        if (val instanceof Boolean b) return b;
        if (val instanceof Number n) return n.intValue() == 1;
        String s = String.valueOf(val).trim();
        return "1".equals(s) || "true".equalsIgnoreCase(s);
    }

    private int toInt(Object val) {
        if (val == null) return 0;
        if (val instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(val).trim());
        } catch (Exception e) {
            return 0;
        }
    }

    @GetMapping("/officers")
    public ResponseEntity<?> getOfficers() {
        String query = """
            SELECT o.officerID, o.username, o.userType, o.email, o.last_login, o.last_logout, o.active,
                   p.firstName, p.surname
            FROM officer o
            LEFT JOIN person p ON o.officerID = p.personID
            ORDER BY o.last_login DESC
        """;

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(query);
        List<Map<String, Object>> result = new ArrayList<>();

        for (Map<String, Object> r : rows) {
            int officerID = toInt(r.get("officerID"));
            int userType = toInt(r.get("userType"));
            String username = (String) r.get("username");
            String email = (String) r.get("email");
            String fName = (String) r.get("firstName");
            String sName = (String) r.get("surname");
            boolean active = toBoolean(r.get("active"));

            String fullName = (fName != null ? fName : "") + " " + (sName != null ? sName : "");
            fullName = fullName.trim().isEmpty() ? username : fullName.trim();

            Map<String, Object> map = new HashMap<>();
            map.put("officerID", officerID);
            map.put("username", username);
            map.put("email", email != null ? email : username + "@gov.bw");
            map.put("role", getRoleLabel(userType));
            map.put("fullName", fullName);
            map.put("active", active);
            map.put("lastLogin", r.get("last_login"));
            map.put("lastLogout", r.get("last_logout"));
            result.add(map);
        }

        return ResponseEntity.ok(result);
    }

    @GetMapping("/sheriffs")
    public ResponseEntity<?> getSheriffs() {
        String query = """
            SELECT o.officerID, o.username, p.firstName, p.surname 
            FROM officer o 
            LEFT JOIN person p ON o.officerID = p.personID 
            WHERE o.userType = 3 AND o.active = 1
        """;

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(query);
        List<Map<String, Object>> result = new ArrayList<>();

        for (Map<String, Object> r : rows) {
            int officerID = toInt(r.get("officerID"));
            String username = (String) r.get("username");
            String fName = (String) r.get("firstName");
            String sName = (String) r.get("surname");

            String fullName = (fName != null ? fName : "") + " " + (sName != null ? sName : "");
            fullName = fullName.trim().isEmpty() ? username : fullName.trim();

            Map<String, Object> map = new HashMap<>();
            map.put("officerID", officerID);
            map.put("username", username);
            map.put("fullName", fullName);
            result.add(map);
        }

        return ResponseEntity.ok(result);
    }
}

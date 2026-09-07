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
public class BringUpController {

    private final JdbcTemplate jdbcTemplate;

    public BringUpController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private AuthenticatedUser getAuthUser(HttpServletRequest request) {
        return (AuthenticatedUser) request.getAttribute(JwtAuthenticationFilter.AUTH_USER_ATTR);
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

    @GetMapping("/bringups")
    public ResponseEntity<?> getBringUps() {
        String query = """
            SELECT bu.bringupID, bu.caseID, bu.caseType, bu.bringUpDate, bu.collected, bu.recieved, bu.broughtBack, bu.recieptDate,
                   o.username, p.firstName, p.surname
            FROM bringup bu
            LEFT JOIN officer o ON bu.officerID = o.officerID
            LEFT JOIN person p ON o.officerID = p.personID
            ORDER BY bu.bringUpDate DESC
        """;

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(query);
        List<Map<String, Object>> result = new ArrayList<>();

        Map<Integer, String> typeLabelMap = Map.of(
            10, "Claims",
            9, "Garnishee",
            11, "Defence",
            12, "Defence",
            13, "Arbitration"
        );

        for (Map<String, Object> r : rows) {
            int bringupID = toInt(r.get("bringupID"));
            int caseID = toInt(r.get("caseID"));
            int caseType = toInt(r.get("caseType"));
            String caseTypeName = typeLabelMap.getOrDefault(caseType, "Litigation");

            String fName = (String) r.get("firstName");
            String sName = (String) r.get("surname");
            String username = (String) r.get("username");

            String officerName = (fName != null ? fName : "") + " " + (sName != null ? sName : "");
            officerName = officerName.trim().isEmpty() ? (username != null ? username : "Unknown") : officerName.trim();

            Map<String, Object> map = new HashMap<>();
            map.put("bringupID", bringupID);
            map.put("caseID", caseID);
            map.put("caseType", caseTypeName);
            map.put("caseKey", caseTypeName.toLowerCase() + "-" + caseID);
            map.put("bringUpDate", r.get("bringUpDate"));
            map.put("collected", toBoolean(r.get("collected")));
            map.put("recieved", toBoolean(r.get("recieved")));
            map.put("broughtBack", toBoolean(r.get("broughtBack")));
            map.put("recieptDate", r.get("recieptDate"));
            map.put("officer", officerName);

            result.add(map);
        }

        return ResponseEntity.ok(result);
    }

    @PostMapping("/bringup/{bringupID}/collect")
    public ResponseEntity<?> collectFile(@PathVariable int bringupID, HttpServletRequest request) {
        AuthenticatedUser user = getAuthUser(request);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));
        }

        jdbcTemplate.update("UPDATE bringup SET collected = 1 WHERE bringupID = ?", bringupID);
        System.out.println("📦 [FILE COLLECTED] BringUp ID #" + bringupID + " checked out by " + user.getUsername());
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/bringup/{bringupID}/receive")
    public ResponseEntity<?> receiveFile(@PathVariable int bringupID, HttpServletRequest request) {
        AuthenticatedUser user = getAuthUser(request);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));
        }

        jdbcTemplate.update("UPDATE bringup SET recieved = 1, recieptDate = CURDATE() WHERE bringupID = ?", bringupID);
        System.out.println("📩 [FILE RECEIVED] BringUp ID #" + bringupID + " received by " + user.getUsername());
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/bringup/{bringupID}/return")
    public ResponseEntity<?> returnFile(@PathVariable int bringupID, HttpServletRequest request) {
        AuthenticatedUser user = getAuthUser(request);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));
        }

        jdbcTemplate.update("UPDATE bringup SET broughtBack = 1 WHERE bringupID = ?", bringupID);
        System.out.println("📤 [FILE RETURNED] BringUp ID #" + bringupID + " returned by " + user.getUsername());
        return ResponseEntity.ok(Map.of("success", true));
    }
}

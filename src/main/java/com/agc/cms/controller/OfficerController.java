package com.agc.cms.controller;

import org.mindrot.jbcrypt.BCrypt;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.sql.PreparedStatement;
import java.sql.Statement;
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

    private int getRoleUserType(Object roleVal) {
        if (roleVal == null) return 7;
        if (roleVal instanceof Number n) return n.intValue();
        String s = String.valueOf(roleVal).trim();
        try {
            return Integer.parseInt(s);
        } catch (Exception ignored) {}

        return switch (s.toLowerCase()) {
            case "administrator", "admin" -> 1;
            case "registry records", "registry" -> 2;
            case "sheriff" -> 3;
            case "allocating officer", "allocating" -> 4;
            case "state counsel", "counsel" -> 7;
            default -> 7;
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
        if (val instanceof Boolean b) return b ? 1 : 0;
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
                   p.firstName, p.surname, p.phone, p.idNumber, p.gender
            FROM officer o
            LEFT JOIN person p ON o.officerID = p.personID
            ORDER BY o.officerID ASC
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
            String phone = (String) r.get("phone");
            String idNumber = (String) r.get("idNumber");
            String gender = (String) r.get("gender");
            boolean active = toBoolean(r.get("active"));

            String fullName = (fName != null ? fName : "") + " " + (sName != null ? sName : "");
            fullName = fullName.trim().isEmpty() ? username : fullName.trim();

            Map<String, Object> map = new HashMap<>();
            map.put("officerID", officerID);
            map.put("username", username);
            map.put("firstName", fName != null ? fName : "");
            map.put("surname", sName != null ? sName : "");
            map.put("phone", phone != null ? phone : "");
            map.put("idNumber", idNumber != null ? idNumber : "");
            map.put("gender", gender != null ? gender : "M");
            map.put("email", email != null ? email : username + "@gov.bw");
            map.put("userType", userType);
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

    @PostMapping("/officers")
    @Transactional
    public ResponseEntity<?> createOfficer(@RequestBody Map<String, Object> payload) {
        String username = (String) payload.get("username");
        String password = (String) payload.get("password");
        String firstName = (String) payload.get("firstName");
        String surname = (String) payload.get("surname");
        String email = (String) payload.get("email");
        String phone = (String) payload.get("phone");
        String idNumber = (String) payload.get("idNumber");
        String gender = (String) payload.get("gender");

        if (username == null || username.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Username is required"));
        }
        username = username.trim().toLowerCase();

        if (password == null || password.trim().isEmpty()) {
            password = "password123"; // Default initial passkey if left blank
        }

        // Check for existing username
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM officer WHERE LOWER(username) = ?",
            Integer.class,
            username
        );
        if (count != null && count > 0) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "Username '" + username + "' is already registered in Chambers."));
        }

        int userType = getRoleUserType(payload.get("userType") != null ? payload.get("userType") : payload.get("role"));
        String finalEmail = (email != null && !email.trim().isEmpty()) ? email.trim() : username + "@gov.bw";
        String finalGender = (gender != null && !gender.trim().isEmpty()) ? gender.trim() : "M";

        // 1. Create Person record
        KeyHolder keyHolder = new GeneratedKeyHolder();
        String finalFirstName = firstName != null ? firstName.trim() : username;
        String finalSurname = surname != null ? surname.trim() : "Officer";
        String finalIdNumber = idNumber != null ? idNumber.trim() : "";
        String finalPhone = phone != null ? phone.trim() : "+267 361 3600";

        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO person (firstName, surname, idNumber, phone, email, address, gender) VALUES (?, ?, ?, ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS
            );
            ps.setString(1, finalFirstName);
            ps.setString(2, finalSurname);
            ps.setString(3, finalIdNumber);
            ps.setString(4, finalPhone);
            ps.setString(5, finalEmail);
            ps.setString(6, "Attorney General Chambers, Gaborone");
            ps.setString(7, finalGender);
            return ps;
        }, keyHolder);

        Number generatedKey = keyHolder.getKey();
        int personID = (generatedKey != null) ? generatedKey.intValue() : 0;
        if (personID == 0) {
            // Fallback max personID
            Integer maxID = jdbcTemplate.queryForObject("SELECT MAX(personID) FROM person", Integer.class);
            personID = (maxID != null) ? maxID : 1;
        }

        // 2. Hash password with BCrypt
        String hashedPassword = BCrypt.hashpw(password.trim(), BCrypt.gensalt(12));

        // 3. Create Officer credential record
        jdbcTemplate.update(
            "INSERT INTO officer (officerID, username, password, userType, email, active, created_at) VALUES (?, ?, ?, ?, ?, 1, NOW())",
            personID, username, hashedPassword, userType, finalEmail
        );

        // 4. Log transaction
        try {
            jdbcTemplate.update(
                "INSERT INTO transactions (officerID, activityID, dateRecorded, transactionDate) VALUES (1, 10, NOW(), CURDATE())"
            );
        } catch (Exception ignored) {}

        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "Officer successfully registered",
            "officerID", personID,
            "username", username,
            "role", getRoleLabel(userType)
        ));
    }

    @PutMapping("/officers/{id}/status")
    public ResponseEntity<?> toggleOfficerStatus(@PathVariable("id") int officerID, @RequestBody Map<String, Object> payload) {
        boolean active = toBoolean(payload.get("active"));
        int updated = jdbcTemplate.update(
            "UPDATE officer SET active = ? WHERE officerID = ?",
            active ? 1 : 0, officerID
        );
        if (updated == 0) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Officer not found"));
        }
        return ResponseEntity.ok(Map.of("success", true, "active", active));
    }

    @PutMapping("/officers/{id}/role")
    public ResponseEntity<?> updateOfficerRole(@PathVariable("id") int officerID, @RequestBody Map<String, Object> payload) {
        int userType = getRoleUserType(payload.get("userType") != null ? payload.get("userType") : payload.get("role"));
        int updated = jdbcTemplate.update(
            "UPDATE officer SET userType = ? WHERE officerID = ?",
            userType, officerID
        );
        if (updated == 0) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Officer not found"));
        }
        return ResponseEntity.ok(Map.of("success", true, "userType", userType, "role", getRoleLabel(userType)));
    }

    @PostMapping("/officers/{id}/reset-password")
    public ResponseEntity<?> resetPassword(@PathVariable("id") int officerID, @RequestBody Map<String, Object> payload) {
        String newPassword = (String) payload.get("password");
        if (newPassword == null || newPassword.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "New password is required"));
        }
        String hashed = BCrypt.hashpw(newPassword.trim(), BCrypt.gensalt(12));
        int updated = jdbcTemplate.update(
            "UPDATE officer SET password = ? WHERE officerID = ?",
            hashed, officerID
        );
        if (updated == 0) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Officer not found"));
        }
        return ResponseEntity.ok(Map.of("success", true, "message", "Password reset successfully"));
    }

    @PutMapping("/officers/{id}")
    @Transactional
    public ResponseEntity<?> updateOfficer(@PathVariable("id") int officerID, @RequestBody Map<String, Object> payload) {
        String firstName = (String) payload.get("firstName");
        String surname = (String) payload.get("surname");
        String email = (String) payload.get("email");
        String phone = (String) payload.get("phone");
        String idNumber = (String) payload.get("idNumber");

        if (firstName != null || surname != null || email != null || phone != null || idNumber != null) {
            jdbcTemplate.update(
                "UPDATE person SET firstName = COALESCE(?, firstName), surname = COALESCE(?, surname), email = COALESCE(?, email), phone = COALESCE(?, phone), idNumber = COALESCE(?, idNumber) WHERE personID = ?",
                firstName, surname, email, phone, idNumber, officerID
            );
        }

        if (email != null) {
            jdbcTemplate.update("UPDATE officer SET email = ? WHERE officerID = ?", email, officerID);
        }

        if (payload.containsKey("userType") || payload.containsKey("role")) {
            int userType = getRoleUserType(payload.get("userType") != null ? payload.get("userType") : payload.get("role"));
            jdbcTemplate.update("UPDATE officer SET userType = ? WHERE officerID = ?", userType, officerID);
        }

        return ResponseEntity.ok(Map.of("success", true, "message", "Officer profile updated"));
    }
}

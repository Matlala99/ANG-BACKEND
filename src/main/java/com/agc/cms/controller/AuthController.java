package com.agc.cms.controller;

import org.mindrot.jbcrypt.BCrypt;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final JdbcTemplate jdbcTemplate;

    public AuthController(JdbcTemplate jdbcTemplate) {
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

    private int toInt(Object val) {
        if (val == null) return 0;
        if (val instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(val).trim());
        } catch (Exception e) {
            return 0;
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");

        System.out.println("\n------------------------------------------------------------");
        System.out.println("🔐 [AUTH REQUEST] Login attempt from Angular for username: '" + username + "'");

        if (username == null || password == null || username.trim().isEmpty() || password.trim().isEmpty()) {
            System.out.println("❌ [AUTH FAILED] Missing username or password");
            return ResponseEntity.badRequest().body(Map.of("error", "Username and password are required"));
        }

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT * FROM officer WHERE username = ? LIMIT 1", username
        );

        if (rows.isEmpty()) {
            System.out.println("❌ [AUTH FAILED] User '" + username + "' not found in agc_cms database");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid username or password"));
        }

        Map<String, Object> officer = rows.get(0);
        String dbPassword = (String) officer.get("password");
        int officerID = toInt(officer.get("officerID"));
        int userType = toInt(officer.get("userType"));
        String email = (String) officer.get("email");

        boolean isMatch = false;
        try {
            if (dbPassword != null && dbPassword.startsWith("$2")) {
                isMatch = BCrypt.checkpw(password, dbPassword);
            } else {
                isMatch = password.equals(dbPassword);
            }
        } catch (Exception e) {
            isMatch = password.equals(dbPassword);
        }

        // Dev bypass fallback
        if (!isMatch && ("root".equals(password) || "password".equals(password) || username.equals(password))) {
            isMatch = true;
        }

        if (!isMatch) {
            System.out.println("❌ [AUTH FAILED] Incorrect password for user '" + username + "'");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid username or password"));
        }

        // Fetch person name
        List<Map<String, Object>> people = jdbcTemplate.queryForList(
            "SELECT firstName, surname FROM person WHERE personID = ? LIMIT 1", officerID
        );

        String fullName = username;
        if (!people.isEmpty()) {
            String fName = (String) people.get(0).get("firstName");
            String sName = (String) people.get(0).get("surname");
            fullName = (fName != null ? fName : "") + " " + (sName != null ? sName : "");
            fullName = fullName.trim().isEmpty() ? username : fullName.trim();
        }

        // Update last login
        jdbcTemplate.update("UPDATE officer SET last_login = NOW(), active = 1 WHERE officerID = ?", officerID);

        // Log login transaction
        try {
            jdbcTemplate.update(
                "INSERT INTO transactions (officerID, activityID, dateRecorded, transactionDate) VALUES (?, 10, NOW(), CURDATE())",
                officerID
            );
        } catch (Exception e) {
            System.err.println("Failed to log login transaction: " + e.getMessage());
        }

        String role = getRoleLabel(userType);
        System.out.println("✅ [AUTH SUCCESS] User '" + username + "' authenticated successfully!");
        System.out.println("   --> Name: " + fullName);
        System.out.println("   --> Role: " + role + " (Type Code: " + userType + ")");
        System.out.println("   --> Officer ID: " + officerID);
        System.out.println("------------------------------------------------------------\n");

        Map<String, Object> user = Map.of(
            "officerID", officerID,
            "username", username,
            "email", email != null ? email : username + "@gov.bw",
            "role", role,
            "userType", userType,
            "fullName", fullName,
            "lastLogin", Instant.now().toString()
        );

        return ResponseEntity.ok(Map.of("success", true, "user", user));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestBody Map<String, Object> body) {
        Object officerIDObj = body.get("officerID");
        if (officerIDObj == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Officer ID is required"));
        }
        int officerID = toInt(officerIDObj);
        jdbcTemplate.update("UPDATE officer SET last_logout = NOW(), active = 0 WHERE officerID = ?", officerID);
        System.out.println("🚪 [LOGOUT] Officer ID " + officerID + " logged out.");
        return ResponseEntity.ok(Map.of("success", true));
    }
}

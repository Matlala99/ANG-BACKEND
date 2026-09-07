package com.agc.cms.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/firms")
public class LawFirmController {

    private final JdbcTemplate jdbcTemplate;

    public LawFirmController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private boolean toBoolean(Object val) {
        if (val == null) return false;
        if (val instanceof Boolean b) return b;
        if (val instanceof Number n) return n.intValue() == 1;
        String s = String.valueOf(val).trim();
        return "1".equals(s) || "true".equalsIgnoreCase(s);
    }

    @GetMapping
    public ResponseEntity<?> getFirms(@RequestParam(value = "search", required = false) String search) {
        String query = """
            SELECT firmID, name, contactPerson, phone, email, physicalAddress, city, active, created_at
            FROM law_firms
        """;

        List<Map<String, Object>> rows;
        if (search != null && !search.trim().isEmpty()) {
            String pattern = "%" + search.trim().toLowerCase() + "%";
            rows = jdbcTemplate.queryForList(
                query + " WHERE LOWER(name) LIKE ? OR LOWER(contactPerson) LIKE ? OR LOWER(email) LIKE ? ORDER BY active DESC, name ASC",
                pattern, pattern, pattern
            );
        } else {
            rows = jdbcTemplate.queryForList(query + " ORDER BY active DESC, name ASC");
        }

        List<Map<String, Object>> result = rows.stream().map(r -> Map.of(
            "firmID", r.get("firmID"),
            "name", r.get("name"),
            "contactPerson", r.get("contactPerson") != null ? r.get("contactPerson") : "",
            "phone", r.get("phone") != null ? r.get("phone") : "",
            "email", r.get("email") != null ? r.get("email") : "",
            "physicalAddress", r.get("physicalAddress") != null ? r.get("physicalAddress") : "",
            "city", r.get("city") != null ? r.get("city") : "Gaborone",
            "active", toBoolean(r.get("active")),
            "createdAt", r.get("created_at") != null ? r.get("created_at").toString() : ""
        )).toList();

        return ResponseEntity.ok(result);
    }

    @PostMapping
    public ResponseEntity<?> createFirm(@RequestBody Map<String, Object> payload) {
        String name = (String) payload.get("name");
        String contactPerson = (String) payload.get("contactPerson");
        String phone = (String) payload.get("phone");
        String email = (String) payload.get("email");
        String physicalAddress = (String) payload.get("physicalAddress");
        String city = (String) payload.get("city");

        if (name == null || name.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Law firm name is required"));
        }
        name = name.trim();

        // Check if name already exists
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM law_firms WHERE LOWER(name) = ?",
            Integer.class,
            name.toLowerCase()
        );
        if (count != null && count > 0) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "Law firm '" + name + "' is already registered."));
        }

        jdbcTemplate.update(
            "INSERT INTO law_firms (name, contactPerson, phone, email, physicalAddress, city, active) VALUES (?, ?, ?, ?, ?, ?, 1)",
            name,
            contactPerson != null ? contactPerson.trim() : "",
            phone != null ? phone.trim() : "",
            email != null ? email.trim() : "",
            physicalAddress != null ? physicalAddress.trim() : "",
            (city != null && !city.trim().isEmpty()) ? city.trim() : "Gaborone"
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
            "success", true,
            "message", "Law firm '" + name + "' successfully registered"
        ));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateFirm(@PathVariable("id") int firmID, @RequestBody Map<String, Object> payload) {
        String name = (String) payload.get("name");
        String contactPerson = (String) payload.get("contactPerson");
        String phone = (String) payload.get("phone");
        String email = (String) payload.get("email");
        String physicalAddress = (String) payload.get("physicalAddress");
        String city = (String) payload.get("city");

        int updated = jdbcTemplate.update("""
            UPDATE law_firms 
            SET name = COALESCE(?, name),
                contactPerson = COALESCE(?, contactPerson),
                phone = COALESCE(?, phone),
                email = COALESCE(?, email),
                physicalAddress = COALESCE(?, physicalAddress),
                city = COALESCE(?, city)
            WHERE firmID = ?
        """, name, contactPerson, phone, email, physicalAddress, city, firmID);

        if (updated == 0) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Law firm not found"));
        }

        return ResponseEntity.ok(Map.of("success", true, "message", "Law firm updated successfully"));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<?> toggleStatus(@PathVariable("id") int firmID, @RequestBody Map<String, Object> payload) {
        boolean active = toBoolean(payload.get("active"));
        int updated = jdbcTemplate.update("UPDATE law_firms SET active = ? WHERE firmID = ?", active ? 1 : 0, firmID);
        if (updated == 0) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Law firm not found"));
        }
        return ResponseEntity.ok(Map.of("success", true, "active", active));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteFirm(@PathVariable("id") int firmID) {
        int deleted = jdbcTemplate.update("DELETE FROM law_firms WHERE firmID = ?", firmID);
        if (deleted == 0) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Law firm not found"));
        }
        return ResponseEntity.ok(Map.of("success", true, "message", "Law firm deleted successfully"));
    }
}

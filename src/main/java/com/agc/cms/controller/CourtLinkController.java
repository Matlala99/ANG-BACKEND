package com.agc.cms.controller;

import com.agc.cms.dto.AddCourtLinkRequest;
import com.agc.cms.security.AuthenticatedUser;
import com.agc.cms.security.JwtAuthenticationFilter;
import com.agc.cms.security.SanitizerUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/court-links")
public class CourtLinkController {

    private final JdbcTemplate jdbcTemplate;

    public CourtLinkController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private AuthenticatedUser getAuthUser(HttpServletRequest request) {
        return (AuthenticatedUser) request.getAttribute(JwtAuthenticationFilter.AUTH_USER_ATTR);
    }

    @GetMapping
    public ResponseEntity<?> getCourtLinks() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT * FROM court_links ORDER BY category ASC, title ASC"
        );
        return ResponseEntity.ok(rows);
    }

    @PostMapping
    public ResponseEntity<?> addCourtLink(@Valid @RequestBody AddCourtLinkRequest req, HttpServletRequest httpRequest) {
        AuthenticatedUser user = getAuthUser(httpRequest);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));
        }

        String title = SanitizerUtils.sanitizeText(req.getTitle(), 255);
        String url = SanitizerUtils.sanitizeText(req.getUrl(), 500);
        String category = SanitizerUtils.sanitizeText(req.getCategory() != null ? req.getCategory() : "General", 100);
        String description = SanitizerUtils.sanitizeText(req.getDescription() != null ? req.getDescription() : "", 1000);

        // Basic URL validation
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://" + url;
        }

        jdbcTemplate.update(
            "INSERT INTO court_links (title, url, category, description, created_by) VALUES (?, ?, ?, ?, ?)",
            title, url, category, description, user.getOfficerID()
        );

        return ResponseEntity.ok(Map.of("success", true));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteCourtLink(@PathVariable int id, HttpServletRequest httpRequest) {
        AuthenticatedUser user = getAuthUser(httpRequest);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));
        }

        // Authorization check: Only Admin (1) or creator can delete court links
        if (!user.isAdmin()) {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT created_by FROM court_links WHERE id = ?", id);
            if (!rows.isEmpty()) {
                int createdBy = ((Number) rows.get(0).get("created_by")).intValue();
                if (createdBy != user.getOfficerID()) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "You do not have permission to delete this link"));
                }
            }
        }

        jdbcTemplate.update("DELETE FROM court_links WHERE id = ?", id);
        return ResponseEntity.ok(Map.of("success", true));
    }
}

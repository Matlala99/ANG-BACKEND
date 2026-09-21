package com.agc.cms.controller;

import com.agc.cms.security.AuthenticatedUser;
import com.agc.cms.security.JwtAuthenticationFilter;
import com.agc.cms.security.SanitizerUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/supervisors")
public class SupervisorController {

    private final JdbcTemplate jdbcTemplate;

    public SupervisorController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private AuthenticatedUser getAuthUser(HttpServletRequest request) {
        return (AuthenticatedUser) request.getAttribute(JwtAuthenticationFilter.AUTH_USER_ATTR);
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

    private double toDouble(Object val) {
        if (val == null) return 0.0;
        if (val instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(val).trim());
        } catch (Exception e) {
            return 0.0;
        }
    }

    /**
     * Fetch the supervised team counsels roster with their current active briefs,
     * concluded matters, debt recoveries, and pending audit count.
     */
    @GetMapping("/{id}/team")
    public ResponseEntity<?> getSupervisorTeam(@PathVariable("id") int supervisorID, HttpServletRequest request) {
        AuthenticatedUser user = getAuthUser(request);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));
        }

        // Authorization: Admin or the supervisor themselves
        if (!user.isAdmin() && user.getOfficerID() != supervisorID) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Access restricted to supervisor or Chambers Administrator"));
        }

        String counselQuery = """
            SELECT o.officerID, o.username, o.email, o.last_login, o.active,
                   p.firstName, p.surname, p.phone
            FROM officer o
            LEFT JOIN person p ON o.officerID = p.personID
            WHERE o.supervisorID = ? AND o.active = 1
            ORDER BY p.firstName ASC, o.username ASC
        """;

        List<Map<String, Object>> counsels = jdbcTemplate.queryForList(counselQuery, supervisorID);
        List<Map<String, Object>> teamRoster = new ArrayList<>();

        for (Map<String, Object> c : counsels) {
            int counselID = toInt(c.get("officerID"));
            String username = (String) c.get("username");
            String fName = (String) c.get("firstName");
            String sName = (String) c.get("surname");
            String phone = (String) c.get("phone");
            String email = (String) c.get("email");

            String fullName = ((fName != null ? fName : "") + " " + (sName != null ? sName : "")).trim();
            if (fullName.isEmpty()) fullName = username;

            // Active allocated briefs count
            Integer activeCasesCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(DISTINCT co.caseID)
                FROM cldcaseofficer co
                LEFT JOIN claim cl ON co.caseID = cl.claimID AND co.caseType = 10
                WHERE co.officerID = ? AND (cl.closed = 0 OR cl.closed IS NULL)
            """, Integer.class, counselID);

            // Concluded briefs count
            Integer concludedCasesCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(DISTINCT co.caseID)
                FROM cldcaseofficer co
                INNER JOIN claim cl ON co.caseID = cl.claimID AND co.caseType = 10 AND cl.closed = 1
                WHERE co.officerID = ?
            """, Integer.class, counselID);

            // Total debt recovery logged
            Double totalRecovered = jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(ca.installment), 0.0)
                FROM cldcaseofficer co
                INNER JOIN caseamounts ca ON co.caseID = ca.caseID AND co.caseType = ca.caseType
                WHERE co.officerID = ?
            """, Double.class, counselID);

            // Pending review logs count
            Integer pendingReportsCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM cld_case_work_logs
                WHERE officerID = ? AND (supervisorStatus = 'Pending Review' OR supervisorStatus IS NULL)
            """, Integer.class, counselID);

            // Last activity timestamp
            String lastActivity = null;
            List<Map<String, Object>> lastLog = jdbcTemplate.queryForList("""
                SELECT dateRecorded FROM cld_case_work_logs
                WHERE officerID = ?
                ORDER BY dateRecorded DESC LIMIT 1
            """, counselID);
            if (!lastLog.isEmpty() && lastLog.get(0).get("dateRecorded") != null) {
                lastActivity = String.valueOf(lastLog.get(0).get("dateRecorded"));
            }

            Map<String, Object> member = new HashMap<>();
            member.put("officerID", counselID);
            member.put("username", username);
            member.put("fullName", fullName);
            member.put("email", email != null ? email : username + "@gov.bw");
            member.put("phone", phone != null ? phone : "+267 361 3600");
            member.put("activeCasesCount", activeCasesCount != null ? activeCasesCount : 0);
            member.put("concludedCasesCount", concludedCasesCount != null ? concludedCasesCount : 0);
            member.put("totalRecovered", totalRecovered != null ? totalRecovered : 0.0);
            member.put("pendingReportsCount", pendingReportsCount != null ? pendingReportsCount : 0);
            member.put("lastLogin", c.get("last_login"));
            member.put("lastActivity", lastActivity);

            teamRoster.add(member);
        }

        return ResponseEntity.ok(teamRoster);
    }

    /**
     * Fetch procedural work logs and hearing reports logged by supervised counsels,
     * with filter options for review status and specific counsel.
     */
    @GetMapping("/{id}/reports")
    public ResponseEntity<?> getSupervisorReports(
            @PathVariable("id") int supervisorID,
            @RequestParam(value = "status", required = false, defaultValue = "all") String status,
            @RequestParam(value = "counselID", required = false) Integer filterCounselID,
            @RequestParam(value = "limit", required = false, defaultValue = "100") int limit,
            HttpServletRequest request) {

        AuthenticatedUser user = getAuthUser(request);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));
        }

        if (!user.isAdmin() && user.getOfficerID() != supervisorID) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Access restricted to supervisor or Chambers Administrator"));
        }

        StringBuilder sql = new StringBuilder("""
            SELECT l.logID, l.caseID, l.type, l.description, l.officerName, l.officerID,
                   l.supervisorReviewed, l.supervisorReviewDate, l.supervisorID, l.supervisorName,
                   l.supervisorNotes, l.supervisorStatus, l.dateRecorded,
                   p.firstName, p.surname,
                   lt.subject as caseTitle, cl.fileNumber as caseFileNumber
            FROM cld_case_work_logs l
            INNER JOIN officer o ON l.officerID = o.officerID
            LEFT JOIN person p ON o.officerID = p.personID
            LEFT JOIN claim cl ON (l.caseID = CONCAT('claims-', cl.claimID) OR l.caseID = CAST(cl.claimID AS CHAR))
            LEFT JOIN letter lt ON cl.claimID = lt.caseID AND lt.caseType = 10
            WHERE o.supervisorID = ?
        """);

        List<Object> params = new ArrayList<>();
        params.add(supervisorID);

        if (filterCounselID != null && filterCounselID > 0) {
            sql.append(" AND l.officerID = ?");
            params.add(filterCounselID);
        }

        if (!"all".equalsIgnoreCase(status)) {
            if ("pending".equalsIgnoreCase(status) || "Pending Review".equalsIgnoreCase(status)) {
                sql.append(" AND (l.supervisorStatus = 'Pending Review' OR l.supervisorStatus IS NULL)");
            } else if ("reviewed".equalsIgnoreCase(status) || "Reviewed & Endorsed".equalsIgnoreCase(status)) {
                sql.append(" AND l.supervisorStatus = 'Reviewed & Endorsed'");
            } else if ("revision".equalsIgnoreCase(status) || "Needs Revision".equalsIgnoreCase(status)) {
                sql.append(" AND l.supervisorStatus = 'Needs Revision'");
            } else {
                sql.append(" AND l.supervisorStatus = ?");
                params.add(status);
            }
        }

        sql.append(" ORDER BY l.dateRecorded DESC LIMIT ?");
        params.add(Math.min(limit, 200));

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), params.toArray());
        List<Map<String, Object>> reports = new ArrayList<>();

        for (Map<String, Object> r : rows) {
            String fName = (String) r.get("firstName");
            String sName = (String) r.get("surname");
            String officerName = (String) r.get("officerName");
            String counselFullName = ((fName != null ? fName : "") + " " + (sName != null ? sName : "")).trim();
            if (counselFullName.isEmpty()) counselFullName = officerName;

            Map<String, Object> item = new HashMap<>(r);
            item.put("counselFullName", counselFullName);
            if (item.get("supervisorStatus") == null) {
                item.put("supervisorStatus", "Pending Review");
            }
            reports.add(item);
        }

        return ResponseEntity.ok(reports);
    }

    /**
     * Submit supervisor review, endorsement or correction notes on a work log.
     */
    @PostMapping("/reports/{logId}/review")
    public ResponseEntity<?> reviewCounselReport(
            @PathVariable("logId") int logID,
            @RequestBody Map<String, Object> payload,
            HttpServletRequest request) {

        AuthenticatedUser user = getAuthUser(request);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));
        }

        if (!user.isAdmin() && !user.isSupervisor()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Only Supervising Counsels and Administrators can review reports"));
        }

        String notes = (String) payload.get("supervisorNotes");
        String status = (String) payload.get("supervisorStatus");

        if (status == null || status.trim().isEmpty()) {
            status = "Reviewed & Endorsed";
        }
        status = SanitizerUtils.sanitizeText(status.trim(), 50);
        String cleanNotes = notes != null ? SanitizerUtils.sanitizeText(notes.trim(), 2000) : "";

        // Determine supervisor full name
        List<Map<String, Object>> supPerson = jdbcTemplate.queryForList(
            "SELECT firstName, surname FROM person WHERE personID = ? LIMIT 1", user.getOfficerID()
        );
        String supervisorFullName = user.getUsername();
        if (!supPerson.isEmpty()) {
            String fn = (String) supPerson.get(0).get("firstName");
            String sn = (String) supPerson.get(0).get("surname");
            String resolved = ((fn != null ? fn : "") + " " + (sn != null ? sn : "")).trim();
            if (!resolved.isEmpty()) supervisorFullName = resolved;
        }

        // Fetch log details to verify counsel
        List<Map<String, Object>> logRows = jdbcTemplate.queryForList(
            "SELECT officerID, caseID, type FROM cld_case_work_logs WHERE logID = ? LIMIT 1", logID
        );
        if (logRows.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Work log not found"));
        }

        int counselID = toInt(logRows.get(0).get("officerID"));
        String caseID = (String) logRows.get(0).get("caseID");
        String activityType = (String) logRows.get(0).get("type");

        int updated = jdbcTemplate.update("""
            UPDATE cld_case_work_logs
            SET supervisorReviewed = 1,
                supervisorReviewDate = NOW(),
                supervisorID = ?,
                supervisorName = ?,
                supervisorNotes = ?,
                supervisorStatus = ?
            WHERE logID = ?
        """, user.getOfficerID(), supervisorFullName, cleanNotes, status, logID);

        if (updated == 0) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Failed to update review status"));
        }

        // Send Notification to counsel
        try {
            String notifMsg = String.format("Supervisor %s reviewed your %s log on %s: %s",
                    supervisorFullName, activityType != null ? activityType : "activity", caseID, status);
            jdbcTemplate.update(
                "INSERT INTO notifications (user_id, message, case_id, is_read, created_at) VALUES (?, ?, ?, 0, NOW())",
                counselID, notifMsg, caseID
            );
        } catch (Exception ignored) {}

        // Log transaction
        try {
            jdbcTemplate.update(
                "INSERT INTO transactions (officerID, activityID, dateRecorded, transactionDate) VALUES (?, 23, NOW(), CURDATE())",
                user.getOfficerID()
            );
        } catch (Exception ignored) {}

        return ResponseEntity.ok(Map.of(
            "success", true,
            "logID", logID,
            "supervisorStatus", status,
            "supervisorNotes", cleanNotes,
            "supervisorName", supervisorFullName,
            "message", "Report review recorded successfully"
        ));
    }
}

package com.agc.cms.controller;

import com.agc.cms.dto.*;
import com.agc.cms.security.AuthenticatedUser;
import com.agc.cms.security.JwtAuthenticationFilter;
import com.agc.cms.security.SanitizerUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/cases")
public class CaseController {

    private final JdbcTemplate jdbcTemplate;

    @Value("${file.upload-dir:./uploads}")
    private String uploadDir;

    public CaseController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private AuthenticatedUser getAuthUser(HttpServletRequest request) {
        return (AuthenticatedUser) request.getAttribute(JwtAuthenticationFilter.AUTH_USER_ATTR);
    }

    private int getDbTypeCode(String caseTypeStr) {
        if (caseTypeStr == null) return 10;
        return switch (caseTypeStr.toLowerCase()) {
            case "garnishee" -> 9;
            case "defence" -> 11;
            case "arbitration" -> 13;
            default -> 10; // claims
        };
    }

    private String getTargetTable(String caseType) {
        return switch (caseType.toLowerCase()) {
            case "garnishee" -> "garnishee";
            case "defence" -> "defence";
            case "arbitration" -> "arbitration";
            default -> "claim";
        };
    }

    private String getTargetIdCol(String caseType) {
        return switch (caseType.toLowerCase()) {
            case "garnishee" -> "garnisheeID";
            case "defence" -> "defenceID";
            case "arbitration" -> "arbitrationID";
            default -> "claimID";
        };
    }

    private boolean isCounselAllocated(int officerID, int rawID, int dbType) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM cldcaseofficer WHERE caseID = ? AND caseType = ? AND officerID = ?",
                Integer.class, rawID, dbType, officerID
        );
        return count != null && count > 0;
    }

    private void ensureDocumentsTableExists() {
        try {
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS `documents` (
                  `documentID` INT AUTO_INCREMENT PRIMARY KEY,
                  `caseID` INT NOT NULL,
                  `caseType` INT NOT NULL,
                  `documentClass` INT DEFAULT 1,
                  `name` VARCHAR(255) NOT NULL,
                  `size` BIGINT DEFAULT 0,
                  `type` VARCHAR(100) DEFAULT 'application/pdf',
                  `uploadDate` TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
            """);
        } catch (Exception e) {
            System.err.println("Notice on documents table check: " + e.getMessage());
        }
    }

    @GetMapping
    public ResponseEntity<?> getAllCases() {
        try {
            // 1. Fetch Claims (caseType = 10)
            String claimsSql = """
                SELECT c.claimID as caseID, 'Claims' as caseType, c.fileNumber, c.dateRecieved as created_at, c.closed, c.preClosed,
                       MAX(ca.amount) as amount, MAX(ca.installment) as collected, MAX(ca.balance) as outstanding, MAX(ca.lastPaymentDate) as lastPaymentDate,
                       MAX(l.subject) as title, MAX(co.officerID) as counselID, MAX(co.recieved) as counselRecieved,
                       MAX(m.name) as ministryName
                FROM claim c
                LEFT JOIN caseamounts ca ON c.claimID = ca.caseID AND ca.caseType = 10
                LEFT JOIN letter l ON c.claimID = l.caseID AND l.caseType = 10
                LEFT JOIN cldcaseofficer co ON c.claimID = co.caseID AND co.caseType = 10
                LEFT JOIN ministries m ON c.ministryID = m.ministryID
                GROUP BY c.claimID, c.fileNumber, c.dateRecieved, c.closed, c.preClosed
                ORDER BY c.claimID DESC
            """;

            // 2. Fetch Garnishees (caseType = 9)
            String garnisheeSql = """
                SELECT g.garnisheeID as caseID, 'Garnishee' as caseType, g.fileNumber, g.dateRecieved as created_at, g.closed, g.preClosed,
                       MAX(ca.amount) as amount, MAX(ca.installment) as collected, MAX(ca.balance) as outstanding, MAX(ca.lastPaymentDate) as lastPaymentDate,
                       MAX(l.subject) as title, MAX(co.officerID) as counselID, MAX(co.recieved) as counselRecieved,
                       NULL as ministryName
                FROM garnishee g
                LEFT JOIN caseamounts ca ON g.garnisheeID = ca.caseID AND ca.caseType = 9
                LEFT JOIN letter l ON g.garnisheeID = l.caseID AND l.caseType = 9
                LEFT JOIN cldcaseofficer co ON g.garnisheeID = co.caseID AND co.caseType = 9
                GROUP BY g.garnisheeID, g.fileNumber, g.dateRecieved, g.closed, g.preClosed
                ORDER BY g.garnisheeID DESC
            """;

            // 3. Fetch Defences (caseType = 11)
            String defenceSql = """
                SELECT d.defenceID as caseID, 'Defence' as caseType, d.fileNumber, d.dateRecieved as created_at, d.closed, d.preClosed,
                       MAX(ca.amount) as amount, MAX(ca.installment) as collected, MAX(ca.balance) as outstanding, MAX(ca.lastPaymentDate) as lastPaymentDate,
                       MAX(l.subject) as title, MAX(co.officerID) as counselID, MAX(co.recieved) as counselRecieved,
                       NULL as ministryName
                FROM defence d
                LEFT JOIN caseamounts ca ON d.defenceID = ca.caseID AND ca.caseType = 11
                LEFT JOIN letter l ON d.defenceID = l.caseID AND l.caseType = 11
                LEFT JOIN cldcaseofficer co ON d.defenceID = co.caseID AND co.caseType = 11
                GROUP BY d.defenceID, d.fileNumber, d.dateRecieved, d.closed, d.preClosed
                ORDER BY d.defenceID DESC
            """;

            // 4. Fetch Arbitrations (caseType = 13)
            String arbitrationSql = """
                SELECT a.arbitrationID as caseID, 'Arbitration' as caseType, a.fileNumber, a.dateRecieved as created_at, a.closed, a.preClosed,
                       MAX(ca.amount) as amount, MAX(ca.installment) as collected, MAX(ca.balance) as outstanding, MAX(ca.lastPaymentDate) as lastPaymentDate,
                       MAX(l.subject) as title, MAX(co.officerID) as counselID, MAX(co.recieved) as counselRecieved,
                       NULL as ministryName
                FROM arbitration a
                LEFT JOIN caseamounts ca ON a.arbitrationID = ca.caseID AND ca.caseType = 13
                LEFT JOIN letter l ON a.arbitrationID = l.caseID AND l.caseType = 13
                LEFT JOIN cldcaseofficer co ON a.arbitrationID = co.caseID AND co.caseType = 13
                GROUP BY a.arbitrationID, a.fileNumber, a.dateRecieved, a.closed, a.preClosed
                ORDER BY a.arbitrationID DESC
            """;

            List<Map<String, Object>> allRaw = new ArrayList<>();
            allRaw.addAll(jdbcTemplate.queryForList(claimsSql));
            allRaw.addAll(jdbcTemplate.queryForList(garnisheeSql));
            allRaw.addAll(jdbcTemplate.queryForList(defenceSql));
            allRaw.addAll(jdbcTemplate.queryForList(arbitrationSql));

            // Fetch lookup maps
            Map<Integer, String> officerMap = new HashMap<>();
            List<Map<String, Object>> officers = jdbcTemplate.queryForList("""
                SELECT o.officerID, o.username, p.firstName, p.surname 
                FROM officer o 
                LEFT JOIN person p ON o.officerID = p.personID
            """);
            for (Map<String, Object> off : officers) {
                int id = ((Number) off.get("officerID")).intValue();
                String fName = (String) off.get("firstName");
                String sName = (String) off.get("surname");
                String username = (String) off.get("username");
                String full = (fName != null ? fName : "") + " " + (sName != null ? sName : "");
                officerMap.put(id, full.trim().isEmpty() ? username : full.trim());
            }

            // Fetch bringups
            Map<String, String> bringupMap = new HashMap<>();
            List<Map<String, Object>> bringups = jdbcTemplate.queryForList("""
                SELECT caseID, caseType, MAX(bringUpDate) as maxDate 
                FROM bringup 
                WHERE broughtBack = 0 
                GROUP BY caseID, caseType
            """);
            for (Map<String, Object> bu : bringups) {
                int cId = ((Number) bu.get("caseID")).intValue();
                int cType = ((Number) bu.get("caseType")).intValue();
                String date = String.valueOf(bu.get("maxDate"));
                bringupMap.put(cType + "-" + cId, date);
            }

            // Fetch verdicts
            Map<String, String> verdictMap = new HashMap<>();
            List<Map<String, Object>> verdicts = jdbcTemplate.queryForList("""
                SELECT caseID, caseType, description 
                FROM caseverdict
            """);
            for (Map<String, Object> v : verdicts) {
                int cId = ((Number) v.get("caseID")).intValue();
                int cType = ((Number) v.get("caseType")).intValue();
                String desc = (String) v.get("description");
                verdictMap.put(cType + "-" + cId, desc);
            }

            // Fetch documents mapped by dbType-caseID
            Map<String, List<Map<String, Object>>> documentsMap = new HashMap<>();
            try {
                ensureDocumentsTableExists();
                List<Map<String, Object>> docs = jdbcTemplate.queryForList("""
                    SELECT documentID, caseID, caseType, name, size, type, uploadDate 
                    FROM documents 
                    ORDER BY documentID DESC
                """);
                for (Map<String, Object> doc : docs) {
                    int cId = ((Number) doc.get("caseID")).intValue();
                    int cType = ((Number) doc.get("caseType")).intValue();
                    String key = cType + "-" + cId;
                    documentsMap.computeIfAbsent(key, k -> new ArrayList<>()).add(doc);
                }
            } catch (Exception docEx) {
                System.err.println("Notice: could not load case documents: " + docEx.getMessage());
            }

            // Build result list
            List<Map<String, Object>> formattedList = new ArrayList<>();
            for (Map<String, Object> r : allRaw) {
                int rawID = ((Number) r.get("caseID")).intValue();
                String caseType = (String) r.get("caseType");
                int dbType = getDbTypeCode(caseType);
                String caseKey = caseType.toLowerCase() + "-" + rawID;

                Object counselIDObj = r.get("counselID");
                String counselName = "Unallocated";
                String counselIDStr = null;
                boolean isCounselRecieved = false;

                if (counselIDObj != null) {
                    int cID = ((Number) counselIDObj).intValue();
                    counselIDStr = String.valueOf(cID);
                    counselName = officerMap.getOrDefault(cID, "Officer #" + cID);
                    Object recObj = r.get("counselRecieved");
                    isCounselRecieved = recObj != null && ("1".equals(String.valueOf(recObj)) || Boolean.TRUE.equals(recObj));
                }

                boolean closed = "1".equals(String.valueOf(r.get("closed"))) || Boolean.TRUE.equals(r.get("closed"));
                String status;
                if (closed) {
                    status = "Closed";
                } else if (counselIDStr == null) {
                    status = "Unallocated";
                } else if (!isCounselRecieved) {
                    status = "Pending Acceptance";
                } else {
                    status = "Allocated";
                }

                String bringUpDate = bringupMap.get(dbType + "-" + rawID);
                String verdict = verdictMap.get(dbType + "-" + rawID);
                List<Map<String, Object>> caseDocs = documentsMap.getOrDefault(dbType + "-" + rawID, Collections.emptyList());

                Map<String, Object> item = new HashMap<>();
                item.put("id", caseKey);
                item.put("caseID", caseKey);
                item.put("rawID", rawID);
                item.put("fileNumber", r.get("fileNumber"));
                item.put("caseType", caseType);
                item.put("title", r.get("title") != null ? r.get("title") : "Case Matter #" + r.get("fileNumber"));
                item.put("counsel", counselName);
                item.put("counselID", counselIDStr);
                item.put("status", status);
                item.put("amount", r.get("amount") != null ? r.get("amount") : 0.0);
                item.put("collected", r.get("collected") != null ? r.get("collected") : 0.0);
                item.put("outstanding", r.get("outstanding") != null ? r.get("outstanding") : r.get("amount"));
                item.put("lastPaymentDate", r.get("lastPaymentDate"));
                item.put("bringUpDate", bringUpDate);
                item.put("verdict", verdict);
                item.put("documents", caseDocs);
                item.put("ministryName", r.get("ministryName"));
                item.put("created_at", r.get("created_at"));

                formattedList.add(item);
            }

            return ResponseEntity.ok(formattedList);

        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Error fetching case records"));
        }
    }

    @PostMapping
    @Transactional
    public ResponseEntity<?> createCase(@Valid @RequestBody CreateCaseRequest req, HttpServletRequest httpRequest) {
        AuthenticatedUser user = getAuthUser(httpRequest);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));
        }

        String caseType = req.getCaseType().trim();
        String fileNumber = SanitizerUtils.sanitizeText(req.getFileNumber(), 100);
        String title = SanitizerUtils.sanitizeText(req.getTitle(), 255);
        String ministryName = SanitizerUtils.sanitizeText(req.getMinistryName(), 255);
        Object amountObj = req.getAmount();

        double cleanAmount = 0.0;
        if (amountObj != null) {
            try {
                cleanAmount = Double.parseDouble(String.valueOf(amountObj).replaceAll("[^0-9.]", ""));
            } catch (Exception ignored) {}
        }

        Integer resolvedMinistryID = null;
        if (ministryName != null && !ministryName.trim().isEmpty()) {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT ministryID FROM ministries WHERE name LIKE ? OR ? LIKE CONCAT('%', name, '%') LIMIT 1",
                "%" + ministryName + "%", ministryName
            );
            if (!rows.isEmpty()) {
                resolvedMinistryID = ((Number) rows.get(0).get("ministryID")).intValue();
            }
        }

        int dbType = getDbTypeCode(caseType);
        String targetTable = getTargetTable(caseType);
        String targetIdCol = getTargetIdCol(caseType);

        // Check if file number already exists
        List<Map<String, Object>> existing = jdbcTemplate.queryForList(
            "SELECT " + targetIdCol + " as id FROM " + targetTable + " WHERE fileNumber = ? LIMIT 1", fileNumber
        );
        if (!existing.isEmpty()) {
            int existId = ((Number) existing.get(0).get("id")).intValue();
            return ResponseEntity.ok(Map.of(
                "success", true,
                "caseID", caseType.toLowerCase() + "-" + existId,
                "rawID", existId,
                "alreadyExists", true
            ));
        }

        KeyHolder keyHolder = new GeneratedKeyHolder();
        final String finFileNo = fileNumber;
        final Integer finMinistryID = resolvedMinistryID;

        if ("Claims".equalsIgnoreCase(caseType)) {
            jdbcTemplate.update(con -> {
                PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO claim (dateRecieved, fileNumber, locationID, closed, preClosed, subdivision, instanceID, ministryID) VALUES (CURDATE(), ?, 1, 0, 0, 1, 1, ?)",
                    Statement.RETURN_GENERATED_KEYS
                );
                ps.setString(1, finFileNo);
                if (finMinistryID != null) {
                    ps.setInt(2, finMinistryID);
                } else {
                    ps.setNull(2, java.sql.Types.INTEGER);
                }
                return ps;
            }, keyHolder);
        } else if ("Garnishee".equalsIgnoreCase(caseType)) {
            final String courtFileNo = "CRT-" + finFileNo + "-" + (System.currentTimeMillis() % 10000);
            jdbcTemplate.update(con -> {
                PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO garnishee (dateRecieved, fileNumber, courtFileNumber, caseInstanceID, sourceType, sourceID, subdivision, locationID, closed, preClosed, installment) VALUES (CURDATE(), ?, ?, 1, 1, 1, 1, 1, 0, 0, 0)",
                    Statement.RETURN_GENERATED_KEYS
                );
                ps.setString(1, finFileNo);
                ps.setString(2, courtFileNo);
                return ps;
            }, keyHolder);
        } else if ("Defence".equalsIgnoreCase(caseType)) {
            jdbcTemplate.update(con -> {
                PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO defence (dateRecieved, fileNumber, locationID, closed, preClosed, subdivision, instanceID, defenceType, natureType) VALUES (CURDATE(), ?, 1, 0, 0, 1, 1, 1, 1)",
                    Statement.RETURN_GENERATED_KEYS
                );
                ps.setString(1, finFileNo);
                return ps;
            }, keyHolder);
        } else { // Arbitration
            jdbcTemplate.update(con -> {
                PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO arbitration (dateRecieved, fileNumber, locationID, closed, preClosed) VALUES (CURDATE(), ?, 1, 0, 0)",
                    Statement.RETURN_GENERATED_KEYS
                );
                ps.setString(1, finFileNo);
                return ps;
            }, keyHolder);
        }

        int newCaseId = keyHolder.getKey().intValue();

        // 2. Insert into letter
        String letterRef = "REF-" + dbType + "-" + (System.currentTimeMillis() % 100000);
        jdbcTemplate.update(
            "INSERT INTO letter (letterRef, letterDate, subject, caseID, caseType) VALUES (?, CURDATE(), ?, ?, ?)",
            letterRef, title, newCaseId, dbType
        );

        // 3. Insert into caseamounts
        jdbcTemplate.update(
            "INSERT INTO caseamounts (amount, caseID, balance, caseType) VALUES (?, ?, ?, ?)",
            cleanAmount, newCaseId, cleanAmount, dbType
        );

        // 4. Log Registration Transaction with server-derived officerID
        jdbcTemplate.update(
            "INSERT INTO transactions (caseID, caseType, officerID, activityID, dateRecorded, transactionDate) VALUES (?, ?, ?, 1, NOW(), CURDATE())",
            newCaseId, dbType, user.getOfficerID()
        );

        // 5. Optional initial counsel allocation
        Object counselIDObj = req.getCounselID();
        Integer counselID = null;
        if (counselIDObj != null && !String.valueOf(counselIDObj).trim().isEmpty()) {
            try {
                counselID = Integer.parseInt(String.valueOf(counselIDObj));
            } catch (Exception ignored) {}
        }

        if (counselID != null) {
            jdbcTemplate.update(
                "INSERT INTO cldcaseofficer (caseID, officerID, caseType, recieved) VALUES (?, ?, ?, 0)",
                newCaseId, counselID, dbType
            );
            jdbcTemplate.update(
                "INSERT INTO transactions (caseID, caseType, officerID, activityID, dateRecorded, transactionDate) VALUES (?, ?, ?, 5, NOW(), CURDATE())",
                newCaseId, dbType, counselID
            );
            String counselMsg = String.format("New %s matter #%s (%s) has been allocated to you for representation.", caseType, finFileNo, title);
            jdbcTemplate.update(
                "INSERT INTO notifications (user_id, message, case_id, is_read, created_at) VALUES (?, ?, ?, 0, NOW())",
                counselID, counselMsg, caseType.toLowerCase() + "-" + newCaseId
            );
        }

        // 6. Notify Allocating Officers
        try {
            List<Map<String, Object>> allocatingOfficers = jdbcTemplate.queryForList(
                "SELECT officerID FROM officer WHERE userType = 4 AND active = 1"
            );
            String allocMsg = String.format("📁 New Case Registered: %s matter #%s (%s) received and ready for counsel allocation.", caseType, finFileNo, title);
            for (Map<String, Object> allocOff : allocatingOfficers) {
                int allocID = ((Number) allocOff.get("officerID")).intValue();
                jdbcTemplate.update(
                    "INSERT INTO notifications (user_id, message, case_id, is_read, created_at) VALUES (?, ?, ?, 0, NOW())",
                    allocID, allocMsg, caseType.toLowerCase() + "-" + newCaseId
                );
            }
        } catch (Exception ignored) {}

        return ResponseEntity.ok(Map.of(
            "success", true,
            "caseID", caseType.toLowerCase() + "-" + newCaseId,
            "rawID", newCaseId
        ));
    }

    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<?> updateCase(@PathVariable String id, @Valid @RequestBody UpdateCaseRequest req, HttpServletRequest httpRequest) {
        AuthenticatedUser user = getAuthUser(httpRequest);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));
        }

        String[] parts = id.split("-");
        String caseType = parts[0].toLowerCase();
        int rawID = parts.length > 1 ? Integer.parseInt(parts[1]) : Integer.parseInt(parts[0]);
        int dbType = getDbTypeCode(caseType);

        // RLS Authorization Check: Only Admin, Allocating Officer, Registry, or the Assigned Counsel can modify
        if (!user.isAdmin() && !user.isAllocatingOfficer() && !user.isRegistry() && !isCounselAllocated(user.getOfficerID(), rawID, dbType)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "You do not have permission to modify this case"));
        }

        String fileNumber = SanitizerUtils.sanitizeText(req.getFileNumber(), 100);
        String title = SanitizerUtils.sanitizeText(req.getTitle(), 255);
        Object amountObj = req.getAmount();

        String tableName = getTargetTable(caseType);
        String idColumn = getTargetIdCol(caseType);

        if (fileNumber != null && !fileNumber.trim().isEmpty()) {
            jdbcTemplate.update("UPDATE " + tableName + " SET fileNumber = ? WHERE " + idColumn + " = ?", fileNumber, rawID);
        }

        if (title != null && !title.trim().isEmpty()) {
            jdbcTemplate.update("UPDATE letter SET subject = ? WHERE caseID = ? AND caseType = ?", title, rawID, dbType);
        }

        if (amountObj != null) {
            double cleanAmount = Double.parseDouble(String.valueOf(amountObj).replaceAll("[^0-9.]", ""));
            jdbcTemplate.update("UPDATE caseamounts SET amount = ? WHERE caseID = ? AND caseType = ?", cleanAmount, rawID, dbType);
        }

        jdbcTemplate.update(
            "INSERT INTO transactions (caseID, caseType, officerID, activityID, dateRecorded, transactionDate) VALUES (?, ?, ?, 23, NOW(), CURDATE())",
            rawID, dbType, user.getOfficerID()
        );

        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/{id}/allocate")
    public ResponseEntity<?> allocateCase(@PathVariable String id, @RequestBody AllocateCaseRequest req, HttpServletRequest httpRequest) {
        AuthenticatedUser user = getAuthUser(httpRequest);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));
        }

        // Authorization: Only Allocating Officer (4) or Admin (1) can allocate cases
        if (!user.isAllocatingOfficer() && !user.isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Only Allocating Officers and Administrators can allocate matters"));
        }

        String[] parts = id.split("-");
        String caseType = parts[0].toLowerCase();
        int rawID = parts.length > 1 ? Integer.parseInt(parts[1]) : Integer.parseInt(parts[0]);
        int dbType = getDbTypeCode(caseType);

        Object counselIDObj = req.getCounselID();
        Integer counselID = counselIDObj != null && !String.valueOf(counselIDObj).trim().isEmpty()
            ? Integer.parseInt(String.valueOf(counselIDObj))
            : null;

        List<Map<String, Object>> exists = jdbcTemplate.queryForList(
            "SELECT * FROM cldcaseofficer WHERE caseID = ? AND caseType = ?", rawID, dbType
        );

        if (!exists.isEmpty()) {
            if (counselID != null) {
                jdbcTemplate.update("UPDATE cldcaseofficer SET officerID = ?, recieved = 0 WHERE caseID = ? AND caseType = ?", counselID, rawID, dbType);
            } else {
                jdbcTemplate.update("DELETE FROM cldcaseofficer WHERE caseID = ? AND caseType = ?", rawID, dbType);
            }
        } else if (counselID != null) {
            jdbcTemplate.update("INSERT INTO cldcaseofficer (caseID, officerID, caseType, recieved) VALUES (?, ?, ?, 0)", rawID, counselID, dbType);
        }

        jdbcTemplate.update(
            "INSERT INTO transactions (caseID, caseType, officerID, activityID, dateRecorded, transactionDate) VALUES (?, ?, ?, 5, NOW(), CURDATE())",
            rawID, dbType, user.getOfficerID()
        );

        // Notify Assigned Counsel
        if (counselID != null) {
            String msg = "A case matter #" + rawID + " has been allocated to you for representation.";
            jdbcTemplate.update("INSERT INTO notifications (user_id, message, case_id) VALUES (?, ?, ?)", counselID, msg, id);
        }

        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/{id}/activities")
    public ResponseEntity<?> addActivity(@PathVariable String id, @Valid @RequestBody AddActivityRequest req, HttpServletRequest httpRequest) {
        AuthenticatedUser user = getAuthUser(httpRequest);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));
        }

        String[] parts = id.split("-");
        String caseType = parts[0].toLowerCase();
        int rawID = parts.length > 1 ? Integer.parseInt(parts[1]) : Integer.parseInt(parts[0]);
        int dbType = getDbTypeCode(caseType);

        // Authorization: Assigned Counsel, Allocating Officer, Registry, or Admin
        if (!user.isAdmin() && !user.isAllocatingOfficer() && !user.isRegistry() && !isCounselAllocated(user.getOfficerID(), rawID, dbType)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "You are not authorized to log activities on this case"));
        }

        String type = SanitizerUtils.sanitizeText(req.getType(), 100);
        String description = SanitizerUtils.sanitizeText(req.getDescription(), 2000);
        String officerName = (user.getUsername() != null) ? user.getUsername() : "Officer";

        jdbcTemplate.update(
            "INSERT INTO cld_case_work_logs (caseID, type, description, officerName, officerID) VALUES (?, ?, ?, ?, ?)",
            id, type, description, officerName, user.getOfficerID()
        );

        jdbcTemplate.update(
            "INSERT INTO transactions (caseID, caseType, officerID, activityID, dateRecorded, transactionDate) VALUES (?, ?, ?, 23, NOW(), CURDATE())",
            rawID, dbType, user.getOfficerID()
        );

        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/{id}/bringup")
    public ResponseEntity<?> scheduleBringup(@PathVariable String id, @Valid @RequestBody ScheduleBringupRequest req, HttpServletRequest httpRequest) {
        AuthenticatedUser user = getAuthUser(httpRequest);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));
        }

        String[] parts = id.split("-");
        String caseType = parts[0].toLowerCase();
        int rawID = parts.length > 1 ? Integer.parseInt(parts[1]) : Integer.parseInt(parts[0]);
        int dbType = getDbTypeCode(caseType);

        String bringUpDate = req.getBringUpDate();

        jdbcTemplate.update(
            "INSERT INTO bringup (caseID, caseType, officerID, bringUpDate, collected, recieved, broughtBack) VALUES (?, ?, ?, ?, 0, 0, 0)",
            rawID, dbType, user.getOfficerID(), bringUpDate
        );

        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/{id}/conclude")
    @Transactional
    public ResponseEntity<?> concludeCase(@PathVariable String id, @Valid @RequestBody ConcludeCaseRequest req, HttpServletRequest httpRequest) {
        AuthenticatedUser user = getAuthUser(httpRequest);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));
        }

        String[] parts = id.split("-");
        String caseType = parts[0].toLowerCase();
        int rawID = parts.length > 1 ? Integer.parseInt(parts[1]) : Integer.parseInt(parts[0]);
        int dbType = getDbTypeCode(caseType);

        // Authorization check: Assigned Counsel, Allocating Officer, or Admin
        if (!user.isAdmin() && !user.isAllocatingOfficer() && !isCounselAllocated(user.getOfficerID(), rawID, dbType)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "You do not have permission to conclude this matter"));
        }

        Object verdictIDObj = req.getVerdictID();
        int verdictID = verdictIDObj instanceof Number ? ((Number) verdictIDObj).intValue() : 1;
        String verdictText = SanitizerUtils.sanitizeText(req.getVerdictText(), 2000);

        String tableName = getTargetTable(caseType);
        String idColumn = getTargetIdCol(caseType);

        jdbcTemplate.update("UPDATE " + tableName + " SET closed = 1 WHERE " + idColumn + " = ?", rawID);

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                "INSERT INTO transactions (caseID, caseType, officerID, activityID, dateRecorded, transactionDate) VALUES (?, ?, ?, 2, NOW(), CURDATE())",
                Statement.RETURN_GENERATED_KEYS
            );
            ps.setInt(1, rawID);
            ps.setInt(2, dbType);
            ps.setInt(3, user.getOfficerID());
            return ps;
        }, keyHolder);

        int txID = keyHolder.getKey().intValue();

        jdbcTemplate.update(
            "INSERT INTO caseverdict (caseID, caseType, verdictID, description, transactionID) VALUES (?, ?, ?, ?, ?)",
            rawID, dbType, verdictID, verdictText != null ? verdictText : "Matter concluded", txID
        );

        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/{id}/respond")
    public ResponseEntity<?> respondAllocation(@PathVariable String id, @Valid @RequestBody RespondCaseRequest req, HttpServletRequest httpRequest) {
        AuthenticatedUser user = getAuthUser(httpRequest);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));
        }

        String[] parts = id.split("-");
        String caseType = parts[0].toLowerCase();
        int rawID = parts.length > 1 ? Integer.parseInt(parts[1]) : Integer.parseInt(parts[0]);
        int dbType = getDbTypeCode(caseType);

        int officerID = user.getOfficerID();
        String response = req.getResponse();

        if ("accept".equals(response)) {
            jdbcTemplate.update(
                "UPDATE cldcaseofficer SET recieved = 1, recieptDate = NOW() WHERE caseID = ? AND caseType = ? AND officerID = ?",
                rawID, dbType, officerID
            );
            jdbcTemplate.update(
                "INSERT INTO transactions (caseID, caseType, officerID, activityID, dateRecorded, transactionDate) VALUES (?, ?, ?, 4, NOW(), CURDATE())",
                rawID, dbType, officerID
            );
        } else if ("decline".equals(response)) {
            jdbcTemplate.update(
                "DELETE FROM cldcaseofficer WHERE caseID = ? AND caseType = ? AND officerID = ?",
                rawID, dbType, officerID
            );
            jdbcTemplate.update(
                "INSERT INTO transactions (caseID, caseType, officerID, activityID, dateRecorded, transactionDate) VALUES (?, ?, ?, 24, NOW(), CURDATE())",
                rawID, dbType, officerID
            );
            jdbcTemplate.update(
                "INSERT INTO notifications (user_id, message, case_id) VALUES (4, ?, ?)",
                "Case allocation declined by " + user.getUsername() + " for Case #" + rawID, String.valueOf(rawID)
            );
        }

        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/{id}/sheriff")
    public ResponseEntity<?> assignSheriff(@PathVariable String id, @Valid @RequestBody SheriffActionRequest req, HttpServletRequest httpRequest) {
        AuthenticatedUser user = getAuthUser(httpRequest);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));
        }

        String[] parts = id.split("-");
        String caseType = parts[0].toLowerCase();
        int rawID = parts.length > 1 ? Integer.parseInt(parts[1]) : Integer.parseInt(parts[0]);
        int dbType = getDbTypeCode(caseType);

        String actionType = req.getAction_type();
        Object sheriffIdObj = req.getSheriff_id();
        int sheriffId = sheriffIdObj instanceof Number ? ((Number) sheriffIdObj).intValue() : 1;

        String defendantName = SanitizerUtils.sanitizeText(req.getDefendant_name(), 200);
        String defendantAddress = SanitizerUtils.sanitizeText(req.getDefendant_address(), 500);
        String notes = SanitizerUtils.sanitizeText(req.getNotes(), 2000);
        String instructions = SanitizerUtils.sanitizeText(req.getInstructions(), 2000);
        String writType = SanitizerUtils.sanitizeText(req.getWrit_type(), 100);

        if ("send_summons".equals(actionType)) {
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbcTemplate.update(con -> {
                PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO summons (case_id, case_type, assigned_by, assigned_sheriff, defendant_name, defendant_address, notes, status) VALUES (?, ?, ?, ?, ?, ?, ?, 'Assigned')",
                    Statement.RETURN_GENERATED_KEYS
                );
                ps.setInt(1, rawID);
                ps.setInt(2, dbType);
                ps.setInt(3, user.getOfficerID());
                ps.setInt(4, sheriffId);
                ps.setString(5, defendantName != null ? defendantName : "Defendant");
                ps.setString(6, defendantAddress);
                ps.setString(7, notes);
                return ps;
            }, keyHolder);

            String msg = "NEW SUMMONS ASSIGNED. Case: #" + rawID + ", Defendant: " + defendantName + ", Notes: " + notes;
            jdbcTemplate.update("INSERT INTO notifications (user_id, message, case_id) VALUES (?, ?, ?)", sheriffId, msg, String.valueOf(rawID));

        } else if ("send_writ".equals(actionType)) {
            Integer summonId = null;
            List<Map<String, Object>> summonsRows = jdbcTemplate.queryForList(
                "SELECT summon_id FROM summons WHERE case_id = ? AND assigned_sheriff = ? ORDER BY assigned_at DESC LIMIT 1",
                rawID, sheriffId
            );

            if (!summonsRows.isEmpty()) {
                summonId = ((Number) summonsRows.get(0).get("summon_id")).intValue();
            } else {
                KeyHolder autoKey = new GeneratedKeyHolder();
                jdbcTemplate.update(con -> {
                    PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO summons (case_id, case_type, assigned_by, assigned_sheriff, defendant_name, status) VALUES (?, ?, ?, ?, ?, 'Served')",
                        Statement.RETURN_GENERATED_KEYS
                    );
                    ps.setInt(1, rawID);
                    ps.setInt(2, dbType);
                    ps.setInt(3, user.getOfficerID());
                    ps.setInt(4, sheriffId);
                    ps.setString(5, defendantName != null ? defendantName : "Defendant");
                    return ps;
                }, autoKey);
                summonId = autoKey.getKey().intValue();
            }

            final int finSummonId = summonId;
            jdbcTemplate.update(
                "INSERT INTO writs_of_execution (summon_id, assigned_by, assigned_sheriff, writ_type, instructions, status) VALUES (?, ?, ?, ?, ?, 'Pending')",
                finSummonId, user.getOfficerID(), sheriffId, writType, instructions
            );

            String msg = "NEW WRIT OF EXECUTION. Case: #" + rawID + ", Type: " + writType;
            jdbcTemplate.update("INSERT INTO notifications (user_id, message, case_id) VALUES (?, ?, ?)", sheriffId, msg, String.valueOf(rawID));
        }

        jdbcTemplate.update(
            "INSERT INTO transactions (caseID, caseType, officerID, activityID, dateRecorded, transactionDate) VALUES (?, ?, ?, 23, NOW(), CURDATE())",
            rawID, dbType, user.getOfficerID()
        );

        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/{id}/documents")
    public ResponseEntity<?> uploadDocument(@PathVariable String id, @RequestParam("file") MultipartFile file, HttpServletRequest httpRequest) {
        AuthenticatedUser user = getAuthUser(httpRequest);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));
        }

        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "No file uploaded"));
        }

        String[] parts = id.split("-");
        String caseType = parts[0].toLowerCase();
        int rawID = parts.length > 1 ? Integer.parseInt(parts[1]) : Integer.parseInt(parts[0]);
        int dbType = getDbTypeCode(caseType);

        try {
            ensureDocumentsTableExists();

            Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            String cleanName = SanitizerUtils.sanitizeFilename(file.getOriginalFilename());
            String storedFilename = System.currentTimeMillis() + "-" + Math.round(Math.random() * 1E9) + "-" + cleanName;
            Path targetLocation = uploadPath.resolve(storedFilename);
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);

            jdbcTemplate.update(
                "INSERT INTO documents (caseID, caseType, documentClass, name, size, type, uploadDate) VALUES (?, ?, 1, ?, ?, ?, NOW())",
                rawID, dbType, storedFilename, file.getSize(), file.getContentType()
            );

            try {
                jdbcTemplate.update(
                    "INSERT INTO transactions (caseID, caseType, officerID, activityID, dateRecorded, transactionDate) VALUES (?, ?, ?, 10, NOW(), CURDATE())",
                    rawID, dbType, user.getOfficerID()
                );
            } catch (Exception txEx) {
                System.err.println("Notice on transaction recording: " + txEx.getMessage());
            }

            return ResponseEntity.ok(Map.of(
                "success", true,
                "name", storedFilename,
                "size", file.getSize(),
                "type", file.getContentType() != null ? file.getContentType() : "application/pdf"
            ));
        } catch (Exception e) {
            System.err.println("Error saving document: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Failed to save file securely: " + e.getMessage()));
        }
    }

    @PostMapping("/{id}/payments")
    @Transactional
    public ResponseEntity<?> recordPayment(@PathVariable String id, @Valid @RequestBody RecordPaymentRequest req, HttpServletRequest httpRequest) {
        AuthenticatedUser user = getAuthUser(httpRequest);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));
        }

        String[] parts = id.split("-");
        String caseType = parts[0].toLowerCase();
        int rawID = parts.length > 1 ? Integer.parseInt(parts[1]) : Integer.parseInt(parts[0]);
        int dbType = getDbTypeCode(caseType);

        Object amountObj = req.getPaymentAmount();
        double cleanAmount = 0.0;
        if (amountObj != null) {
            cleanAmount = Double.parseDouble(String.valueOf(amountObj).replaceAll("[^0-9.]", ""));
        }

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT amount, balance, installment FROM caseamounts WHERE caseID = ? AND caseType = ?", rawID, dbType
        );

        if (!rows.isEmpty()) {
            Map<String, Object> current = rows.get(0);
            Number instNum = (Number) current.get("installment");
            double inst = instNum != null ? instNum.doubleValue() : 0.0;

            Number balNum = (Number) current.get("balance");
            double bal = balNum != null ? balNum.doubleValue() : 0.0;

            double newCollected = inst + cleanAmount;
            double newOutstanding = Math.max(bal - cleanAmount, 0.0);

            jdbcTemplate.update(
                "UPDATE caseamounts SET installment = ?, balance = ?, lastPaymentDate = NOW() WHERE caseID = ? AND caseType = ?",
                newCollected, newOutstanding, rawID, dbType
            );

            jdbcTemplate.update(
                "INSERT INTO transactions (caseID, caseType, officerID, activityID, dateRecorded, transactionDate) VALUES (?, ?, ?, 16, NOW(), CURDATE())",
                rawID, dbType, user.getOfficerID()
            );
        }

        return ResponseEntity.ok(Map.of("success", true));
    }
}

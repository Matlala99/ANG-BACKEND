package com.agc.cms.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.text.DecimalFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/cases")
public class CaseController {

    private final JdbcTemplate jdbcTemplate;

    @Value("${file.upload-dir:./uploads}")
    private String uploadDir;

    public CaseController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private boolean toBoolean(Object val) {
        if (val == null) return false;
        if (val instanceof Boolean b) return b;
        if (val instanceof Number n) return n.intValue() == 1;
        String s = String.valueOf(val).trim();
        return "1".equals(s) || "true".equalsIgnoreCase(s);
    }

    private double toDouble(Object val) {
        if (val == null) return 0.0;
        if (val instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(val).replaceAll("[^0-9.]", ""));
        } catch (Exception e) {
            return 0.0;
        }
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

    private long toLong(Object val) {
        if (val == null) return 0L;
        if (val instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(String.valueOf(val).trim());
        } catch (Exception e) {
            return 0L;
        }
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
            """;

            // 2. Fetch Garnishee (caseType = 9)
            String garnisheeSql = """
                SELECT g.garnisheeID as caseID, 'Garnishee' as caseType, g.fileNumber, g.dateRecieved as created_at, g.closed, g.preClosed,
                       MAX(ca.amount) as amount, MAX(ca.installment) as collected, MAX(ca.balance) as outstanding, MAX(ca.lastPaymentDate) as lastPaymentDate,
                       MAX(l.subject) as title, MAX(co.officerID) as counselID, MAX(co.recieved) as counselRecieved
                FROM garnishee g
                LEFT JOIN caseamounts ca ON g.garnisheeID = ca.caseID AND ca.caseType = 9
                LEFT JOIN letter l ON g.garnisheeID = l.caseID AND l.caseType = 9
                LEFT JOIN cldcaseofficer co ON g.garnisheeID = co.caseID AND co.caseType = 9
                GROUP BY g.garnisheeID, g.fileNumber, g.dateRecieved, g.closed, g.preClosed
            """;

            // 3. Fetch Defence (caseType IN (11, 12))
            String defenceSql = """
                SELECT d.defenceID as caseID, 'Defence' as caseType, d.fileNumber, d.dateRecieved as created_at, d.closed, d.preClosed,
                       MAX(ca.amount) as amount, MAX(ca.installment) as collected, MAX(ca.balance) as outstanding, MAX(ca.lastPaymentDate) as lastPaymentDate,
                       MAX(l.subject) as title, MAX(co.officerID) as counselID, MAX(co.recieved) as counselRecieved
                FROM defence d
                LEFT JOIN caseamounts ca ON d.defenceID = ca.caseID AND ca.caseType IN (11, 12)
                LEFT JOIN letter l ON d.defenceID = l.caseID AND l.caseType IN (11, 12)
                LEFT JOIN cldcaseofficer co ON d.defenceID = co.caseID AND co.caseType IN (11, 12)
                GROUP BY d.defenceID, d.fileNumber, d.dateRecieved, d.closed, d.preClosed
            """;

            // 4. Fetch Arbitration (caseType = 13)
            String arbitrationSql = """
                SELECT a.arbitrationID as caseID, 'Arbitration' as caseType, a.fileNumber, a.dateRecieved as created_at, a.closed, a.preClosed,
                       MAX(ca.amount) as amount, MAX(ca.installment) as collected, MAX(ca.balance) as outstanding, MAX(ca.lastPaymentDate) as lastPaymentDate,
                       MAX(l.subject) as title, MAX(co.officerID) as counselID, MAX(co.recieved) as counselRecieved
                FROM arbitration a
                LEFT JOIN caseamounts ca ON a.arbitrationID = ca.caseID AND ca.caseType = 13
                LEFT JOIN letter l ON a.arbitrationID = l.caseID AND l.caseType = 13
                LEFT JOIN cldcaseofficer co ON a.arbitrationID = co.caseID AND co.caseType = 13
                GROUP BY a.arbitrationID, a.fileNumber, a.dateRecieved, a.closed, a.preClosed
            """;

            List<Map<String, Object>> claims = jdbcTemplate.queryForList(claimsSql);
            List<Map<String, Object>> garnishees = jdbcTemplate.queryForList(garnisheeSql);
            List<Map<String, Object>> defences = jdbcTemplate.queryForList(defenceSql);
            List<Map<String, Object>> arbitrations = jdbcTemplate.queryForList(arbitrationSql);

            // Fetch documents map
            List<Map<String, Object>> dbDocs = jdbcTemplate.queryForList("SELECT * FROM documents");
            Map<String, List<Map<String, Object>>> docsMap = new HashMap<>();
            DecimalFormat df = new DecimalFormat("#,##0.0");

            for (Map<String, Object> d : dbDocs) {
                String key = d.get("caseID") + "-" + d.get("caseType");
                docsMap.putIfAbsent(key, new ArrayList<>());
                long size = toLong(d.get("size"));
                String sizeStr = size > 1024 * 1024
                    ? df.format((double) size / (1024 * 1024)) + " MB"
                    : df.format((double) size / 1024) + " KB";

                Object dateObj = d.get("uploadDate");
                String dateStr = dateObj != null ? dateObj.toString().substring(0, 10) : LocalDate.now().toString();

                Map<String, Object> docObj = Map.of(
                    "documentID", d.get("documentID"),
                    "name", d.get("name"),
                    "size", sizeStr,
                    "date", dateStr,
                    "type", d.get("type"),
                    "uploader", "Sarah Registry"
                );
                docsMap.get(key).add(docObj);
            }

            // Fetch custom activities from cld_case_work_logs
            List<Map<String, Object>> activitiesRows = jdbcTemplate.queryForList(
                "SELECT * FROM cld_case_work_logs ORDER BY dateRecorded DESC"
            );
            Map<String, List<Map<String, Object>>> activitiesMap = new HashMap<>();
            for (Map<String, Object> act : activitiesRows) {
                String cId = (String) act.get("caseID");
                activitiesMap.putIfAbsent(cId, new ArrayList<>());
                Map<String, Object> actObj = Map.of(
                    "date", act.get("dateRecorded"),
                    "type", act.get("type"),
                    "description", act.get("description"),
                    "officer", act.get("officerName")
                );
                activitiesMap.get(cId).add(actObj);
            }

            List<Map<String, Object>> rawList = new ArrayList<>();
            rawList.addAll(claims);
            rawList.addAll(garnishees);
            rawList.addAll(defences);
            rawList.addAll(arbitrations);

            Map<String, Integer> typeCodeMap = Map.of(
                "Claims", 10,
                "Garnishee", 9,
                "Defence", 11,
                "Arbitration", 13
            );

            List<Map<String, Object>> allCases = new ArrayList<>();
            DecimalFormat moneyFmt = new DecimalFormat("#,##0.00");

            for (Map<String, Object> c : rawList) {
                int rawID = toInt(c.get("caseID"));
                String caseType = (String) c.get("caseType");
                String fileNumber = (String) c.get("fileNumber");
                int tc = typeCodeMap.getOrDefault(caseType, 10);

                List<Map<String, Object>> caseDocs;
                if (tc == 11) {
                    caseDocs = new ArrayList<>();
                    caseDocs.addAll(docsMap.getOrDefault(rawID + "-11", Collections.emptyList()));
                    caseDocs.addAll(docsMap.getOrDefault(rawID + "-12", Collections.emptyList()));
                } else {
                    caseDocs = docsMap.getOrDefault(rawID + "-" + tc, Collections.emptyList());
                }

                String caseKey = caseType.toLowerCase() + "-" + rawID;
                List<Map<String, Object>> customActivities = activitiesMap.getOrDefault(caseKey, Collections.emptyList());

                Object createdAtObj = c.get("created_at");
                String createdAt = createdAtObj != null ? createdAtObj.toString() : Instant.now().toString();

                Map<String, Object> defaultActivity = Map.of(
                    "date", createdAt,
                    "type", "Created",
                    "description", "Litigation matter registered and filed by Registry",
                    "officer", "System Registry"
                );

                List<Map<String, Object>> activitiesList = new ArrayList<>(customActivities);
                activitiesList.add(defaultActivity);

                boolean isClosed = toBoolean(c.get("closed"));
                Object counselIDObj = c.get("counselID");
                String counselID = counselIDObj != null ? String.valueOf(counselIDObj) : null;
                boolean counselRecieved = toBoolean(c.get("counselRecieved"));

                String status = isClosed ? "Closed" : (counselID != null ? (counselRecieved ? "Accepted" : "Pending") : "Unallocated");

                double amtRaw = toDouble(c.get("amount"));
                double collected = toDouble(c.get("collected"));
                double outstanding = toDouble(c.get("outstanding"));

                String title = (String) c.get("title");
                if (title == null || title.trim().isEmpty()) {
                    title = "Litigation Matter - Reference " + fileNumber;
                }

                String ministryName = (String) c.get("ministryName");
                if (ministryName == null || ministryName.trim().isEmpty()) {
                    ministryName = "Other Ministry / General";
                }

                String court = "Arbitration".equals(caseType) ? "CADER Secretariat" : "High Court of Botswana";

                Map<String, Object> item = new HashMap<>();
                item.put("caseID", caseKey);
                item.put("rawID", rawID);
                item.put("caseType", caseType);
                item.put("fileNumber", fileNumber);
                item.put("title", title);
                item.put("amount", "BWP " + moneyFmt.format(amtRaw));
                item.put("amountRaw", amtRaw);
                item.put("collected", collected);
                item.put("outstanding", outstanding);
                item.put("lastPaymentDate", c.get("lastPaymentDate"));
                item.put("counselID", counselID);
                item.put("counselRecieved", counselRecieved ? 1 : 0);
                item.put("status", status);
                item.put("urgency", Math.random() > 0.8 ? "Urgent" : "Normal");
                item.put("created_at", createdAt);
                item.put("updated_at", createdAt);
                item.put("plaintiffs", List.of("The Attorney General of Botswana"));
                item.put("defendants", List.of("Private Opposing Party"));
                item.put("court", court);
                item.put("ministryName", ministryName);
                item.put("causeOfAction", "Litigation process files checked in and awaiting counsel argument.");
                item.put("activities", activitiesList);
                item.put("documents", caseDocs);
                item.put("schedules", Collections.emptyList());

                allCases.add(item);
            }

            System.out.println("📂 [FETCH CASES] Loaded " + allCases.size() + " cases from MySQL agc_cms database (Claims: " + claims.size() + ", Garnishee: " + garnishees.size() + ", Defence: " + defences.size() + ", Arbitration: " + arbitrations.size() + ")");
            return ResponseEntity.ok(allCases);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping
    @Transactional
    public ResponseEntity<?> createCase(@RequestBody Map<String, Object> body) {
        String caseType = (String) body.get("caseType");
        String fileNumber = (String) body.get("fileNumber");
        String title = (String) body.get("title");
        Object amountObj = body.get("amount");
        String ministryName = (String) body.get("ministryName");

        if (caseType == null || fileNumber == null || title == null ||
            caseType.trim().isEmpty() || fileNumber.trim().isEmpty() || title.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Case Type, File Number, and Case Title are required"));
        }

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
        String tableName = switch (caseType) {
            case "Garnishee" -> "garnishee";
            case "Defence" -> "defence";
            case "Arbitration" -> "arbitration";
            default -> "claim";
        };
        String idColumn = switch (caseType) {
            case "Garnishee" -> "garnisheeID";
            case "Defence" -> "defenceID";
            case "Arbitration" -> "arbitrationID";
            default -> "claimID";
        };

        // Check if existing
        List<Map<String, Object>> existing = jdbcTemplate.queryForList(
            "SELECT " + idColumn + " as id FROM " + tableName + " WHERE fileNumber = ? LIMIT 1", fileNumber
        );

        if (!existing.isEmpty()) {
            int existingId = ((Number) existing.get(0).get("id")).intValue();
            return ResponseEntity.ok(Map.of(
                "success", true,
                "caseID", caseType.toLowerCase() + "-" + existingId,
                "rawID", existingId,
                "alreadyExists", true
            ));
        }

        KeyHolder keyHolder = new GeneratedKeyHolder();
        final Integer finMinId = resolvedMinistryID;
        final String finFileNo = fileNumber;

        if ("Claims".equals(caseType)) {
            jdbcTemplate.update(con -> {
                PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO claim (dateRecieved, fileNumber, locationID, closed, preClosed, subdivision, instanceID, ministryID) VALUES (CURDATE(), ?, 1, 0, 0, 1, 1, ?)",
                    Statement.RETURN_GENERATED_KEYS
                );
                ps.setString(1, finFileNo);
                if (finMinId != null) ps.setInt(2, finMinId); else ps.setNull(2, java.sql.Types.INTEGER);
                return ps;
            }, keyHolder);
        } else if ("Garnishee".equals(caseType)) {
            String courtFileNo = "CRT-" + fileNumber + "-" + System.currentTimeMillis() % 10000;
            jdbcTemplate.update(con -> {
                PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO garnishee (dateRecieved, fileNumber, courtFileNumber, caseInstanceID, sourceType, sourceID, subdivision, locationID, closed, preClosed, installment) VALUES (CURDATE(), ?, ?, 1, 1, 1, 1, 1, 0, 0, 0)",
                    Statement.RETURN_GENERATED_KEYS
                );
                ps.setString(1, finFileNo);
                ps.setString(2, courtFileNo);
                return ps;
            }, keyHolder);
        } else if ("Defence".equals(caseType)) {
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
        String letterRef = "REF-" + dbType + "-" + System.currentTimeMillis() % 100000;
        jdbcTemplate.update(
            "INSERT INTO letter (letterRef, letterDate, subject, caseID, caseType) VALUES (?, CURDATE(), ?, ?, ?)",
            letterRef, title, newCaseId, dbType
        );

        // 3. Insert into caseamounts
        jdbcTemplate.update(
            "INSERT INTO caseamounts (amount, caseID, balance, caseType) VALUES (?, ?, ?, ?)",
            cleanAmount, newCaseId, cleanAmount, dbType
        );

        // 4. Log Registration Transaction
        jdbcTemplate.update(
            "INSERT INTO transactions (caseID, caseType, officerID, activityID, dateRecorded, transactionDate) VALUES (?, ?, 1, 1, NOW(), CURDATE())",
            newCaseId, dbType
        );

        return ResponseEntity.ok(Map.of(
            "success", true,
            "caseID", caseType.toLowerCase() + "-" + newCaseId,
            "rawID", newCaseId
        ));
    }

    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<?> updateCase(@PathVariable String id, @RequestBody Map<String, Object> body) {
        String[] parts = id.split("-");
        String caseType = parts[0].toLowerCase();
        int rawID = parts.length > 1 ? Integer.parseInt(parts[1]) : Integer.parseInt(parts[0]);
        int dbType = getDbTypeCode(caseType);

        String title = (String) body.get("title");
        String fileNumber = (String) body.get("fileNumber");
        Object amountObj = body.get("amount");

        String tableName = switch (caseType) {
            case "garnishee" -> "garnishee";
            case "defence" -> "defence";
            case "arbitration" -> "arbitration";
            default -> "claim";
        };
        String idColumn = switch (caseType) {
            case "garnishee" -> "garnisheeID";
            case "defence" -> "defenceID";
            case "arbitration" -> "arbitrationID";
            default -> "claimID";
        };

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
            "INSERT INTO transactions (caseID, caseType, officerID, activityID, dateRecorded, transactionDate) VALUES (?, ?, 1, 23, NOW(), CURDATE())",
            rawID, dbType
        );

        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/{id}/allocate")
    public ResponseEntity<?> allocateCase(@PathVariable String id, @RequestBody Map<String, Object> body) {
        String[] parts = id.split("-");
        String caseType = parts[0].toLowerCase();
        int rawID = parts.length > 1 ? Integer.parseInt(parts[1]) : Integer.parseInt(parts[0]);
        int dbType = getDbTypeCode(caseType);

        Object counselIDObj = body.get("counselID");
        Integer counselID = counselIDObj != null && !String.valueOf(counselIDObj).trim().isEmpty()
            ? Integer.parseInt(String.valueOf(counselIDObj))
            : null;

        List<Map<String, Object>> exists = jdbcTemplate.queryForList(
            "SELECT * FROM cldcaseofficer WHERE caseID = ? AND caseType = ?", rawID, dbType
        );

        if (!exists.isEmpty()) {
            if (counselID != null) {
                jdbcTemplate.update("UPDATE cldcaseofficer SET officerID = ? WHERE caseID = ? AND caseType = ?", counselID, rawID, dbType);
            } else {
                jdbcTemplate.update("DELETE FROM cldcaseofficer WHERE caseID = ? AND caseType = ?", rawID, dbType);
            }
        } else if (counselID != null) {
            jdbcTemplate.update("INSERT INTO cldcaseofficer (caseID, officerID, caseType) VALUES (?, ?, ?)", rawID, counselID, dbType);
        }

        jdbcTemplate.update(
            "INSERT INTO transactions (caseID, caseType, officerID, activityID, dateRecorded, transactionDate) VALUES (?, ?, ?, 5, NOW(), CURDATE())",
            rawID, dbType, counselID != null ? counselID : 1
        );

        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/{id}/activities")
    public ResponseEntity<?> addActivity(@PathVariable String id, @RequestBody Map<String, Object> body) {
        String[] parts = id.split("-");
        String caseType = parts[0].toLowerCase();
        int rawID = parts.length > 1 ? Integer.parseInt(parts[1]) : Integer.parseInt(parts[0]);
        int dbType = getDbTypeCode(caseType);

        String type = (String) body.get("type");
        String description = (String) body.get("description");
        String officerName = (String) body.getOrDefault("officerName", "State Counsel");
        Object officerIDObj = body.getOrDefault("officerID", 1);
        int officerID = officerIDObj instanceof Number ? ((Number) officerIDObj).intValue() : 1;

        jdbcTemplate.update(
            "INSERT INTO cld_case_work_logs (caseID, type, description, officerName, officerID) VALUES (?, ?, ?, ?, ?)",
            id, type, description, officerName, officerID
        );

        jdbcTemplate.update(
            "INSERT INTO transactions (caseID, caseType, officerID, activityID, dateRecorded, transactionDate) VALUES (?, ?, ?, 23, NOW(), CURDATE())",
            rawID, dbType, officerID
        );

        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/{id}/bringup")
    public ResponseEntity<?> scheduleBringup(@PathVariable String id, @RequestBody Map<String, Object> body) {
        String[] parts = id.split("-");
        String caseType = parts[0].toLowerCase();
        int rawID = parts.length > 1 ? Integer.parseInt(parts[1]) : Integer.parseInt(parts[0]);
        int dbType = getDbTypeCode(caseType);

        String bringUpDate = (String) body.get("bringUpDate");
        Object officerIDObj = body.get("officerID");
        int officerID = officerIDObj instanceof Number ? ((Number) officerIDObj).intValue() : 1;

        jdbcTemplate.update(
            "INSERT INTO bringup (caseID, caseType, officerID, bringUpDate, collected, recieved, broughtBack) VALUES (?, ?, ?, ?, 0, 0, 0)",
            rawID, dbType, officerID, bringUpDate
        );

        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/{id}/conclude")
    @Transactional
    public ResponseEntity<?> concludeCase(@PathVariable String id, @RequestBody Map<String, Object> body) {
        String[] parts = id.split("-");
        String caseType = parts[0].toLowerCase();
        int rawID = parts.length > 1 ? Integer.parseInt(parts[1]) : Integer.parseInt(parts[0]);
        int dbType = getDbTypeCode(caseType);

        Object verdictIDObj = body.get("verdictID");
        int verdictID = verdictIDObj instanceof Number ? ((Number) verdictIDObj).intValue() : 1;
        String verdictText = (String) body.get("verdictText");
        Object officerIDObj = body.getOrDefault("officerID", 1);
        int officerID = officerIDObj instanceof Number ? ((Number) officerIDObj).intValue() : 1;

        String tableName = switch (caseType) {
            case "garnishee" -> "garnishee";
            case "defence" -> "defence";
            case "arbitration" -> "arbitration";
            default -> "claim";
        };
        String idColumn = switch (caseType) {
            case "garnishee" -> "garnisheeID";
            case "defence" -> "defenceID";
            case "arbitration" -> "arbitrationID";
            default -> "claimID";
        };

        jdbcTemplate.update("UPDATE " + tableName + " SET closed = 1 WHERE " + idColumn + " = ?", rawID);

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                "INSERT INTO transactions (caseID, caseType, officerID, activityID, dateRecorded, transactionDate) VALUES (?, ?, ?, 2, NOW(), CURDATE())",
                Statement.RETURN_GENERATED_KEYS
            );
            ps.setInt(1, rawID);
            ps.setInt(2, dbType);
            ps.setInt(3, officerID);
            return ps;
        }, keyHolder);

        int txID = keyHolder.getKey().intValue();

        jdbcTemplate.update(
            "INSERT INTO caseverdict (caseID, caseType, verdictID, description, transactionID) VALUES (?, ?, ?, ?, ?)",
            rawID, dbType, verdictID, verdictText, txID
        );

        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/{id}/respond")
    public ResponseEntity<?> respondAllocation(@PathVariable String id, @RequestBody Map<String, Object> body) {
        String[] parts = id.split("-");
        String caseType = parts[0].toLowerCase();
        int rawID = parts.length > 1 ? Integer.parseInt(parts[1]) : Integer.parseInt(parts[0]);
        int dbType = getDbTypeCode(caseType);

        String response = (String) body.get("response");
        Object officerIDObj = body.get("officerID");
        int officerID = officerIDObj instanceof Number ? ((Number) officerIDObj).intValue() : 1;

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
                "INSERT INTO notifications (user_id, message, case_id) VALUES (246, ?, ?)",
                "⚠️ Case allocation declined by assigned officer for Case #" + rawID, String.valueOf(rawID)
            );
        }

        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/{id}/sheriff")
    public ResponseEntity<?> assignSheriff(@PathVariable String id, @RequestBody Map<String, Object> body) {
        String[] parts = id.split("-");
        String caseType = parts[0].toLowerCase();
        int rawID = parts.length > 1 ? Integer.parseInt(parts[1]) : Integer.parseInt(parts[0]);
        int dbType = getDbTypeCode(caseType);

        String actionType = (String) body.get("action_type");
        Object sheriffIdObj = body.get("sheriff_id");
        int sheriffId = sheriffIdObj instanceof Number ? ((Number) sheriffIdObj).intValue() : 1;

        String defendantName = (String) body.get("defendant_name");
        String defendantAddress = (String) body.get("defendant_address");
        String notes = (String) body.get("notes");
        String instructions = (String) body.get("instructions");
        String writType = (String) body.get("writ_type");
        Object officerIDObj = body.getOrDefault("officerID", 1);
        int officerID = officerIDObj instanceof Number ? ((Number) officerIDObj).intValue() : 1;

        if ("send_summons".equals(actionType)) {
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbcTemplate.update(con -> {
                PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO summons (case_id, case_type, assigned_by, assigned_sheriff, defendant_name, defendant_address, notes, status) VALUES (?, ?, ?, ?, ?, ?, ?, 'Assigned')",
                    Statement.RETURN_GENERATED_KEYS
                );
                ps.setInt(1, rawID);
                ps.setInt(2, dbType);
                ps.setInt(3, officerID);
                ps.setInt(4, sheriffId);
                ps.setString(5, defendantName != null ? defendantName : "Defendant");
                ps.setString(6, defendantAddress);
                ps.setString(7, notes);
                return ps;
            }, keyHolder);

            String msg = "📜 NEW SUMMONS ASSIGNED. Case: #" + rawID + ", Defendant: " + defendantName + ", Notes: " + notes;
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
                    ps.setInt(3, officerID);
                    ps.setInt(4, sheriffId);
                    ps.setString(5, defendantName != null ? defendantName : "Defendant");
                    return ps;
                }, autoKey);
                summonId = autoKey.getKey().intValue();
            }

            final int finSummonId = summonId;
            jdbcTemplate.update(
                "INSERT INTO writs_of_execution (summon_id, assigned_by, assigned_sheriff, writ_type, instructions, status) VALUES (?, ?, ?, ?, ?, 'Pending')",
                finSummonId, officerID, sheriffId, writType, instructions
            );

            String msg = "⚖️ NEW WRIT OF EXECUTION. Case: #" + rawID + ", Type: " + writType;
            jdbcTemplate.update("INSERT INTO notifications (user_id, message, case_id) VALUES (?, ?, ?)", sheriffId, msg, String.valueOf(rawID));
        }

        jdbcTemplate.update(
            "INSERT INTO transactions (caseID, caseType, officerID, activityID, dateRecorded, transactionDate) VALUES (?, ?, ?, 23, NOW(), CURDATE())",
            rawID, dbType, officerID
        );

        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/{id}/documents")
    public ResponseEntity<?> uploadDocument(@PathVariable String id, @RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "No file uploaded"));
        }

        String[] parts = id.split("-");
        String caseType = parts[0].toLowerCase();
        int rawID = parts.length > 1 ? Integer.parseInt(parts[1]) : Integer.parseInt(parts[0]);
        int dbType = getDbTypeCode(caseType);

        try {
            File dir = new File(uploadDir);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            String filename = System.currentTimeMillis() + "-" + Math.round(Math.random() * 1E9) + "-" + file.getOriginalFilename();
            File dest = new File(dir, filename);
            file.transferTo(dest);

            jdbcTemplate.update(
                "INSERT INTO documents (caseID, caseType, documentClass, name, size, type, uploadDate) VALUES (?, ?, 1, ?, ?, ?, NOW())",
                rawID, dbType, filename, file.getSize(), file.getContentType()
            );

            jdbcTemplate.update(
                "INSERT INTO transactions (caseID, caseType, officerID, activityID, dateRecorded, transactionDate) VALUES (?, ?, 1, 10, NOW(), CURDATE())",
                rawID, dbType
            );

            return ResponseEntity.ok(Map.of("success", true, "name", filename));
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/payments")
    @Transactional
    public ResponseEntity<?> recordPayment(@PathVariable String id, @RequestBody Map<String, Object> body) {
        String[] parts = id.split("-");
        String caseType = parts[0].toLowerCase();
        int rawID = parts.length > 1 ? Integer.parseInt(parts[1]) : Integer.parseInt(parts[0]);
        int dbType = getDbTypeCode(caseType);

        Object amountObj = body.get("paymentAmount");
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
                "INSERT INTO transactions (caseID, caseType, officerID, activityID, dateRecorded, transactionDate) VALUES (?, ?, 1, 16, NOW(), CURDATE())",
                rawID, dbType
            );
        }

        return ResponseEntity.ok(Map.of("success", true));
    }
}

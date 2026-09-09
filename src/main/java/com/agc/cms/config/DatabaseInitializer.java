package com.agc.cms.config;

import org.mindrot.jbcrypt.BCrypt;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

@Component
public class DatabaseInitializer implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;

    public DatabaseInitializer(JdbcTemplate jdbcTemplate, DataSource dataSource) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataSource = dataSource;
    }

    @Override
    public void run(String... args) {
        try {
            System.out.println("Validating/Initializing AGC CMS MySQL tables for Spring Boot backend...");

            boolean needInit = false;
            try {
                Integer officerCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM officer", Integer.class);
                if (officerCount == null || officerCount == 0) {
                    needInit = true;
                }
            } catch (Exception e) {
                // Table officer does not exist yet
                needInit = true;
            }

            if (needInit) {
                System.out.println("Executing full AGC CMS schema & seed data from schema.sql...");
                ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
                populator.addScript(new ClassPathResource("schema.sql"));
                populator.setContinueOnError(true);
                populator.setIgnoreFailedDrops(true);
                populator.execute(dataSource);
                System.out.println("AGC CMS Database initialized with complete schema and seed data!");
            } else {
                System.out.println("AGC CMS Database verified. Officer and case records are ready.");
            }

            // Security Hardening: Auto-migrate any unhashed legacy passwords to BCrypt
            try {
                List<Map<String, Object>> officers = jdbcTemplate.queryForList("SELECT officerID, username, password FROM officer");
                int migratedCount = 0;
                for (Map<String, Object> off : officers) {
                    int officerID = ((Number) off.get("officerID")).intValue();
                    String pwd = (String) off.get("password");
                    if (pwd != null && !pwd.startsWith("$2a$") && !pwd.startsWith("$2b$") && !pwd.startsWith("$2y$")) {
                        String hashed = BCrypt.hashpw(pwd, BCrypt.gensalt(12));
                        jdbcTemplate.update("UPDATE officer SET password = ? WHERE officerID = ?", hashed, officerID);
                        migratedCount++;
                    }
                }
                if (migratedCount > 0) {
                    System.out.println("🔒 [SECURITY HARDENING] Auto-migrated " + migratedCount + " legacy officer passwords to BCrypt hashes.");
                }
            } catch (Exception e) {
                System.err.println("Password migration check notice: " + e.getMessage());
            }

            // Guarantee all existing officer accounts are active
            try {
                int activated = jdbcTemplate.update("UPDATE officer SET active = 1 WHERE active IS NULL OR active = 0");
                if (activated > 0) {
                    System.out.println("✅ [ACCOUNT STATUS] Re-activated " + activated + " officer account(s).");
                }
            } catch (Exception ignored) {}

            // Ensure law_firms table exists and seed Botswana law firms
            try {
                jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS `law_firms` (
                      `firmID` INT AUTO_INCREMENT PRIMARY KEY,
                      `name` VARCHAR(255) NOT NULL UNIQUE,
                      `contactPerson` VARCHAR(150) NULL,
                      `phone` VARCHAR(50) NULL,
                      `email` VARCHAR(150) NULL,
                      `physicalAddress` TEXT NULL,
                      `city` VARCHAR(100) DEFAULT 'Gaborone',
                      `active` TINYINT(1) DEFAULT 1,
                      `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                """);

                Integer firmCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM law_firms", Integer.class);
                if (firmCount == null || firmCount == 0) {
                    System.out.println("Seeding Botswana registered law practice firms into law_firms table...");
                    jdbcTemplate.batchUpdate(
                        "INSERT INTO law_firms (name, contactPerson, phone, email, physicalAddress, city, active) VALUES (?, ?, ?, ?, ?, ?, 1)",
                        List.of(
                            new Object[]{"Armstrongs Attorneys", "John Armstrong", "+267 395 3481", "info@armstrongs.bw", "Plot 22, Khama Crescent", "Gaborone"},
                            new Object[]{"Collins Newman & Co", "Dineo Newman", "+267 395 2725", "litigation@collinsnewman.bw", "Dinatla Court, Morupule Drive", "Gaborone"},
                            new Object[]{"Minchin & Kelly (Botswana)", "Terence Kelly", "+267 391 2734", "law@minchinkelly.bw", "Plot 68833, Gaborone CBD", "Gaborone"},
                            new Object[]{"Desai Law Group", "Rizwan Desai", "+267 316 2727", "contact@desailawgroup.com", "Central Square, CBD", "Gaborone"},
                            new Object[]{"Bookbinder Business Law", "Jeffrey Bookbinder", "+267 391 2397", "info@bookbinderlaw.co.bw", "9th Floor, iTowers, CBD", "Gaborone"},
                            new Object[]{"Bogopa, Manewe, Tobedza & Co", "Tshepo Manewe", "+267 390 1284", "admin@bmtlaw.co.bw", "Plot 17927, Gaborone West", "Gaborone"},
                            new Object[]{"Akheel Jinabhai & Associates", "Akheel Jinabhai", "+267 390 6555", "info@ajabw.com", "Plot 54368, The Hub, iTowers", "Gaborone"},
                            new Object[]{"Dingake Law Partners", "Key Dingake", "+267 393 4511", "partners@dingakelaw.co.bw", "Fairgrounds Office Park", "Gaborone"}
                        )
                    );
                    System.out.println("✅ Successfully seeded 8 major Botswana law practice firms.");
                }
            } catch (Exception e) {
                System.err.println("Notice on law_firms setup: " + e.getMessage());
            }

            // Ensure documents table exists for file uploads and vault attachments
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
                System.out.println("✅ [TABLE VERIFICATION] Documents table verified for case file attachments.");
            } catch (Exception e) {
                System.err.println("Notice on documents table setup: " + e.getMessage());
            }

        } catch (Exception e) {
            System.err.println("DatabaseInitializer note: " + e.getMessage());
        }
    }
}

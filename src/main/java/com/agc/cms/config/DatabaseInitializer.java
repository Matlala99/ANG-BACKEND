package com.agc.cms.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DatabaseInitializer implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    public DatabaseInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        try {
            System.out.println("Validating/Initializing AGC CMS MySQL tables...");

            // 1. Create cld_case_work_logs
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS cld_case_work_logs (
                  logID INT AUTO_INCREMENT PRIMARY KEY,
                  caseID VARCHAR(50) NOT NULL,
                  type VARCHAR(100) NOT NULL,
                  description TEXT NOT NULL,
                  officerName VARCHAR(150) NOT NULL,
                  officerID INT NOT NULL,
                  dateRecorded TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
            """);

            // 2. Create court_diary
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS court_diary (
                  id INT AUTO_INCREMENT PRIMARY KEY,
                  case_id INT NOT NULL,
                  case_type INT NOT NULL,
                  event_type VARCHAR(50) NOT NULL,
                  event_date DATE NOT NULL,
                  event_time TIME NULL,
                  location VARCHAR(255) NULL,
                  description TEXT NULL,
                  assigned_user_id INT NULL,
                  created_by INT NOT NULL,
                  status VARCHAR(20) DEFAULT 'Pending',
                  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
            """);

            // 3. Create articles
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS articles (
                  article_id INT AUTO_INCREMENT PRIMARY KEY,
                  title VARCHAR(255) NOT NULL,
                  content TEXT NOT NULL,
                  author VARCHAR(100) NULL,
                  article_type VARCHAR(50) NOT NULL,
                  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
            """);

            // 4. Create court_links
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS court_links (
                  id INT AUTO_INCREMENT PRIMARY KEY,
                  title VARCHAR(255) NOT NULL,
                  url VARCHAR(255) NOT NULL,
                  category VARCHAR(100) DEFAULT 'General',
                  description TEXT NULL,
                  created_by INT NOT NULL,
                  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
            """);

            // 5. Create summons
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS summons (
                  summon_id INT AUTO_INCREMENT PRIMARY KEY,
                  case_id INT NOT NULL,
                  case_type INT NOT NULL,
                  assigned_by INT NOT NULL,
                  assigned_sheriff INT NOT NULL,
                  defendant_name VARCHAR(150) NOT NULL,
                  defendant_address TEXT NULL,
                  notes TEXT NULL,
                  status VARCHAR(20) DEFAULT 'Assigned',
                  assigned_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                  served_at TIMESTAMP NULL
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
            """);

            // 6. Create writs_of_execution
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS writs_of_execution (
                  writ_id INT AUTO_INCREMENT PRIMARY KEY,
                  summon_id INT NOT NULL,
                  assigned_by INT NOT NULL,
                  assigned_sheriff INT NOT NULL,
                  writ_type VARCHAR(50) NOT NULL,
                  instructions TEXT NULL,
                  status VARCHAR(20) DEFAULT 'Pending',
                  issued_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                  executed_at TIMESTAMP NULL
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
            """);

            // 7. Create notifications
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS notifications (
                  notification_id INT AUTO_INCREMENT PRIMARY KEY,
                  user_id INT NOT NULL,
                  message TEXT NOT NULL,
                  case_id VARCHAR(50) NULL,
                  is_read TINYINT(1) DEFAULT 0,
                  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
            """);

            try { jdbcTemplate.execute("ALTER TABLE notifications ADD COLUMN user_id INT NULL"); } catch (Exception ignored) {}
            try { jdbcTemplate.execute("ALTER TABLE notifications ADD COLUMN case_id VARCHAR(50) NULL"); } catch (Exception ignored) {}
            try { jdbcTemplate.execute("ALTER TABLE notifications ADD COLUMN is_read TINYINT(1) DEFAULT 0"); } catch (Exception ignored) {}
            try { jdbcTemplate.execute("ALTER TABLE notifications ADD COLUMN created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP"); } catch (Exception ignored) {}

            // Seed court_links if empty
            Integer linksCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM court_links", Integer.class);
            if (linksCount != null && linksCount == 0) {
                jdbcTemplate.execute("""
                    INSERT INTO court_links (title, url, category, description, created_by) VALUES
                    ('Botswana e-Laws Portal', 'https://www.elaws.gov.bw/', 'Statutes & Legislation', 'Official database of Botswana Acts and statutory instruments.', 1),
                    ('High Court Rulings', 'https://www.botswanalaws.com/court-judgments', 'Judgments & Rulings', 'Collection of searchable High Court and Court of Appeal judgments.', 1),
                    ('Industrial Court Botswana', 'http://www.gov.bw/en/Ministries--Authorities/Ministries/Administration-of-Justice/Industrial-Court1/', 'Court Portals', 'Official portal of the Botswana Industrial Court.', 1),
                    ('Gaborone Magistrate Court', 'https://www.gov.bw/en/Ministries--Authorities/Ministries/Administration-of-Justice/Magistrates-Courts/', 'Court Portals', 'Gaborone regional magistrate information and guidelines.', 1)
                """);
            }

            // Seed articles if empty
            Integer articlesCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM articles", Integer.class);
            if (articlesCount != null && articlesCount == 0) {
                jdbcTemplate.execute("""
                    INSERT INTO articles (title, content, author, article_type) VALUES
                    ('Understanding Salary Overpayment Recovery Protocols', 'This article outlines the civil division legal guidelines for recovering public funds disbursed in excess of statutory entitlement. Under the State Proceedings Act, recovery demands must be preceded by a formal audit reconciliation statement and a 30-day notice of intended civil litigation. Defences of change of position are scrutinized under Botswana common law precedents.', 'Deputy Attorney General', 'Salary Overpayment'),
                    ('Loan Repayment Enforcement Guidelines under the LMS', 'Step-by-step procedure for handling defaults on government employee loans. The division must verify that the loan agreement was executed under the finance act, and recovery steps should prioritize payroll deductions (salary earmarks) before initiating high court writ attachment procedures. If the employee has exited service, the civil suit must name both the employee and their guarantors.', 'Principal Counsel', 'Loan Repayment'),
                    ('Eviction of Illegal Occupants from State Land', 'Summary of evictions sequence. Counsel must draft notice of motion for recovery of land under the Land Control Act. Demolition warrants require a specific court decree from the Land Tribunal or the High Court. Local sheriffs must carry out execution in coordination with regional state land officers.', 'Senior Counsel', 'Eviction')
                """);
            }

            System.out.println("AGC CMS Database initialization completed successfully!");

        } catch (Exception e) {
            System.err.println("Error initializing database tables: " + e.getMessage());
        }
    }
}

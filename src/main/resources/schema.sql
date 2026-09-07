-- ====================================================================
-- Attorney General Chambers (AGC) - Civil Litigation Division
-- Comprehensive Database Schema & Seed Data Script
-- Database: agc_cms
-- Target Engines: MySQL 8.x / MariaDB / Spring Boot 3.x / Express.js
-- ====================================================================

CREATE DATABASE IF NOT EXISTS `agc_cms` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `agc_cms`;

SET FOREIGN_KEY_CHECKS = 0;

-- --------------------------------------------------------------------
-- 1. TABLE: person
-- Physical identity records for all officers, plaintiffs, and defendants
-- --------------------------------------------------------------------
DROP TABLE IF EXISTS `person`;
CREATE TABLE `person` (
  `personID` INT AUTO_INCREMENT PRIMARY KEY,
  `firstName` VARCHAR(100) NOT NULL,
  `surname` VARCHAR(100) NOT NULL,
  `idNumber` VARCHAR(50) NULL,
  `phone` VARCHAR(50) NULL,
  `email` VARCHAR(150) NULL,
  `address` TEXT NULL,
  `gender` VARCHAR(10) NULL,
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- --------------------------------------------------------------------
-- 2. TABLE: officer
-- User credentials and system access roles
-- User Types: 1=Administrator, 2=Registry Records, 3=Sheriff, 4=Allocating Officer, 7=State Counsel
-- --------------------------------------------------------------------
DROP TABLE IF EXISTS `officer`;
CREATE TABLE `officer` (
  `officerID` INT PRIMARY KEY,
  `username` VARCHAR(100) NOT NULL UNIQUE,
  `password` VARCHAR(255) NOT NULL,
  `userType` INT NOT NULL,
  `email` VARCHAR(150) NULL,
  `active` TINYINT(1) DEFAULT 1,
  `last_login` TIMESTAMP NULL DEFAULT NULL,
  `last_logout` TIMESTAMP NULL DEFAULT NULL,
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- --------------------------------------------------------------------
-- 3. TABLE: ministries
-- Originating Government Ministries & Departments
-- --------------------------------------------------------------------
DROP TABLE IF EXISTS `ministries`;
CREATE TABLE `ministries` (
  `ministryID` INT AUTO_INCREMENT PRIMARY KEY,
  `name` VARCHAR(255) NOT NULL,
  `code` VARCHAR(50) NULL,
  `contactPerson` VARCHAR(150) NULL,
  `email` VARCHAR(150) NULL,
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- --------------------------------------------------------------------
-- 4. TABLE: caseactivity
-- Lookup table for system audit & activity logging
-- --------------------------------------------------------------------
DROP TABLE IF EXISTS `caseactivity`;
CREATE TABLE `caseactivity` (
  `activityID` INT PRIMARY KEY,
  `description` VARCHAR(255) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- --------------------------------------------------------------------
-- 5. TABLE: claim (Case Type: 10)
-- Civil litigation recovery claims initiated by the State
-- --------------------------------------------------------------------
DROP TABLE IF EXISTS `claim`;
CREATE TABLE `claim` (
  `claimID` INT AUTO_INCREMENT PRIMARY KEY,
  `dateRecieved` DATE NOT NULL,
  `fileNumber` VARCHAR(100) NOT NULL,
  `locationID` INT DEFAULT 1,
  `closed` TINYINT(1) DEFAULT 0,
  `preClosed` TINYINT(1) DEFAULT 0,
  `subdivision` INT DEFAULT 1,
  `instanceID` INT DEFAULT 1,
  `ministryID` INT NULL,
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- --------------------------------------------------------------------
-- 6. TABLE: garnishee (Case Type: 9)
-- Garnishee proceedings and attachment of debts
-- --------------------------------------------------------------------
DROP TABLE IF EXISTS `garnishee`;
CREATE TABLE `garnishee` (
  `garnisheeID` INT AUTO_INCREMENT PRIMARY KEY,
  `dateRecieved` DATE NOT NULL,
  `fileNumber` VARCHAR(100) NOT NULL,
  `courtFileNumber` VARCHAR(100) NULL,
  `caseInstanceID` INT DEFAULT 1,
  `sourceType` INT DEFAULT 1,
  `sourceID` INT DEFAULT 1,
  `subdivision` INT DEFAULT 1,
  `locationID` INT DEFAULT 1,
  `closed` TINYINT(1) DEFAULT 0,
  `preClosed` TINYINT(1) DEFAULT 0,
  `installment` DECIMAL(15,2) DEFAULT 0.00,
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- --------------------------------------------------------------------
-- 7. TABLE: defence (Case Type: 11 / 12)
-- Civil defence suits defended on behalf of the Attorney General
-- --------------------------------------------------------------------
DROP TABLE IF EXISTS `defence`;
CREATE TABLE `defence` (
  `defenceID` INT AUTO_INCREMENT PRIMARY KEY,
  `dateRecieved` DATE NOT NULL,
  `fileNumber` VARCHAR(100) NOT NULL,
  `locationID` INT DEFAULT 1,
  `closed` TINYINT(1) DEFAULT 0,
  `preClosed` TINYINT(1) DEFAULT 0,
  `subdivision` INT DEFAULT 1,
  `instanceID` INT DEFAULT 1,
  `defenceType` INT DEFAULT 1,
  `natureType` INT DEFAULT 1,
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- --------------------------------------------------------------------
-- 8. TABLE: arbitration (Case Type: 13)
-- Commercial arbitration & alternative dispute resolution cases
-- --------------------------------------------------------------------
DROP TABLE IF EXISTS `arbitration`;
CREATE TABLE `arbitration` (
  `arbitrationID` INT AUTO_INCREMENT PRIMARY KEY,
  `dateRecieved` DATE NOT NULL,
  `fileNumber` VARCHAR(100) NOT NULL,
  `locationID` INT DEFAULT 1,
  `closed` TINYINT(1) DEFAULT 0,
  `preClosed` TINYINT(1) DEFAULT 0,
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- --------------------------------------------------------------------
-- 9. TABLE: letter
-- Case titles, pleadings summary, and originating registry letters
-- --------------------------------------------------------------------
DROP TABLE IF EXISTS `letter`;
CREATE TABLE `letter` (
  `letterID` INT AUTO_INCREMENT PRIMARY KEY,
  `letterRef` VARCHAR(100) NOT NULL,
  `letterDate` DATE NOT NULL,
  `subject` TEXT NOT NULL,
  `caseID` INT NOT NULL,
  `caseType` INT NOT NULL,
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- --------------------------------------------------------------------
-- 10. TABLE: caseamounts
-- Financial debt costs, amounts collected, balances & payment tracking
-- --------------------------------------------------------------------
DROP TABLE IF EXISTS `caseamounts`;
CREATE TABLE `caseamounts` (
  `caseAmountID` INT AUTO_INCREMENT PRIMARY KEY,
  `amount` DECIMAL(15,2) DEFAULT 0.00,
  `installment` DECIMAL(15,2) DEFAULT 0.00,
  `balance` DECIMAL(15,2) DEFAULT 0.00,
  `lastPaymentDate` DATETIME NULL,
  `caseID` INT NOT NULL,
  `caseType` INT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- --------------------------------------------------------------------
-- 11. TABLE: cldcaseofficer
-- Allocation and acknowledgment of case files to State Counsel
-- --------------------------------------------------------------------
DROP TABLE IF EXISTS `cldcaseofficer`;
CREATE TABLE `cldcaseofficer` (
  `cldCaseOfficerID` INT AUTO_INCREMENT PRIMARY KEY,
  `caseID` INT NOT NULL,
  `officerID` INT NOT NULL,
  `caseType` INT NOT NULL,
  `recieved` TINYINT(1) DEFAULT 0,
  `recieptDate` DATETIME NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- --------------------------------------------------------------------
-- 12. TABLE: cld_case_work_logs
-- Counsel workspace notes, pleadings filing logs, hearing reports
-- --------------------------------------------------------------------
DROP TABLE IF EXISTS `cld_case_work_logs`;
CREATE TABLE `cld_case_work_logs` (
  `logID` INT AUTO_INCREMENT PRIMARY KEY,
  `caseID` VARCHAR(50) NOT NULL,
  `type` VARCHAR(100) NOT NULL,
  `description` TEXT NOT NULL,
  `officerName` VARCHAR(150) NOT NULL,
  `officerID` INT NOT NULL,
  `dateRecorded` TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- --------------------------------------------------------------------
-- 13. TABLE: transactions
-- Audit trail ledger for registrations, allocations, status changes
-- --------------------------------------------------------------------
DROP TABLE IF EXISTS `transactions`;
CREATE TABLE `transactions` (
  `transactionID` INT AUTO_INCREMENT PRIMARY KEY,
  `caseID` INT NOT NULL,
  `caseType` INT NOT NULL,
  `officerID` INT NOT NULL,
  `activityID` INT NOT NULL,
  `dateRecorded` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  `transactionDate` DATE NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- --------------------------------------------------------------------
-- 14. TABLE: bringup
-- Registry physical case file tickler and movement ledger
-- --------------------------------------------------------------------
DROP TABLE IF EXISTS `bringup`;
CREATE TABLE `bringup` (
  `bringupID` INT AUTO_INCREMENT PRIMARY KEY,
  `caseID` INT NOT NULL,
  `caseType` INT NOT NULL,
  `officerID` INT NOT NULL,
  `bringUpDate` DATE NOT NULL,
  `collected` TINYINT(1) DEFAULT 0,
  `recieved` TINYINT(1) DEFAULT 0,
  `broughtBack` TINYINT(1) DEFAULT 0,
  `recieptDate` DATE NULL,
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- --------------------------------------------------------------------
-- 15. TABLE: documents
-- Case files vault, pleadings attachments, judgments PDFs
-- --------------------------------------------------------------------
DROP TABLE IF EXISTS `documents`;
CREATE TABLE `documents` (
  `documentID` INT AUTO_INCREMENT PRIMARY KEY,
  `caseID` INT NOT NULL,
  `caseType` INT NOT NULL,
  `documentClass` INT DEFAULT 1,
  `name` VARCHAR(255) NOT NULL,
  `size` BIGINT DEFAULT 0,
  `type` VARCHAR(100) DEFAULT 'application/pdf',
  `uploadDate` TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- --------------------------------------------------------------------
-- 16. TABLE: caseverdict
-- Judgment verdicts on concluded / archived litigation matters
-- --------------------------------------------------------------------
DROP TABLE IF EXISTS `caseverdict`;
CREATE TABLE `caseverdict` (
  `caseVerdictID` INT AUTO_INCREMENT PRIMARY KEY,
  `caseID` INT NOT NULL,
  `caseType` INT NOT NULL,
  `verdictID` VARCHAR(50) NOT NULL,
  `description` TEXT NOT NULL,
  `transactionID` INT NOT NULL,
  `concludedDate` TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- --------------------------------------------------------------------
-- 17. TABLE: notifications
-- Notification alerts feed for case assignments and sheriff actions
-- --------------------------------------------------------------------
DROP TABLE IF EXISTS `notifications`;
CREATE TABLE `notifications` (
  `notification_id` INT AUTO_INCREMENT PRIMARY KEY,
  `user_id` INT NOT NULL,
  `message` TEXT NOT NULL,
  `case_id` VARCHAR(50) NULL,
  `is_read` TINYINT(1) DEFAULT 0,
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- --------------------------------------------------------------------
-- 18. TABLE: summons
-- Sheriff summons process delivery tracking
-- --------------------------------------------------------------------
DROP TABLE IF EXISTS `summons`;
CREATE TABLE `summons` (
  `summon_id` INT AUTO_INCREMENT PRIMARY KEY,
  `case_id` INT NOT NULL,
  `case_type` INT NOT NULL,
  `assigned_by` INT NOT NULL,
  `assigned_sheriff` INT NOT NULL,
  `defendant_name` VARCHAR(150) NOT NULL,
  `defendant_address` TEXT NULL,
  `notes` TEXT NULL,
  `status` VARCHAR(20) DEFAULT 'Assigned',
  `assigned_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  `served_at` TIMESTAMP NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- --------------------------------------------------------------------
-- 19. TABLE: writs_of_execution
-- Sheriff writ attachment and legal execution warrants
-- --------------------------------------------------------------------
DROP TABLE IF EXISTS `writs_of_execution`;
CREATE TABLE `writs_of_execution` (
  `writ_id` INT AUTO_INCREMENT PRIMARY KEY,
  `summon_id` INT NOT NULL,
  `assigned_by` INT NOT NULL,
  `assigned_sheriff` INT NOT NULL,
  `writ_type` VARCHAR(50) NOT NULL,
  `instructions` TEXT NULL,
  `status` VARCHAR(20) DEFAULT 'Pending',
  `issued_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  `executed_at` TIMESTAMP NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- --------------------------------------------------------------------
-- 20. TABLE: court_diary
-- Court appearances, motion roll schedules, and appointments
-- --------------------------------------------------------------------
DROP TABLE IF EXISTS `court_diary`;
CREATE TABLE `court_diary` (
  `id` INT AUTO_INCREMENT PRIMARY KEY,
  `case_id` INT NOT NULL,
  `case_type` INT NOT NULL,
  `event_type` VARCHAR(50) NOT NULL,
  `event_date` DATE NOT NULL,
  `event_time` TIME NULL,
  `location` VARCHAR(255) NULL,
  `description` TEXT NULL,
  `assigned_user_id` INT NULL,
  `created_by` INT NOT NULL,
  `status` VARCHAR(20) DEFAULT 'Pending',
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- --------------------------------------------------------------------
-- 21. TABLE: articles
-- Claims repository knowledge base and legal research articles
-- --------------------------------------------------------------------
DROP TABLE IF EXISTS `articles`;
CREATE TABLE `articles` (
  `article_id` INT AUTO_INCREMENT PRIMARY KEY,
  `title` VARCHAR(255) NOT NULL,
  `content` TEXT NOT NULL,
  `author` VARCHAR(100) NULL,
  `article_type` VARCHAR(50) NOT NULL,
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- --------------------------------------------------------------------
-- 22. TABLE: court_links
-- Legal links and external portals directory
-- --------------------------------------------------------------------
DROP TABLE IF EXISTS `court_links`;
CREATE TABLE `court_links` (
  `id` INT AUTO_INCREMENT PRIMARY KEY,
  `title` VARCHAR(255) NOT NULL,
  `url` VARCHAR(255) NOT NULL,
  `category` VARCHAR(100) DEFAULT 'General',
  `description` TEXT NULL,
  `created_by` INT NOT NULL,
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SET FOREIGN_KEY_CHECKS = 1;


-- ====================================================================
-- SEED DATA POPULATION
-- ====================================================================

-- 1. Standard Case Activities Lookup
INSERT INTO `caseactivity` (`activityID`, `description`) VALUES
(1, 'Registration of Matter'),
(2, 'Concluded and Closed'),
(4, 'Allocation Accepted by Counsel'),
(5, 'Matter Allocated to Counsel'),
(10, 'Document / Pleadings Filed'),
(16, 'Claim Payment Received'),
(23, 'Counsel Workspace Log / Sheriff Action'),
(24, 'Allocation Declined by Counsel');

-- 2. Government Ministries
INSERT INTO `ministries` (`ministryID`, `name`, `code`, `contactPerson`, `email`) VALUES
(1, 'Ministry of Justice', 'MOJ', 'Permanent Secretary', 'justice@gov.bw'),
(2, 'Ministry of Finance and Economic Development', 'MFED', 'Accountant General', 'finance@gov.bw'),
(3, 'Ministry of Lands and Water Affairs', 'MLWA', 'Director of Lands', 'lands@gov.bw'),
(4, 'Ministry of Health', 'MOH', 'Director of Clinical Services', 'health@gov.bw'),
(5, 'Ministry of Transport and Public Works', 'MTPW', 'Chief Roads Engineer', 'transport@gov.bw'),
(6, 'Ministry of Education and Skills Development', 'MESD', 'Director of Basic Education', 'education@gov.bw');

-- 3. Persons (Officers & Sandbox Identities)
INSERT INTO `person` (`personID`, `firstName`, `surname`, `idNumber`, `phone`, `email`, `address`, `gender`) VALUES
(1, 'Gomolemo', 'Ncube', '10992384', '+267 361 3600', 'gncube@gov.bw', 'Government Enclave, Gaborone', 'M'),
(2, 'Kabo', 'Olome', '20881923', '+267 361 3620', 'komolome@gov.bw', 'Central Registry Desk, AGC Gaborone', 'M'),
(3, 'Tshepo', 'Molefe', '989917504', '+267 721 0045', '989917504@gov.bw', 'High Court Sheriff Division, Gaborone', 'M'),
(4, 'Lesego', 'Kgosiemang', '30449102', '+267 361 3650', 'lkgs@gov.bw', 'Litigation Command, AGC Gaborone', 'F'),
(5, 'Philemon', 'Musindo', '40558192', '+267 361 3680', 'pmusindo@gov.bw', 'Civil Litigation Chambers, Gaborone', 'M'),
(6, 'Nthabiseng', 'Matlala', '50667283', '+267 361 3685', 'nmatlala@gov.bw', 'Civil Litigation Chambers, Gaborone', 'F'),
(7, 'Sarah', 'Dube', '60778394', '+267 361 3688', 'sdube@gov.bw', 'Civil Litigation Chambers, Gaborone', 'F');

-- 4. Officers Credentials (Password 'root' securely stored as BCrypt hash)
INSERT INTO `officer` (`officerID`, `username`, `password`, `userType`, `email`, `active`, `last_login`) VALUES
(1, 'gncube', '$2a$12$4v041lYc1i48Ff5qYQ7F.Og7m0.Wj1cK9Zz0u1x2y3z4a5b6c7d8e', 1, 'gncube@gov.bw', 1, NOW()),
(2, 'komolome', '$2a$12$4v041lYc1i48Ff5qYQ7F.Og7m0.Wj1cK9Zz0u1x2y3z4a5b6c7d8e', 2, 'komolome@gov.bw', 1, NOW()),
(3, '989917504', '$2a$12$4v041lYc1i48Ff5qYQ7F.Og7m0.Wj1cK9Zz0u1x2y3z4a5b6c7d8e', 3, '989917504@gov.bw', 1, NOW()),
(4, 'allocating_officer', '$2a$12$4v041lYc1i48Ff5qYQ7F.Og7m0.Wj1cK9Zz0u1x2y3z4a5b6c7d8e', 4, 'lkgs@gov.bw', 1, NOW()),
(5, 'pmusindo', '$2a$12$4v041lYc1i48Ff5qYQ7F.Og7m0.Wj1cK9Zz0u1x2y3z4a5b6c7d8e', 7, 'pmusindo@gov.bw', 1, NOW()),
(6, 'nmatlala', '$2a$12$4v041lYc1i48Ff5qYQ7F.Og7m0.Wj1cK9Zz0u1x2y3z4a5b6c7d8e', 7, 'nmatlala@gov.bw', 1, NOW()),
(7, 'sdube', '$2a$12$4v041lYc1i48Ff5qYQ7F.Og7m0.Wj1cK9Zz0u1x2y3z4a5b6c7d8e', 7, 'sdube@gov.bw', 1, NOW());

-- 5. Cases: Claims (Case Type: 10)
INSERT INTO `claim` (`claimID`, `dateRecieved`, `fileNumber`, `locationID`, `closed`, `preClosed`, `subdivision`, `instanceID`, `ministryID`) VALUES
(1, '2025-01-15', 'CLD/CLM/2025/001', 1, 0, 0, 1, 1, 2),
(2, '2025-02-10', 'CLD/CLM/2025/002', 1, 0, 0, 1, 1, 6),
(3, '2025-03-05', 'CLD/CLM/2025/003', 1, 0, 0, 1, 1, 1),
(4, '2025-04-12', 'CLD/CLM/2025/004', 1, 1, 1, 1, 1, 5),
(5, '2025-05-20', 'CLD/CLM/2025/005', 1, 0, 0, 1, 1, 4);

-- 6. Cases: Garnishees (Case Type: 9)
INSERT INTO `garnishee` (`garnisheeID`, `dateRecieved`, `fileNumber`, `courtFileNumber`, `caseInstanceID`, `sourceType`, `sourceID`, `subdivision`, `locationID`, `closed`, `preClosed`, `installment`) VALUES
(1, '2025-01-20', 'CLD/GAR/2025/001', 'CV-GB-2025-012', 1, 1, 1, 1, 1, 0, 0, 1500.00),
(2, '2025-02-18', 'CLD/GAR/2025/002', 'CV-GB-2025-045', 1, 1, 1, 1, 1, 0, 0, 2200.00);

-- 7. Cases: Defences (Case Type: 11)
INSERT INTO `defence` (`defenceID`, `dateRecieved`, `fileNumber`, `locationID`, `closed`, `preClosed`, `subdivision`, `instanceID`, `defenceType`, `natureType`) VALUES
(1, '2025-01-28', 'CLD/DEF/2025/001', 1, 0, 0, 1, 1, 1, 1),
(2, '2025-03-14', 'CLD/DEF/2025/002', 1, 0, 0, 1, 1, 1, 1);

-- 8. Cases: Arbitrations (Case Type: 13)
INSERT INTO `arbitration` (`arbitrationID`, `dateRecieved`, `fileNumber`, `locationID`, `closed`, `preClosed`) VALUES
(1, '2025-02-25', 'CLD/ARB/2025/001', 1, 0, 0);

-- 9. Letters (Case Titles & Subjects)
INSERT INTO `letter` (`letterID`, `letterRef`, `letterDate`, `subject`, `caseID`, `caseType`) VALUES
(1, 'REF-10-001', '2025-01-15', 'Attorney General vs. Kalahari Construction Ltd (Advance Recovery)', 1, 10),
(2, 'REF-10-002', '2025-02-10', 'Attorney General vs. K. Motsumi (Salary Overpayment Recovery)', 2, 10),
(3, 'REF-10-003', '2025-03-05', 'Attorney General vs. Delta Logistics (Vehicle Fleet Lease Breach)', 3, 10),
(4, 'REF-10-004', '2025-04-12', 'Attorney General vs. Apex Road Contractors (Liquidated Damages)', 4, 10),
(5, 'REF-10-005', '2025-05-20', 'Attorney General vs. Medical Suppliers Botswana (Contract Default)', 5, 10),
(6, 'REF-9-001', '2025-01-20', 'In Re: Garnishee Order - B. Kebiditswe (Absa Bank)', 1, 9),
(7, 'REF-9-002', '2025-02-18', 'In Re: Garnishee Attachment - T. Kgosi (First National Bank)', 2, 9),
(8, 'REF-11-001', '2025-01-28', 'Makgabaneng Transport vs. Ministry of Transport & Attorney General', 1, 11),
(9, 'REF-11-002', '2025-03-14', 'Dr. J. Molebatsi vs. Ministry of Health & Attorney General', 2, 11),
(10, 'REF-13-001', '2025-02-25', 'State vs. Southern Solar Power Consortium (Arbitration)', 1, 13);

-- 10. Financial Amounts
INSERT INTO `caseamounts` (`amount`, `installment`, `balance`, `lastPaymentDate`, `caseID`, `caseType`) VALUES
(850000.00, 350000.00, 500000.00, '2025-06-10 10:30:00', 1, 10),
(125000.00, 45000.00, 80000.00, '2025-05-22 14:15:00', 2, 10),
(420000.00, 0.00, 420000.00, NULL, 3, 10),
(650000.00, 650000.00, 0.00, '2025-04-30 09:00:00', 4, 10),
(1200000.00, 300000.00, 900000.00, '2025-06-01 11:00:00', 5, 10),
(85000.00, 15000.00, 70000.00, '2025-05-15 16:00:00', 1, 9),
(110000.00, 22000.00, 88000.00, '2025-06-05 12:00:00', 2, 9),
(500000.00, 0.00, 500000.00, NULL, 1, 11),
(750000.00, 0.00, 750000.00, NULL, 2, 11),
(2500000.00, 500000.00, 2000000.00, '2025-04-10 15:30:00', 1, 13);

-- 11. Counsel Allocation
INSERT INTO `cldcaseofficer` (`caseID`, `officerID`, `caseType`, `recieved`, `recieptDate`) VALUES
(1, 5, 10, 1, '2025-01-16 09:00:00'),
(2, 5, 10, 1, '2025-02-11 10:00:00'),
(3, 5, 10, 0, NULL),
(4, 6, 10, 1, '2025-04-13 08:30:00'),
(5, 6, 10, 1, '2025-05-21 11:00:00'),
(1, 5, 9, 1, '2025-01-21 14:00:00'),
(2, 6, 9, 1, '2025-02-19 09:30:00'),
(1, 5, 11, 1, '2025-01-29 10:00:00'),
(2, 7, 11, 1, '2025-03-15 14:00:00'),
(1, 6, 13, 1, '2025-02-26 11:30:00');

-- 12. Case Work Logs
INSERT INTO `cld_case_work_logs` (`caseID`, `type`, `description`, `officerName`, `officerID`, `dateRecorded`) VALUES
('claims-1', 'Court Hearing', 'Argued summary judgment application in High Court Courtroom 3 before Hon. Justice Dingake. Matter reserved for ruling.', 'Philemon Musindo', 5, '2025-05-18 10:00:00'),
('claims-1', 'Filing Document', 'Filed Notice of Motion and Answering Affidavit at the High Court registry.', 'Philemon Musindo', 5, '2025-03-10 14:30:00'),
('claims-2', 'Meeting', 'Client consultation with Accountant General reconciliation auditors on salary overpayment records.', 'Philemon Musindo', 5, '2025-04-05 11:00:00'),
('defence-1', 'Court Hearing', 'Pre-trial conference completed. Trial dates set for October 14-16, 2026.', 'Philemon Musindo', 5, '2025-05-25 09:00:00');

-- 13. Transactions
INSERT INTO `transactions` (`caseID`, `caseType`, `officerID`, `activityID`, `dateRecorded`, `transactionDate`) VALUES
(1, 10, 1, 1, '2025-01-15 08:30:00', '2025-01-15'),
(1, 10, 5, 4, '2025-01-16 09:00:00', '2025-01-16'),
(1, 10, 5, 16, '2025-06-10 10:30:00', '2025-06-10'),
(2, 10, 1, 1, '2025-02-10 10:00:00', '2025-02-10'),
(4, 10, 6, 2, '2025-04-30 16:00:00', '2025-04-30');

-- 14. File Bringups
INSERT INTO `bringup` (`caseID`, `caseType`, `officerID`, `bringUpDate`, `collected`, `recieved`, `broughtBack`, `recieptDate`) VALUES
(1, 10, 5, '2026-08-25', 1, 1, 0, '2026-08-18'),
(2, 10, 5, '2026-08-28', 1, 0, 0, NULL),
(3, 10, 5, '2026-09-02', 0, 0, 0, NULL),
(1, 11, 5, '2026-09-10', 1, 1, 1, '2026-07-20');

-- 15. Court Diary
INSERT INTO `court_diary` (`case_id`, `case_type`, `event_type`, `event_date`, `event_time`, `location`, `description`, `assigned_user_id`, `created_by`, `status`) VALUES
(1, 10, 'Court Date', '2026-08-25', '09:00:00', 'High Court Gaborone Courtroom 3', 'Hearing of summary judgment application', 5, 1, 'Pending'),
(2, 10, 'Meeting', '2026-08-28', '11:00:00', 'Chambers Conference Room A', 'Pre-trial meeting with Auditor General team', 5, 1, 'Pending'),
(1, 11, 'Court Date', '2026-09-15', '09:30:00', 'High Court Lobatse Courtroom 1', 'Roll call and trial directions', 5, 1, 'Pending'),
(1, 13, 'Meeting', '2026-09-20', '14:00:00', 'CADER Arbitration Secretariat', 'Preliminary arbitration hearing', 6, 1, 'Pending');

-- 16. Sheriff Summons & Writs
INSERT INTO `summons` (`summon_id`, `case_id`, `case_type`, `assigned_by`, `assigned_sheriff`, `defendant_name`, `defendant_address`, `notes`, `status`, `assigned_at`, `served_at`) VALUES
(1, 1, 10, 5, 3, 'Kalahari Construction Managing Director', 'Plot 2044, Gaborone West Industrial', 'Urgent service requested prior to court call date.', 'Pending', NOW(), NULL),
(2, 2, 10, 5, 3, 'K. Motsumi', 'Plot 5541, Broadhurst Extension 20', 'Summons for civil recovery of salary overpayment.', 'Served', '2025-05-10 10:00:00', '2025-05-14 14:20:00');

INSERT INTO `writs_of_execution` (`writ_id`, `summon_id`, `assigned_by`, `assigned_sheriff`, `writ_type`, `instructions`, `status`, `issued_at`, `executed_at`) VALUES
(1, 2, 5, 3, 'Writ of Attachment', 'Attach movable properties to satisfy decree of BWP 80,000.', 'Pending', NOW(), NULL),
(2, 1, 5, 3, 'Writ of Execution', 'Attach commercial bank accounts per High Court order.', 'Executed', '2025-04-10 09:00:00', '2025-04-22 15:30:00');

-- 17. Notifications
INSERT INTO `notifications` (`user_id`, `message`, `case_id`, `is_read`) VALUES
(3, '📜 NEW SUMMONS ASSIGNED. Case: #1, Defendant: Kalahari Construction Managing Director', '1', 0),
(5, '⚖️ Writ of execution for case #2 has been recorded by Sheriff.', '2', 0),
(5, '📂 Matter CLD/CLM/2025/003 has been allocated to you by Allocating Officer.', '3', 0),
(1, '⚠️ Case allocation acknowledged by Counselor Philemon Musindo.', '1', 1);

-- 18. Claims Library Research Articles
INSERT INTO `articles` (`title`, `content`, `author`, `article_type`) VALUES
('Understanding Salary Overpayment Recovery Protocols', 'This article outlines the civil division legal guidelines for recovering public funds disbursed in excess of statutory entitlement. Under the State Proceedings Act, recovery demands must be preceded by a formal audit reconciliation statement and a 30-day notice of intended civil litigation. Defences of change of position are scrutinized under Botswana common law precedents.', 'Deputy Attorney General', 'Salary Overpayment'),
('Loan Repayment Enforcement Guidelines under the LMS', 'Step-by-step procedure for handling defaults on government employee loans. The division must verify that the loan agreement was executed under the finance act, and recovery steps should prioritize payroll deductions (salary earmarks) before initiating high court writ attachment procedures. If the employee has exited service, the civil suit must name both the employee and their guarantors.', 'Principal Counsel', 'Loan Repayment'),
('Eviction of Illegal Occupants from State Land', 'Summary of evictions sequence. Counsel must draft notice of motion for recovery of land under the Land Control Act. Demolition warrants require a specific court decree from the Land Tribunal or the High Court. Local sheriffs must carry out execution in coordination with regional state land officers.', 'Senior Counsel', 'Eviction'),
('State Proceedings and Tender Dispute Injunctions', 'Litigation manual on defending urgent interlocutory applications against the Public Procurement Regulatory Authority (PPRA). The High Court requires evidence of prima facie right, well-grounded apprehension of irreparable harm, and lack of alternative satisfactory remedy.', 'Senior State Counsel', 'Contract Breach'),
('Constitutional Challenges to Legislative Enactments', 'Guidance on defending constitutional validity of statutory instruments. State Counsel must file answering affidavits citing Section 86 of the Constitution of Botswana and provide policy justifications from parent ministries.', 'Deputy Attorney General', 'Constitutional Law');

-- 19. Court & Legal Links Directory
INSERT INTO `court_links` (`title`, `url`, `category`, `description`, `created_by`) VALUES
('Botswana e-Laws Portal', 'https://www.elaws.gov.bw/', 'Statutes & Legislation', 'Official database of Botswana Acts and statutory instruments.', 1),
('High Court Rulings', 'https://www.botswanalaws.com/court-judgments', 'Judgments & Rulings', 'Collection of searchable High Court and Court of Appeal judgments.', 1),
('Industrial Court Botswana', 'http://www.gov.bw/en/Ministries--Authorities/Ministries/Administration-of-Justice/Industrial-Court1/', 'Court Portals', 'Official portal of the Botswana Industrial Court.', 1),
('Gaborone Magistrate Court', 'https://www.gov.bw/en/Ministries--Authorities/Ministries/Administration-of-Justice/Magistrates-Courts/', 'Court Portals', 'Gaborone regional magistrate information and guidelines.', 1),
('Court of Appeal Botswana', 'https://www.gov.bw/court-of-appeal', 'Court Portals', 'Apex appellate court decisions and practice directives.', 1),
('Southern African Legal Information Institute (SAFLII)', 'https://saflii.org/bw/', 'Judgments & Rulings', 'Open access legal repository for Botswana case law.', 1);

-- ====================================================================
-- End of Database Initialization Script
-- ====================================================================

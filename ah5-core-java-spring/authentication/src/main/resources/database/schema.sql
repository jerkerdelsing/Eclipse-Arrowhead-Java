-- Schema of the system's own embedded database (H2, MySQL compatibility mode). Run on every connection by the
-- INIT option of spring.datasource.url, so every statement must be idempotent.

-- Logs

CREATE TABLE IF NOT EXISTS `logs` (
  `log_id` varchar(100) NOT NULL,
  `entry_date` timestamp(3) NULL DEFAULT NULL,
  `logger` varchar(100) DEFAULT NULL,
  `log_level` varchar(100) DEFAULT NULL,
  `message` mediumtext,
  `exception` mediumtext,
  PRIMARY KEY (`log_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;  

-- Systems

CREATE TABLE IF NOT EXISTS `system_` (
	`id` bigint(20) NOT NULL AUTO_INCREMENT,
	`name` varchar(63) NOT NULL,
	`authentication_method` varchar(63) NOT NULL,
	`sysop` int(1) NOT NULL DEFAULT 0,
	`extra` varchar(1024),
	`created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
	`created_by` varchar(63) NOT NULL,
	`updated_by` varchar(63) NOT NULL,
	PRIMARY KEY (`id`),
	UNIQUE KEY (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

-- Passwords

CREATE TABLE IF NOT EXISTS `password_authentication` (
	`id` bigint(20) NOT NULL AUTO_INCREMENT,
	`system_id` bigint(20) NOT NULL,
	`password` varchar(63) NOT NULL,
	PRIMARY KEY (`id`),
	UNIQUE KEY `system_id_password` (`system_id`),
	CONSTRAINT `fk_system_password` FOREIGN KEY (`system_id`) REFERENCES `system_` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;


-- Sessions 

CREATE TABLE IF NOT EXISTS `active_session` (
	`id` bigint(20) NOT NULL AUTO_INCREMENT,
	`system_id` bigint(20) NOT NULL,
	`token` varchar(63) NOT NULL, 
	`login_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `expiration_time` timestamp NOT NULL,
	PRIMARY KEY (`id`),
	UNIQUE KEY `system_id_session` (`system_id`),
	CONSTRAINT `fk_system_session` FOREIGN KEY (`system_id`) REFERENCES `system_` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

-- Default rows, once per database: a row that an operator deletes later does not come back at a restart
CREATE TABLE IF NOT EXISTS `ah_seeded` (`seeded` int NOT NULL PRIMARY KEY);
INSERT IGNORE INTO `system_` (`name`, `authentication_method`, `sysop`, `created_by`, `updated_by`)
	SELECT 'Sysop', 'PASSWORD', 1, 'Initialization script', 'Initialization script' WHERE NOT EXISTS (SELECT 1 FROM `ah_seeded`);
INSERT IGNORE INTO `password_authentication` (`system_id`, `password`)
	SELECT (SELECT `id` FROM `system_` WHERE `name` = 'Sysop'), '$2a$10$PeLSXNYONS1gjdqWwNqgxuj2fAKRj3sl4hbUspWqLMr0XpBWZnwmK' WHERE NOT EXISTS (SELECT 1 FROM `ah_seeded`);
INSERT IGNORE INTO `system_` (`name`, `authentication_method`, `sysop`, `created_by`, `updated_by`)
	SELECT 'ServiceRegistry', 'PASSWORD', 1, 'Initialization script', 'Initialization script' WHERE NOT EXISTS (SELECT 1 FROM `ah_seeded`);
INSERT IGNORE INTO `password_authentication` (`system_id`, `password`)
	SELECT (SELECT `id` FROM `system_` WHERE `name` = 'ServiceRegistry'), '$2a$10$scTu/ZS2KFMAJhpcET5B3ugMPkYhwH8UQmEpPefP6bMuwvAotsW1O' WHERE NOT EXISTS (SELECT 1 FROM `ah_seeded`);
INSERT IGNORE INTO `ah_seeded` (`seeded`) VALUES (1);

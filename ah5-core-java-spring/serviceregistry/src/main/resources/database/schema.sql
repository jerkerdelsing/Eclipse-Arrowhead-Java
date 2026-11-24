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

-- Devices

CREATE TABLE IF NOT EXISTS `device` (
	`id` bigint(20) NOT NULL AUTO_INCREMENT,
	`name` varchar(63) NOT NULL,
	`metadata` mediumtext,
	`created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
	PRIMARY KEY (`id`),
	UNIQUE KEY (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS `device_address` (
	`id` bigint(20) NOT NULL AUTO_INCREMENT,
	`device_id` bigint(20) NOT NULL,
	`address_type` varchar(30) NOT NULL,
	`address` VARCHAR(1024) NOT NULL,
	`created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
	PRIMARY KEY (`id`),
	CONSTRAINT `fk_device_id` FOREIGN KEY (`device_id`) REFERENCES `device` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

-- Systems

CREATE TABLE IF NOT EXISTS `system_` (
	`id` bigint(20) NOT NULL AUTO_INCREMENT,
	`name` varchar(63) NOT NULL,
	`metadata` mediumtext,
	`version` varchar(14) NOT NULL DEFAULT '1.0.0',
	`created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
	PRIMARY KEY (`id`),
	UNIQUE KEY (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS `system_address` (
	`id` bigint(20) NOT NULL AUTO_INCREMENT,
	`system_id` bigint(20) NOT NULL,
	`address_type` varchar(30) NOT NULL,
	`address` VARCHAR(1024) NOT NULL,
	`created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
	PRIMARY KEY (`id`),
	CONSTRAINT `fk_system_id` FOREIGN KEY (`system_id`) REFERENCES `system_` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS `device_system_connector` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `device_id` bigint(20) NOT NULL,
  `system_id` bigint(20) NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `pair` (`device_id`,`system_id`),
  UNIQUE KEY `system` (`system_id`),
  CONSTRAINT `fk_device_id2` FOREIGN KEY (`device_id`) REFERENCES `device` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_system_id2` FOREIGN KEY (`system_id`) REFERENCES `system_` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

-- Services

CREATE TABLE IF NOT EXISTS `service_definition` (
	`id` bigint(20) NOT NULL AUTO_INCREMENT,
	`name` varchar(63) NOT NULL,
	`created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
	PRIMARY KEY (`id`),
	UNIQUE KEY (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS `service_instance` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `service_instance_id` varchar(255) NOT NULL,
  `system_id` bigint(20) NOT NULL,
  `service_definition_id` bigint(20) NOT NULL,
  `version` varchar(14) NOT NULL DEFAULT '1.0.0',
  `expires_at` timestamp,
  `metadata` mediumtext,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `unique_service_instance_id` (`service_instance_id`),
  CONSTRAINT `fk_system_id3` FOREIGN KEY (`system_id`) REFERENCES `system_` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_service_definition_id` FOREIGN KEY (`service_definition_id`) REFERENCES `service_definition` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;


CREATE TABLE IF NOT EXISTS `service_interface_template` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `name` varchar(63) NOT NULL,
  `protocol` varchar(63) NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `service_interface_template_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS `service_interface_template_property` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `service_interface_template_id` bigint(20) NOT NULL,
  `property_name` varchar(63) NOT NULL,
  `mandatory` boolean NOT NULL DEFAULT 0,
  `validator` varchar(1024),
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `template_property_pair` (`service_interface_template_id`, `property_name`),
  CONSTRAINT `fk_service_interface_template_property_id` FOREIGN KEY (`service_interface_template_id`) REFERENCES `service_interface_template` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS `service_instance_interface` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `service_instance_id` bigint(20) NOT NULL,
  `service_interface_template_id` bigint(20) NOT NULL,
  `properties` mediumtext NOT NULL,
  `policy` varchar(63) NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_service_instance_id` FOREIGN KEY (`service_instance_id`) REFERENCES `service_instance` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_service_interface_template_id` FOREIGN KEY (`service_interface_template_id`) REFERENCES `service_interface_template` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

-- Default rows, once per database: a row that an operator deletes later does not come back at a restart
CREATE TABLE IF NOT EXISTS `ah_seeded` (`seeded` int NOT NULL PRIMARY KEY);
INSERT IGNORE INTO `service_interface_template` (`name`, `protocol`)
	SELECT 'generic_http', 'http' WHERE NOT EXISTS (SELECT 1 FROM `ah_seeded`);
INSERT IGNORE INTO `service_interface_template_property` (`service_interface_template_id`, `property_name`, `mandatory`, `validator`)
	SELECT (SELECT `id` FROM `service_interface_template` WHERE `name` = 'generic_http'),
	'accessAddresses',
	1,
	'NOT_EMPTY_ADDRESS_LIST' WHERE NOT EXISTS (SELECT 1 FROM `ah_seeded`);
INSERT IGNORE INTO `service_interface_template_property` (`service_interface_template_id`, `property_name`, `mandatory`, `validator`)
	SELECT (SELECT `id` FROM `service_interface_template` WHERE `name` = 'generic_http'),
	'accessPort',
	1,
	'PORT' WHERE NOT EXISTS (SELECT 1 FROM `ah_seeded`);
INSERT IGNORE INTO `service_interface_template_property` (`service_interface_template_id`, `property_name`, `mandatory`)
	SELECT (SELECT `id` FROM `service_interface_template` WHERE `name` = 'generic_http'),
	'basePath',
	1 WHERE NOT EXISTS (SELECT 1 FROM `ah_seeded`);
INSERT IGNORE INTO `service_interface_template_property` (`service_interface_template_id`, `property_name`, `mandatory`, `validator`)
	SELECT (SELECT `id` FROM `service_interface_template` WHERE `name` = 'generic_http'),
	'operations',
	0,
	'HTTP_OPERATIONS' WHERE NOT EXISTS (SELECT 1 FROM `ah_seeded`);
INSERT IGNORE INTO `service_interface_template` (`name`, `protocol`)
	SELECT 'generic_https', 'https' WHERE NOT EXISTS (SELECT 1 FROM `ah_seeded`);
INSERT IGNORE INTO `service_interface_template_property` (`service_interface_template_id`, `property_name`, `mandatory`, `validator`)
	SELECT (SELECT `id` FROM `service_interface_template` WHERE `name` = 'generic_https'),
	'accessAddresses',
	1,
	'NOT_EMPTY_ADDRESS_LIST' WHERE NOT EXISTS (SELECT 1 FROM `ah_seeded`);
INSERT IGNORE INTO `service_interface_template_property` (`service_interface_template_id`, `property_name`, `mandatory`, `validator`)
	SELECT (SELECT `id` FROM `service_interface_template` WHERE `name` = 'generic_https'),
	'accessPort',
	1,
	'PORT' WHERE NOT EXISTS (SELECT 1 FROM `ah_seeded`);
INSERT IGNORE INTO `service_interface_template_property` (`service_interface_template_id`, `property_name`, `mandatory`)
	SELECT (SELECT `id` FROM `service_interface_template` WHERE `name` = 'generic_https'),
	'basePath',
	1 WHERE NOT EXISTS (SELECT 1 FROM `ah_seeded`);
INSERT IGNORE INTO `service_interface_template_property` (`service_interface_template_id`, `property_name`, `mandatory`, `validator`)
	SELECT (SELECT `id` FROM `service_interface_template` WHERE `name` = 'generic_https'),
	'operations',
	0,
	'HTTP_OPERATIONS' WHERE NOT EXISTS (SELECT 1 FROM `ah_seeded`);
INSERT IGNORE INTO `service_interface_template` (`name`, `protocol`)
	SELECT 'generic_mqtt', 'tcp' WHERE NOT EXISTS (SELECT 1 FROM `ah_seeded`);
INSERT IGNORE INTO `service_interface_template_property` (`service_interface_template_id`, `property_name`, `mandatory`, `validator`)
	SELECT (SELECT `id` FROM `service_interface_template` WHERE `name` = 'generic_mqtt'),
	'accessAddresses',
	1,
	'NOT_EMPTY_ADDRESS_LIST' WHERE NOT EXISTS (SELECT 1 FROM `ah_seeded`);
INSERT IGNORE INTO `service_interface_template_property` (`service_interface_template_id`, `property_name`, `mandatory`, `validator`)
	SELECT (SELECT `id` FROM `service_interface_template` WHERE `name` = 'generic_mqtt'),
	'accessPort',
	1,
	'PORT' WHERE NOT EXISTS (SELECT 1 FROM `ah_seeded`);
INSERT IGNORE INTO `service_interface_template_property` (`service_interface_template_id`, `property_name`, `mandatory`)
	SELECT (SELECT `id` FROM `service_interface_template` WHERE `name` = 'generic_mqtt'),
	'baseTopic',
	1 WHERE NOT EXISTS (SELECT 1 FROM `ah_seeded`);
INSERT IGNORE INTO `service_interface_template_property` (`service_interface_template_id`, `property_name`, `mandatory`, `validator`)
	SELECT (SELECT `id` FROM `service_interface_template` WHERE `name` = 'generic_mqtt'),
	'operations',
	1,
	'NOT_EMPTY_STRING_SET|OPERATION' WHERE NOT EXISTS (SELECT 1 FROM `ah_seeded`);
INSERT IGNORE INTO `service_interface_template` (`name`, `protocol`)
	SELECT 'generic_mqtts', 'ssl' WHERE NOT EXISTS (SELECT 1 FROM `ah_seeded`);
INSERT IGNORE INTO `service_interface_template_property` (`service_interface_template_id`, `property_name`, `mandatory`, `validator`)
	SELECT (SELECT `id` FROM `service_interface_template` WHERE `name` = 'generic_mqtts'),
	'accessAddresses',
	1,
	'NOT_EMPTY_ADDRESS_LIST' WHERE NOT EXISTS (SELECT 1 FROM `ah_seeded`);
INSERT IGNORE INTO `service_interface_template_property` (`service_interface_template_id`, `property_name`, `mandatory`, `validator`)
	SELECT (SELECT `id` FROM `service_interface_template` WHERE `name` = 'generic_mqtts'),
	'accessPort',
	1,
	'PORT' WHERE NOT EXISTS (SELECT 1 FROM `ah_seeded`);
INSERT IGNORE INTO `service_interface_template_property` (`service_interface_template_id`, `property_name`, `mandatory`)
	SELECT (SELECT `id` FROM `service_interface_template` WHERE `name` = 'generic_mqtts'),
	'baseTopic',
	1 WHERE NOT EXISTS (SELECT 1 FROM `ah_seeded`);
INSERT IGNORE INTO `service_interface_template_property` (`service_interface_template_id`, `property_name`, `mandatory`, `validator`)
	SELECT (SELECT `id` FROM `service_interface_template` WHERE `name` = 'generic_mqtts'),
	'operations',
	1,
	'NOT_EMPTY_STRING_SET|OPERATION' WHERE NOT EXISTS (SELECT 1 FROM `ah_seeded`);
INSERT IGNORE INTO `service_interface_template` (`name`, `protocol`)
	SELECT 'generic_amqp', 'amqp' WHERE NOT EXISTS (SELECT 1 FROM `ah_seeded`);
INSERT IGNORE INTO `service_interface_template` (`name`, `protocol`)
	SELECT 'generic_amqps', 'amqps' WHERE NOT EXISTS (SELECT 1 FROM `ah_seeded`);
INSERT IGNORE INTO `service_interface_template_property` (`service_interface_template_id`, `property_name`, `mandatory`, `validator`)
	SELECT `id`, 'accessAddresses', 1, 'NOT_EMPTY_ADDRESS_LIST' FROM `service_interface_template` WHERE `name` IN ('generic_amqp', 'generic_amqps') AND NOT EXISTS (SELECT 1 FROM `ah_seeded`);
INSERT IGNORE INTO `service_interface_template_property` (`service_interface_template_id`, `property_name`, `mandatory`, `validator`)
	SELECT `id`, 'accessPort', 1, 'PORT' FROM `service_interface_template` WHERE `name` IN ('generic_amqp', 'generic_amqps') AND NOT EXISTS (SELECT 1 FROM `ah_seeded`);
INSERT IGNORE INTO `service_interface_template_property` (`service_interface_template_id`, `property_name`, `mandatory`)
	SELECT `id`, 'exchange', 1 FROM `service_interface_template` WHERE `name` IN ('generic_amqp', 'generic_amqps') AND NOT EXISTS (SELECT 1 FROM `ah_seeded`);
INSERT IGNORE INTO `service_interface_template_property` (`service_interface_template_id`, `property_name`, `mandatory`)
	SELECT `id`, 'baseRoutingKey', 1 FROM `service_interface_template` WHERE `name` IN ('generic_amqp', 'generic_amqps') AND NOT EXISTS (SELECT 1 FROM `ah_seeded`);
INSERT IGNORE INTO `service_interface_template_property` (`service_interface_template_id`, `property_name`, `mandatory`, `validator`)
	SELECT `id`, 'operations', 1, 'NOT_EMPTY_STRING_SET|OPERATION' FROM `service_interface_template` WHERE `name` IN ('generic_amqp', 'generic_amqps') AND NOT EXISTS (SELECT 1 FROM `ah_seeded`);
INSERT IGNORE INTO `ah_seeded` (`seeded`) VALUES (1);

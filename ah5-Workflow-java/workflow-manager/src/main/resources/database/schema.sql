-- Schema of the system's own embedded database (H2, MySQL compatibility mode). Run on every connection by the
-- INIT option of spring.datasource.url, so every statement must be idempotent.

CREATE TABLE IF NOT EXISTS `logs` (
  `log_id` varchar(100) NOT NULL,
  `entry_date` timestamp(3) NULL DEFAULT NULL,
  `logger` varchar(100) DEFAULT NULL,
  `log_level` varchar(100) DEFAULT NULL,
  `message` mediumtext,
  `exception` mediumtext,
  PRIMARY KEY (`log_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Registered recipe versions (immutable canonical JSON).
CREATE TABLE IF NOT EXISTS `recipe` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(63) NOT NULL,
  `version` varchar(14) NOT NULL,
  `status` varchar(14) NOT NULL,
  `document` mediumtext NOT NULL,
  `registered_by` varchar(63) DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_recipe_name_version` (`name`, `version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Sessions: state is the JSON snapshot of z = (M, M_A, beta, epsilon, xi, status) with the trace.
CREATE TABLE IF NOT EXISTS `workflow_session` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `recipe_id` bigint NOT NULL,
  `recipe_name` varchar(63) NOT NULL,
  `recipe_version` varchar(14) NOT NULL,
  `subject` varchar(255) DEFAULT NULL,
  `owner` varchar(63) NOT NULL,
  `status` varchar(14) NOT NULL,
  `dispatch_mode` varchar(14) NOT NULL,
  `parameters` mediumtext,
  `state` mediumtext NOT NULL,
  `product_system` varchar(63) DEFAULT NULL,
  `extensions` varchar(255) DEFAULT NULL,
  `message` mediumtext,
  `revision` bigint NOT NULL DEFAULT 0,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_session_status` (`status`),
  CONSTRAINT `fk_session_recipe` FOREIGN KEY (`recipe_id`) REFERENCES `recipe` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Step attempts dispatched to (push) or claimed by (pull) one executor.
CREATE TABLE IF NOT EXISTS `work_item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `uid` varchar(63) NOT NULL,
  `session_id` bigint NOT NULL,
  `action_name` varchar(63) NOT NULL,
  `step_name` varchar(63) NOT NULL,
  `executor_name` varchar(63) DEFAULT NULL,
  `attempt` int NOT NULL,
  `state` varchar(14) NOT NULL,
  `excluded` varchar(1024) DEFAULT NULL,
  `pending_result` mediumtext,
  `message` mediumtext,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_work_item_uid` (`uid`),
  KEY `idx_work_item_session_step` (`session_id`, `step_name`),
  KEY `idx_work_item_state` (`state`),
  CONSTRAINT `fk_work_item_session` FOREIGN KEY (`session_id`) REFERENCES `workflow_session` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Leases on shared resources (FIFO, expiry).
CREATE TABLE IF NOT EXISTS `lease` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `resource` varchar(63) NOT NULL,
  `session_id` bigint DEFAULT NULL,
  `holder` varchar(63) NOT NULL,
  `ticket` varchar(63) NOT NULL,
  `token` varchar(63) DEFAULT NULL,
  `status` varchar(14) NOT NULL,
  `lease_until` timestamp NULL DEFAULT NULL,
  `duration_seconds` double NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_lease_ticket` (`ticket`),
  UNIQUE KEY `uk_lease_token` (`token`),
  KEY `idx_lease_resource_status` (`resource`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `worklog` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `session_id` bigint DEFAULT NULL,
  `recipe` varchar(255) DEFAULT NULL,
  `step_name` varchar(63) DEFAULT NULL,
  `message` mediumtext NOT NULL,
  `detail` mediumtext,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_worklog_session` (`session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

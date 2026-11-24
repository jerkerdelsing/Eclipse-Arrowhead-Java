CREATE USER IF NOT EXISTS 'workflowmanager'@'%' IDENTIFIED BY 'change-me';
GRANT SELECT, INSERT, UPDATE, DELETE ON `ah_workflow`.* TO 'workflowmanager'@'%';
FLUSH PRIVILEGES;

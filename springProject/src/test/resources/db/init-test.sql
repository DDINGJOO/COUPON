-- Test database initialization script
-- Create extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Create shedlock table for distributed scheduler
CREATE TABLE IF NOT EXISTS shedlock (
    name VARCHAR(64) NOT NULL,
    lock_until TIMESTAMP NOT NULL,
    locked_at TIMESTAMP NOT NULL,
    locked_by VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
);

-- Add test-specific indexes or data if needed
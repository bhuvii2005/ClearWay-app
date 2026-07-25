-- Add password_version column to support migration from SHA-256 (v1) to BCrypt (v2)
ALTER TABLE users ADD COLUMN password_version INTEGER DEFAULT 1;

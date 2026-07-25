-- Clean up users with no password (e.g. from DataInitializer) before enforcing constraint
DELETE FROM users WHERE password_hash IS NULL;

-- Update password_hash column constraint
ALTER TABLE users ALTER COLUMN password_hash SET NOT NULL;

-- Add CASCADE delete for user -> routes relationship
ALTER TABLE routes DROP CONSTRAINT IF EXISTS routes_user_id_fkey;
ALTER TABLE routes ADD CONSTRAINT routes_user_id_fkey
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

-- Partial index for active routes used by scheduler
CREATE INDEX IF NOT EXISTS idx_routes_is_active ON routes(is_active) WHERE is_active = TRUE;

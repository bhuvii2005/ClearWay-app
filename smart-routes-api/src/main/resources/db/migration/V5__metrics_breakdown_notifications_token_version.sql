-- Token versioning: bumping this value revokes every JWT issued before it (logout, password change)
ALTER TABLE users ADD COLUMN token_version INTEGER NOT NULL DEFAULT 0;

-- Saved routes keep the travel time of the alternative they were saved from
ALTER TABLE routes ADD COLUMN travel_time_seconds INTEGER;
CREATE INDEX IF NOT EXISTS idx_routes_user_created ON routes(user_id, created_at DESC);

-- Per-pollutant averages for each scoring run, used by the history charts
ALTER TABLE route_metrics ADD COLUMN aqi DOUBLE PRECISION;
ALTER TABLE route_metrics ADD COLUMN pm25 DOUBLE PRECISION;
ALTER TABLE route_metrics ADD COLUMN pm10 DOUBLE PRECISION;
ALTER TABLE route_metrics ADD COLUMN no2 DOUBLE PRECISION;
ALTER TABLE route_metrics ADD COLUMN ozone DOUBLE PRECISION;

-- Persisted alerts, so users can see what they missed while offline
CREATE TABLE notifications (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    route_id UUID REFERENCES routes(id) ON DELETE CASCADE,
    kind VARCHAR(30) NOT NULL,
    message VARCHAR(500) NOT NULL,
    pollution_score DOUBLE PRECISION,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_notifications_user_created ON notifications(user_id, created_at DESC);
CREATE INDEX idx_notifications_route_created ON notifications(route_id, created_at DESC);

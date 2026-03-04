-- Create extensions if they don't exist
CREATE EXTENSION IF NOT EXISTS postgis;

-- Users Table
CREATE TABLE users (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    phone VARCHAR(50),
    preferred_channels VARCHAR(255),
    fcm_token VARCHAR(255),
    whatsapp_opt_in BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    last_active TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Routes Table
CREATE TABLE routes (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    name VARCHAR(255) NOT NULL,
    type VARCHAR(50) NOT NULL, -- jogging, cycling, walking, commute
    is_active BOOLEAN DEFAULT TRUE,
    path geometry(LineString, 4326) NOT NULL,
    distance_km DOUBLE PRECISION,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    last_pollution_score DOUBLE PRECISION,
    avg_pollution_score DOUBLE PRECISION,
    last_checked_at TIMESTAMP WITH TIME ZONE
);

-- Route Metrics Table
CREATE TABLE route_metrics (
    id UUID PRIMARY KEY,
    route_id UUID NOT NULL REFERENCES routes(id) ON DELETE CASCADE,
    timestamp TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    pollution_score DOUBLE PRECISION NOT NULL,
    traffic_index DOUBLE PRECISION,
    temperature DOUBLE PRECISION
);

-- Create Indexes for spatial queries and foreign keys
CREATE INDEX idx_routes_user_id ON routes(user_id);
CREATE INDEX idx_routes_path ON routes USING GIST(path);
CREATE INDEX idx_route_metrics_route_id ON route_metrics(route_id);
CREATE INDEX idx_route_metrics_timestamp ON route_metrics(timestamp);

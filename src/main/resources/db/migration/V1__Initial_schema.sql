-- Create short_url table
CREATE TABLE short_url (
    id BIGSERIAL PRIMARY KEY,
    short_code VARCHAR(20) NOT NULL UNIQUE,
    original_url TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE,
    click_count BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_short_code ON short_url(short_code);
CREATE INDEX idx_status ON short_url(status);
CREATE INDEX idx_expires_at ON short_url(expires_at);

-- Create click_event table
CREATE TABLE click_event (
    id BIGSERIAL PRIMARY KEY,
    short_url_id BIGINT NOT NULL REFERENCES short_url(id) ON DELETE CASCADE,
    clicked_at TIMESTAMP WITH TIME ZONE NOT NULL,
    ip_hash VARCHAR(64),
    user_agent TEXT,
    referer TEXT
);

CREATE INDEX idx_short_url_id ON click_event(short_url_id);
CREATE INDEX idx_clicked_at ON click_event(clicked_at);

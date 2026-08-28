-- Add index on (original_url, expires_at) for deduplication query optimization
CREATE INDEX idx_original_url_expires_at ON short_url(original_url, expires_at);

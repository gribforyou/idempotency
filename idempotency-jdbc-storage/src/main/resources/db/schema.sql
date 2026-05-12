-- Idempotency Records Table
-- Used by JdbcIdempotencyStore to persist idempotency state

CREATE TABLE IF NOT EXISTS idempotency_records (
    idempotency_key VARCHAR(255) PRIMARY KEY,
    operation_type  VARCHAR(255) NOT NULL,
    request_data    TEXT,
    state           VARCHAR(50) NOT NULL,
    created_at      TIMESTAMP NOT NULL,
    updated_at      TIMESTAMP NOT NULL,
    version         BIGINT NOT NULL DEFAULT 0
);

-- Index for faster lookups by operation_type
CREATE INDEX IF NOT EXISTS idx_operation_type ON idempotency_records(operation_type);

-- Index for faster lookups by state
CREATE INDEX IF NOT EXISTS idx_state ON idempotency_records(state);

-- Index for faster lookups by created_at (for cleanup jobs)
CREATE INDEX IF NOT EXISTS idx_created_at ON idempotency_records(created_at);
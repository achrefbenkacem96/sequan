CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(320) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(200) NOT NULL,
    global_role VARCHAR(32) NOT NULL,
    stripe_customer_id VARCHAR(255),
    stripe_connect_account_id VARCHAR(255),
    connect_onboarding_complete BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    deleted_at TIMESTAMPTZ
);

CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users (id),
    token_hash VARCHAR(128) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);

CREATE TABLE escrows (
    id UUID PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    currency CHAR(3) NOT NULL,
    amount_total BIGINT NOT NULL CHECK (amount_total >= 0),
    amount_funded BIGINT NOT NULL DEFAULT 0 CHECK (amount_funded >= 0),
    amount_released BIGINT NOT NULL DEFAULT 0 CHECK (amount_released >= 0),
    amount_refunded BIGINT NOT NULL DEFAULT 0 CHECK (amount_refunded >= 0),
    amount_available BIGINT NOT NULL DEFAULT 0 CHECK (amount_available >= 0),
    status VARCHAR(40) NOT NULL,
    funding_deadline TIMESTAMPTZ,
    completion_deadline TIMESTAMPTZ,
    created_by UUID NOT NULL REFERENCES users (id),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_escrow_balance CHECK (
        amount_funded = amount_released + amount_refunded + amount_available
    )
);

CREATE INDEX idx_escrows_status ON escrows (status);
CREATE INDEX idx_escrows_created_by ON escrows (created_by);

CREATE TABLE escrow_participants (
    id UUID PRIMARY KEY,
    escrow_id UUID NOT NULL REFERENCES escrows (id),
    user_id UUID NOT NULL REFERENCES users (id),
    role VARCHAR(32) NOT NULL,
    share_type VARCHAR(16) NOT NULL,
    share_value BIGINT NOT NULL CHECK (share_value >= 0),
    participation_status VARCHAR(32) NOT NULL,
    approval_status VARCHAR(32) NOT NULL DEFAULT 'NOT_REQUIRED',
    can_approve_release BOOLEAN NOT NULL DEFAULT FALSE,
    invited_email VARCHAR(320),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE (escrow_id, user_id, role)
);

CREATE INDEX idx_participants_escrow ON escrow_participants (escrow_id);
CREATE INDEX idx_participants_user ON escrow_participants (user_id);

CREATE TABLE escrow_conditions (
    id UUID PRIMARY KEY,
    escrow_id UUID NOT NULL REFERENCES escrows (id),
    title VARCHAR(200) NOT NULL,
    description TEXT,
    sort_order INT NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL,
    required BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_conditions_escrow ON escrow_conditions (escrow_id);

CREATE TABLE payments (
    id UUID PRIMARY KEY,
    escrow_id UUID NOT NULL REFERENCES escrows (id),
    payer_user_id UUID NOT NULL REFERENCES users (id),
    amount BIGINT NOT NULL CHECK (amount > 0),
    currency CHAR(3) NOT NULL,
    status VARCHAR(32) NOT NULL,
    stripe_checkout_session_id VARCHAR(255) UNIQUE,
    stripe_payment_intent_id VARCHAR(255),
    stripe_charge_id VARCHAR(255),
    idempotency_key VARCHAR(128) UNIQUE,
    failure_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_payments_escrow ON payments (escrow_id);

CREATE TABLE ledger_entries (
    id UUID PRIMARY KEY,
    escrow_id UUID NOT NULL REFERENCES escrows (id),
    entry_type VARCHAR(32) NOT NULL,
    amount BIGINT NOT NULL,
    currency CHAR(3) NOT NULL,
    reference_type VARCHAR(32),
    reference_id UUID,
    description VARCHAR(500),
    created_by UUID REFERENCES users (id),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_ledger_escrow ON ledger_entries (escrow_id, created_at);

CREATE TABLE releases (
    id UUID PRIMARY KEY,
    escrow_id UUID NOT NULL REFERENCES escrows (id),
    requested_by UUID NOT NULL REFERENCES users (id),
    beneficiary_participant_id UUID NOT NULL REFERENCES escrow_participants (id),
    amount BIGINT NOT NULL CHECK (amount > 0),
    currency CHAR(3) NOT NULL,
    status VARCHAR(32) NOT NULL,
    stripe_transfer_id VARCHAR(255) UNIQUE,
    idempotency_key VARCHAR(128) UNIQUE,
    failure_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_releases_escrow ON releases (escrow_id);

CREATE TABLE refunds (
    id UUID PRIMARY KEY,
    escrow_id UUID NOT NULL REFERENCES escrows (id),
    payment_id UUID NOT NULL REFERENCES payments (id),
    requested_by UUID NOT NULL REFERENCES users (id),
    amount BIGINT NOT NULL CHECK (amount > 0),
    currency CHAR(3) NOT NULL,
    status VARCHAR(32) NOT NULL,
    stripe_refund_id VARCHAR(255) UNIQUE,
    idempotency_key VARCHAR(128) UNIQUE,
    failure_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_refunds_escrow ON refunds (escrow_id);

CREATE TABLE disputes (
    id UUID PRIMARY KEY,
    escrow_id UUID NOT NULL REFERENCES escrows (id),
    opened_by UUID NOT NULL REFERENCES users (id),
    reason TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    resolution_notes TEXT,
    resolved_by UUID REFERENCES users (id),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_disputes_escrow ON disputes (escrow_id);

CREATE TABLE webhook_events (
    id UUID PRIMARY KEY,
    stripe_event_id VARCHAR(255) NOT NULL UNIQUE,
    event_type VARCHAR(120) NOT NULL,
    payload_hash VARCHAR(128) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(32) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    last_error TEXT,
    processed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_webhook_status ON webhook_events (status);

CREATE TABLE audit_logs (
    id UUID PRIMARY KEY,
    escrow_id UUID,
    actor_user_id UUID,
    action VARCHAR(80) NOT NULL,
    entity_type VARCHAR(64),
    entity_id UUID,
    metadata JSONB,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_audit_escrow ON audit_logs (escrow_id, created_at);
CREATE INDEX idx_audit_actor ON audit_logs (actor_user_id, created_at);

CREATE TABLE notifications (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users (id),
    escrow_id UUID REFERENCES escrows (id),
    type VARCHAR(64) NOT NULL,
    title VARCHAR(200) NOT NULL,
    message TEXT NOT NULL,
    read_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_notifications_user ON notifications (user_id, created_at);

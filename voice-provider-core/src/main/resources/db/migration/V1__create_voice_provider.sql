CREATE TABLE voice_provider (
    tenant_domain VARCHAR(255) NOT NULL,
    organization_id VARCHAR(255) NOT NULL,
    provider_name VARCHAR(64) NOT NULL,
    ari_url VARCHAR(2048) NOT NULL,
    ari_username VARCHAR(128) NOT NULL,
    secret_reference VARCHAR(512),
    application_name VARCHAR(128) NOT NULL,
    outbound_endpoint VARCHAR(128) NOT NULL,
    dialplan_context VARCHAR(128) NOT NULL,
    dialplan_extension VARCHAR(80) NOT NULL,
    caller_id VARCHAR(32),
    ring_timeout_seconds INTEGER NOT NULL,
    max_call_duration_seconds INTEGER NOT NULL,
    language VARCHAR(16) NOT NULL,
    enabled BOOLEAN NOT NULL,
    revision BIGINT NOT NULL,
    created_at_epoch_ms BIGINT NOT NULL,
    updated_at_epoch_ms BIGINT NOT NULL,
    CONSTRAINT pk_voice_provider PRIMARY KEY (tenant_domain, organization_id, provider_name)
);

CREATE INDEX idx_voice_provider_scope
    ON voice_provider (tenant_domain, organization_id);

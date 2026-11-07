CREATE TABLE voice_provider_secret (
    tenant_domain VARCHAR(255) NOT NULL,
    organization_id VARCHAR(255) NOT NULL,
    secret_reference VARCHAR(64) NOT NULL,
    iv_b64 VARCHAR(64) NOT NULL,
    ciphertext_b64 VARCHAR(8192) NOT NULL,
    CONSTRAINT pk_voice_provider_secret
        PRIMARY KEY (tenant_domain, organization_id, secret_reference)
);

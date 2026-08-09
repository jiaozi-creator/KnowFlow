CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE organization (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    slug VARCHAR(80) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE app_user (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(190) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(80) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE organization_member (
    id BIGSERIAL PRIMARY KEY,
    organization_id BIGINT NOT NULL REFERENCES organization(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    role VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (organization_id, user_id)
);

CREATE TABLE refresh_token (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    organization_id BIGINT NOT NULL REFERENCES organization(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE knowledge_base (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES organization(id) ON DELETE CASCADE,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(500),
    visibility VARCHAR(30) NOT NULL DEFAULT 'TENANT',
    created_by BIGINT NOT NULL REFERENCES app_user(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_kb_tenant ON knowledge_base(tenant_id);

CREATE TABLE document (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES organization(id) ON DELETE CASCADE,
    knowledge_base_id BIGINT NOT NULL REFERENCES knowledge_base(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    file_type VARCHAR(20) NOT NULL,
    status VARCHAR(30) NOT NULL,
    current_version_id BIGINT,
    uploaded_by BIGINT NOT NULL REFERENCES app_user(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_document_kb ON document(tenant_id, knowledge_base_id);

CREATE TABLE document_version (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES organization(id) ON DELETE CASCADE,
    document_id BIGINT NOT NULL REFERENCES document(id) ON DELETE CASCADE,
    version_no INT NOT NULL,
    object_key VARCHAR(600) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(120),
    file_size BIGINT NOT NULL,
    sha256 VARCHAR(64),
    status VARCHAR(30) NOT NULL,
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(document_id, version_no)
);
ALTER TABLE document ADD CONSTRAINT fk_document_current_version
    FOREIGN KEY (current_version_id) REFERENCES document_version(id) ON DELETE SET NULL;

CREATE TABLE ingestion_task (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES organization(id) ON DELETE CASCADE,
    document_id BIGINT NOT NULL REFERENCES document(id) ON DELETE CASCADE,
    document_version_id BIGINT NOT NULL REFERENCES document_version(id) ON DELETE CASCADE,
    status VARCHAR(30) NOT NULL,
    progress INT NOT NULL DEFAULT 0,
    retry_count INT NOT NULL DEFAULT 0,
    error_message TEXT,
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE document_chunk (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES organization(id) ON DELETE CASCADE,
    knowledge_base_id BIGINT NOT NULL REFERENCES knowledge_base(id) ON DELETE CASCADE,
    document_id BIGINT NOT NULL REFERENCES document(id) ON DELETE CASCADE,
    document_version_id BIGINT NOT NULL REFERENCES document_version(id) ON DELETE CASCADE,
    chunk_index INT NOT NULL,
    page_number INT,
    heading VARCHAR(500),
    content TEXT NOT NULL,
    token_count INT,
    embedding VECTOR(384) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(document_version_id, chunk_index)
);
CREATE INDEX idx_chunk_scope ON document_chunk(tenant_id, knowledge_base_id, document_version_id);
CREATE INDEX idx_chunk_embedding_hnsw ON document_chunk USING hnsw (embedding vector_cosine_ops);

CREATE TABLE conversation (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES organization(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    title VARCHAR(160) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE chat_message (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES organization(id) ON DELETE CASCADE,
    conversation_id BIGINT NOT NULL REFERENCES conversation(id) ON DELETE CASCADE,
    role VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE citation (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES organization(id) ON DELETE CASCADE,
    message_id BIGINT NOT NULL REFERENCES chat_message(id) ON DELETE CASCADE,
    document_id BIGINT NOT NULL REFERENCES document(id) ON DELETE CASCADE,
    chunk_id BIGINT NOT NULL REFERENCES document_chunk(id) ON DELETE CASCADE,
    page_number INT,
    citation_index INT NOT NULL,
    excerpt TEXT,
    similarity DOUBLE PRECISION
);

CREATE TABLE audit_log (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES organization(id) ON DELETE CASCADE,
    user_id BIGINT REFERENCES app_user(id) ON DELETE SET NULL,
    action VARCHAR(80) NOT NULL,
    resource_type VARCHAR(80),
    resource_id VARCHAR(80),
    metadata JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

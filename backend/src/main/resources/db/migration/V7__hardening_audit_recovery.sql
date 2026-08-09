-- KnowFlow V1 final hardening
CREATE INDEX IF NOT EXISTS idx_audit_log_tenant_created_at
    ON audit_log(tenant_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_ingestion_task_status_updated
    ON ingestion_task(status, updated_at);

CREATE INDEX IF NOT EXISTS idx_document_version_document_created
    ON document_version(document_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_document_chunk_document_version
    ON document_chunk(document_id, document_version_id);

CREATE INDEX IF NOT EXISTS idx_document_tenant_kb_status
    ON document(tenant_id, knowledge_base_id, status);

WITH ranked AS (
    SELECT id,
           ROW_NUMBER() OVER (
               PARTITION BY document_version_id
               ORDER BY id DESC
           ) AS rn
    FROM ingestion_task
    WHERE status IN ('PENDING', 'PARSING', 'CHUNKING', 'EMBEDDING')
)
UPDATE ingestion_task t
SET status = 'FAILED',
    error_message = COALESCE(
        t.error_message,
        'Duplicate active ingestion task closed during V7 migration'
    ),
    finished_at = COALESCE(t.finished_at, now()),
    updated_at = now()
FROM ranked r
WHERE t.id = r.id
  AND r.rn > 1;

CREATE UNIQUE INDEX IF NOT EXISTS uq_ingestion_task_one_active_per_version
    ON ingestion_task(document_version_id)
    WHERE status IN ('PENDING', 'PARSING', 'CHUNKING', 'EMBEDDING');

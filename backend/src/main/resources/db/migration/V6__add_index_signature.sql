ALTER TABLE document_version
ADD COLUMN IF NOT EXISTS index_signature VARCHAR(255);

ALTER TABLE document_version
ADD COLUMN IF NOT EXISTS indexed_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_document_version_index_signature
ON document_version(index_signature);

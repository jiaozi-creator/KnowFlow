-- Switch from Mock 384-dimensional embeddings
-- to real text-embedding-v4 1024-dimensional embeddings.

DROP INDEX IF EXISTS idx_chunk_embedding_hnsw;

-- Old 384-dimensional vectors cannot be mixed with 1024-dimensional vectors.
-- Citations referencing chunks are removed automatically because of ON DELETE CASCADE.
DELETE FROM document_chunk;

ALTER TABLE document_chunk
DROP COLUMN embedding;

ALTER TABLE document_chunk
ADD COLUMN embedding VECTOR(1024) NOT NULL;

CREATE INDEX idx_chunk_embedding_hnsw
ON document_chunk
USING hnsw (embedding vector_cosine_ops);

UPDATE document
SET
    status = 'NEEDS_REINDEX',
    updated_at = now()
WHERE current_version_id IS NOT NULL;

UPDATE document_version dv
SET
    status = 'NEEDS_REINDEX',
    error_message = 'Embedding model changed to 1024 dimensions. Reindex required.'
WHERE EXISTS (
    SELECT 1
    FROM document d
    WHERE d.current_version_id = dv.id
);

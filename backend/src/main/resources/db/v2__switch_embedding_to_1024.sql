-- 切换到真实 Embedding：text-embedding-v4 使用 1024 维。
-- 旧的 Mock 384 维向量不能与真实模型向量混用，
-- 因此清空旧 chunk，并要求已有文档重新向量化。

DROP INDEX IF EXISTS idx_chunk_embedding_hnsw;

-- citation.chunk_id 如果设置了 ON DELETE CASCADE，
-- 删除旧 document_chunk 时会同步清理旧引用。
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
    error_message = 'Embedding 模型已切换，请重新向量化'
WHERE EXISTS (
    SELECT 1
    FROM document d
    WHERE d.current_version_id = dv.id
);
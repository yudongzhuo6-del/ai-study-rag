-- Back up the database before applying to an existing installation.
-- vector_id stores the full comma-separated embedding, not a vector identifier.
ALTER TABLE document_chunk
    MODIFY COLUMN vector_id MEDIUMTEXT NULL
    COMMENT 'Serialized embedding vector used for reuse and in-memory retrieval';

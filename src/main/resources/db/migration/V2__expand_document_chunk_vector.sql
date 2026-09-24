ALTER TABLE document_chunk
    MODIFY COLUMN vector_id MEDIUMTEXT NULL
    COMMENT 'Serialized embedding vector used for reuse and in-memory retrieval';

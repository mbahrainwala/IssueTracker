-- Documents attached to a ticket. Only the metadata lives here; the bytes are written to
-- app.attachments.directory under storage_key, an opaque UUID that is never derived from
-- the uploaded name - so a crafted filename cannot steer the write anywhere.

CREATE TABLE ticket_attachments (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    ticket_id      BIGINT       NOT NULL,
    filename       VARCHAR(255) NOT NULL,
    content_type   VARCHAR(150) NOT NULL,
    size_bytes     BIGINT       NOT NULL,
    storage_key    VARCHAR(64)  NOT NULL,
    sha256         VARCHAR(64)  NOT NULL,
    uploaded_by_id BIGINT       NOT NULL,
    created_at     TIMESTAMP    NOT NULL,
    updated_at     TIMESTAMP    NOT NULL,
    CONSTRAINT pk_ticket_attachments PRIMARY KEY (id),
    CONSTRAINT uq_ticket_attachments_storage_key UNIQUE (storage_key),
    CONSTRAINT fk_attachments_ticket FOREIGN KEY (ticket_id) REFERENCES tickets (id) ON DELETE CASCADE,
    CONSTRAINT fk_attachments_user FOREIGN KEY (uploaded_by_id) REFERENCES users (id)
);

-- The ticket page lists these on every load, newest first.
CREATE INDEX idx_attachments_ticket ON ticket_attachments (ticket_id, id);

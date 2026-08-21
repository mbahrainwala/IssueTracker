-- Archiving takes finished work out of the day-to-day views without deleting it.
-- archived_at doubles as the flag and the timestamp: NULL means active.

ALTER TABLE tickets ADD COLUMN archived_at TIMESTAMP NULL;
ALTER TABLE tickets ADD COLUMN archived_by_id BIGINT NULL;

ALTER TABLE tickets
    ADD CONSTRAINT fk_tickets_archived_by FOREIGN KEY (archived_by_id) REFERENCES users (id);

-- Every board and list query filters on this, so index it alongside the project.
CREATE INDEX idx_tickets_project_archived ON tickets (project_id, archived_at);

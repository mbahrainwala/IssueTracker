-- A ticket may belong to at most one epic; an epic gathers many tickets.
-- Self-referencing FK on tickets: epic_id points at another ticket whose type is EPIC.
-- ON DELETE SET NULL so deleting an epic releases its children rather than destroying them.

ALTER TABLE tickets ADD COLUMN epic_id BIGINT;

ALTER TABLE tickets
    ADD CONSTRAINT fk_tickets_epic FOREIGN KEY (epic_id) REFERENCES tickets (id) ON DELETE SET NULL;

CREATE INDEX idx_tickets_epic ON tickets (epic_id);

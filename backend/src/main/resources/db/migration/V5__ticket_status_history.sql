-- Records every move of a ticket between status buckets: who moved it, from where, to where.
-- A full log rather than a "last mover" column on tickets, so the trail survives later moves.

CREATE TABLE ticket_status_changes (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    ticket_id     BIGINT      NOT NULL,
    from_status   VARCHAR(20) NOT NULL,
    to_status     VARCHAR(20) NOT NULL,
    moved_by_id   BIGINT      NOT NULL,
    moved_at      TIMESTAMP   NOT NULL,
    CONSTRAINT pk_ticket_status_changes PRIMARY KEY (id),
    CONSTRAINT fk_tsc_ticket FOREIGN KEY (ticket_id) REFERENCES tickets (id) ON DELETE CASCADE,
    CONSTRAINT fk_tsc_user FOREIGN KEY (moved_by_id) REFERENCES users (id)
);

-- Ordering is by id, not moved_at: MySQL TIMESTAMP has second precision, so two moves within
-- the same second would otherwise sort arbitrarily.
CREATE INDEX idx_tsc_ticket ON ticket_status_changes (ticket_id, id);

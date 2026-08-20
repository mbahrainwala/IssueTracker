-- Directed links between tickets ("PROJ2-9 blocks PROJ1-3"). A single row carries the
-- relationship; the opposite ticket shows the inverse type, so no mirror row is stored.
-- Links may cross projects.

CREATE TABLE ticket_links (
    id                BIGINT      NOT NULL AUTO_INCREMENT,
    source_ticket_id  BIGINT      NOT NULL,
    target_ticket_id  BIGINT      NOT NULL,
    link_type         VARCHAR(30) NOT NULL,
    created_by_id     BIGINT      NOT NULL,
    created_at        TIMESTAMP   NOT NULL,
    updated_at        TIMESTAMP   NOT NULL,
    CONSTRAINT pk_ticket_links PRIMARY KEY (id),
    CONSTRAINT uq_ticket_links UNIQUE (source_ticket_id, target_ticket_id, link_type),
    CONSTRAINT fk_ticket_links_source FOREIGN KEY (source_ticket_id) REFERENCES tickets (id) ON DELETE CASCADE,
    CONSTRAINT fk_ticket_links_target FOREIGN KEY (target_ticket_id) REFERENCES tickets (id) ON DELETE CASCADE,
    CONSTRAINT fk_ticket_links_creator FOREIGN KEY (created_by_id) REFERENCES users (id)
);

CREATE INDEX idx_ticket_links_source ON ticket_links (source_ticket_id);
CREATE INDEX idx_ticket_links_target ON ticket_links (target_ticket_id);

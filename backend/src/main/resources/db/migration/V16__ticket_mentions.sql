-- @username in a comment or a ticket description raises a mention: the ticket stays flagged
-- for that person until they open it and acknowledge it with a comment of their own.
--
-- A row per (ticket, person, source), not per ticket: two people mentioned in one comment get
-- one each, and the same person mentioned twice in a conversation is told twice. Acknowledging
-- clears everything outstanding on that ticket for that person at once, because the thing they
-- acknowledge is "I have read this ticket", not "I have read comment 47".
--
-- source_comment_id is null when the mention came from the ticket description, which has no
-- comment row to point at. It is ON DELETE SET NULL rather than CASCADE: deleting the comment
-- that mentioned you should not quietly erase the fact that you were asked.

CREATE TABLE ticket_mentions (
    id                  BIGINT    NOT NULL AUTO_INCREMENT,
    ticket_id           BIGINT    NOT NULL,
    mentioned_user_id   BIGINT    NOT NULL,
    mentioned_by_id     BIGINT    NOT NULL,
    source_comment_id   BIGINT    NULL,
    created_at          TIMESTAMP NOT NULL,
    acknowledged_at     TIMESTAMP NULL,
    acknowledgement_id  BIGINT    NULL,
    CONSTRAINT pk_ticket_mentions PRIMARY KEY (id),
    CONSTRAINT fk_mentions_ticket FOREIGN KEY (ticket_id) REFERENCES tickets (id) ON DELETE CASCADE,
    CONSTRAINT fk_mentions_user FOREIGN KEY (mentioned_user_id) REFERENCES users (id),
    CONSTRAINT fk_mentions_by FOREIGN KEY (mentioned_by_id) REFERENCES users (id),
    CONSTRAINT fk_mentions_source FOREIGN KEY (source_comment_id) REFERENCES comments (id) ON DELETE SET NULL,
    CONSTRAINT fk_mentions_ack FOREIGN KEY (acknowledgement_id) REFERENCES comments (id) ON DELETE SET NULL
);

-- "What is still waiting for me?" is the query this table exists to answer, and it runs on
-- every board load.
CREATE INDEX idx_mentions_outstanding ON ticket_mentions (mentioned_user_id, acknowledged_at);
CREATE INDEX idx_mentions_ticket ON ticket_mentions (ticket_id, mentioned_user_id);

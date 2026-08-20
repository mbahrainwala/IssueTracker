-- Portable DDL: valid on MySQL 8 and on H2 running with MODE=MySQL.
-- Avoid engine/charset clauses and vendor-only types so one migration set serves both.

CREATE TABLE users (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    username        VARCHAR(60)  NOT NULL,
    email           VARCHAR(190) NOT NULL,
    password_hash   VARCHAR(100) NOT NULL,
    display_name    VARCHAR(120) NOT NULL,
    role            VARCHAR(20)  NOT NULL,
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP    NOT NULL,
    updated_at      TIMESTAMP    NOT NULL,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uq_users_username UNIQUE (username),
    CONSTRAINT uq_users_email UNIQUE (email)
);

CREATE TABLE projects (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    project_key     VARCHAR(10)  NOT NULL,
    name            VARCHAR(150) NOT NULL,
    description     VARCHAR(4000),
    lead_id         BIGINT,
    ticket_seq      BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMP    NOT NULL,
    updated_at      TIMESTAMP    NOT NULL,
    CONSTRAINT pk_projects PRIMARY KEY (id),
    CONSTRAINT uq_projects_key UNIQUE (project_key),
    CONSTRAINT fk_projects_lead FOREIGN KEY (lead_id) REFERENCES users (id)
);

CREATE TABLE project_members (
    project_id      BIGINT       NOT NULL,
    user_id         BIGINT       NOT NULL,
    project_role    VARCHAR(20)  NOT NULL,
    CONSTRAINT pk_project_members PRIMARY KEY (project_id, user_id),
    CONSTRAINT fk_pm_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT fk_pm_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE tickets (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    project_id      BIGINT       NOT NULL,
    ticket_number   BIGINT       NOT NULL,
    ticket_key      VARCHAR(64)  NOT NULL,
    title           VARCHAR(255) NOT NULL,
    description     VARCHAR(4000),
    type            VARCHAR(20)  NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    priority        VARCHAR(20)  NOT NULL,
    reporter_id     BIGINT       NOT NULL,
    assignee_id     BIGINT,
    story_points    INT,
    due_date        DATE,
    created_at      TIMESTAMP    NOT NULL,
    updated_at      TIMESTAMP    NOT NULL,
    CONSTRAINT pk_tickets PRIMARY KEY (id),
    CONSTRAINT uq_tickets_key UNIQUE (ticket_key),
    CONSTRAINT uq_tickets_project_number UNIQUE (project_id, ticket_number),
    CONSTRAINT fk_tickets_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT fk_tickets_reporter FOREIGN KEY (reporter_id) REFERENCES users (id),
    CONSTRAINT fk_tickets_assignee FOREIGN KEY (assignee_id) REFERENCES users (id)
);

CREATE INDEX idx_tickets_project_status ON tickets (project_id, status);
CREATE INDEX idx_tickets_assignee ON tickets (assignee_id);

CREATE TABLE comments (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    ticket_id       BIGINT       NOT NULL,
    author_id       BIGINT       NOT NULL,
    body            VARCHAR(4000) NOT NULL,
    created_at      TIMESTAMP    NOT NULL,
    updated_at      TIMESTAMP    NOT NULL,
    CONSTRAINT pk_comments PRIMARY KEY (id),
    CONSTRAINT fk_comments_ticket FOREIGN KEY (ticket_id) REFERENCES tickets (id) ON DELETE CASCADE,
    CONSTRAINT fk_comments_author FOREIGN KEY (author_id) REFERENCES users (id)
);

CREATE INDEX idx_comments_ticket ON comments (ticket_id);

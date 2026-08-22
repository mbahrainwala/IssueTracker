-- Swim lanes stop being a hardcoded set and become per-project data, seeded from a template.
--
-- The five buckets were a Java enum, which meant every project on the installation had the
-- same board whether it tracked sprints, court filings or a holiday. A lane now belongs to a
-- project, and a template is a reusable blueprint an administrator can define once.
--
-- The lane's NAME IS the value stored on the ticket. There is no separate code: 'In Progress'
-- is both what the column is called and what tickets.status holds. That keeps the data legible,
-- lets a template invent any lane it likes, and means the history table - which already stored
-- text - needs no translation table to stay readable years later.

CREATE TABLE project_templates (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    name          VARCHAR(120)  NOT NULL,
    description   VARCHAR(1000) NULL,
    -- Built-in templates ship with the app; they can be edited but not deleted, so an
    -- installation always has something to create a project from.
    built_in      BOOLEAN       NOT NULL DEFAULT FALSE,
    created_by_id BIGINT        NULL,
    created_at    TIMESTAMP     NOT NULL,
    updated_at    TIMESTAMP     NOT NULL,
    CONSTRAINT pk_project_templates PRIMARY KEY (id),
    CONSTRAINT uq_project_templates_name UNIQUE (name),
    CONSTRAINT fk_templates_user FOREIGN KEY (created_by_id) REFERENCES users (id)
);

CREATE TABLE project_template_lanes (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    template_id  BIGINT      NOT NULL,
    name         VARCHAR(60) NOT NULL,
    lane_order   INT         NOT NULL,
    -- Where new tickets land, and which lane counts as finished work. Exactly one of each
    -- per template; the service enforces that, since no useful column constraint can.
    initial_lane BOOLEAN     NOT NULL DEFAULT FALSE,
    done_lane    BOOLEAN     NOT NULL DEFAULT FALSE,
    CONSTRAINT pk_project_template_lanes PRIMARY KEY (id),
    CONSTRAINT uq_template_lane_name UNIQUE (template_id, name),
    CONSTRAINT fk_template_lanes_template FOREIGN KEY (template_id)
        REFERENCES project_templates (id) ON DELETE CASCADE
);

-- A project's own lanes. Copied from a template at creation and independent of it afterwards:
-- editing a template never reaches back into projects already made from it.
CREATE TABLE project_statuses (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    project_id   BIGINT      NOT NULL,
    name         VARCHAR(60) NOT NULL,
    lane_order   INT         NOT NULL,
    initial_lane BOOLEAN     NOT NULL DEFAULT FALSE,
    done_lane    BOOLEAN     NOT NULL DEFAULT FALSE,
    CONSTRAINT pk_project_statuses PRIMARY KEY (id),
    CONSTRAINT uq_project_status_name UNIQUE (project_id, name),
    CONSTRAINT fk_project_statuses_project FOREIGN KEY (project_id)
        REFERENCES projects (id) ON DELETE CASCADE
);

CREATE INDEX idx_project_statuses_order ON project_statuses (project_id, lane_order);

-- Which template a project was made from, for display only.
ALTER TABLE projects ADD COLUMN template_id BIGINT NULL;
ALTER TABLE projects
    ADD CONSTRAINT fk_projects_template FOREIGN KEY (template_id) REFERENCES project_templates (id);

-- Lane names are free text now, so the columns holding them have to be wider than an enum.
ALTER TABLE tickets MODIFY COLUMN status VARCHAR(60) NOT NULL;
ALTER TABLE ticket_status_changes MODIFY COLUMN from_status VARCHAR(60) NOT NULL;
ALTER TABLE ticket_status_changes MODIFY COLUMN to_status VARCHAR(60) NOT NULL;

-- Existing data held enum names; it now holds lane names. Same five buckets, spelled the way
-- they were always displayed.
UPDATE tickets SET status = 'Backlog'     WHERE status = 'BACKLOG';
UPDATE tickets SET status = 'To Do'       WHERE status = 'TODO';
UPDATE tickets SET status = 'In Progress' WHERE status = 'IN_PROGRESS';
UPDATE tickets SET status = 'In Review'   WHERE status = 'IN_REVIEW';
UPDATE tickets SET status = 'Done'        WHERE status = 'DONE';

UPDATE ticket_status_changes SET from_status = 'Backlog'     WHERE from_status = 'BACKLOG';
UPDATE ticket_status_changes SET from_status = 'To Do'       WHERE from_status = 'TODO';
UPDATE ticket_status_changes SET from_status = 'In Progress' WHERE from_status = 'IN_PROGRESS';
UPDATE ticket_status_changes SET from_status = 'In Review'   WHERE from_status = 'IN_REVIEW';
UPDATE ticket_status_changes SET from_status = 'Done'        WHERE from_status = 'DONE';
UPDATE ticket_status_changes SET to_status = 'Backlog'       WHERE to_status = 'BACKLOG';
UPDATE ticket_status_changes SET to_status = 'To Do'         WHERE to_status = 'TODO';
UPDATE ticket_status_changes SET to_status = 'In Progress'   WHERE to_status = 'IN_PROGRESS';
UPDATE ticket_status_changes SET to_status = 'In Review'     WHERE to_status = 'IN_REVIEW';
UPDATE ticket_status_changes SET to_status = 'Done'          WHERE to_status = 'DONE';

-- Every project that already exists keeps exactly the board it had.
INSERT INTO project_statuses (project_id, name, lane_order, initial_lane, done_lane)
SELECT p.id, 'Backlog', 0, TRUE, FALSE FROM projects p;
INSERT INTO project_statuses (project_id, name, lane_order, initial_lane, done_lane)
SELECT p.id, 'To Do', 1, FALSE, FALSE FROM projects p;
INSERT INTO project_statuses (project_id, name, lane_order, initial_lane, done_lane)
SELECT p.id, 'In Progress', 2, FALSE, FALSE FROM projects p;
INSERT INTO project_statuses (project_id, name, lane_order, initial_lane, done_lane)
SELECT p.id, 'In Review', 3, FALSE, FALSE FROM projects p;
INSERT INTO project_statuses (project_id, name, lane_order, initial_lane, done_lane)
SELECT p.id, 'Done', 4, FALSE, TRUE FROM projects p;

-- Built-in templates. Deliberately drawn from unlike kinds of work, because the point of the
-- feature is that a board is not always a software board.
INSERT INTO project_templates (name, description, built_in, created_at, updated_at) VALUES
    ('Kanban', 'A plain three-lane board. A good default when nothing more specific fits.',
     TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('Software Development', 'The classic engineering flow, from backlog through review to done.',
     TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('Legal Case', 'Stages of a matter, from intake through discovery and hearing to closed.',
     TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('Trip Planning', 'Turning ideas into bookings: what to research, what is booked, what is packed.',
     TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('Recruitment', 'A hiring pipeline, from applied through interviews to a decision.',
     TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO project_template_lanes (template_id, name, lane_order, initial_lane, done_lane)
SELECT t.id, v.name, v.lane_order, v.initial_lane, v.done_lane
FROM project_templates t
JOIN (
    SELECT 'Kanban' AS tpl, 'To Do' AS name, 0 AS lane_order, TRUE AS initial_lane, FALSE AS done_lane
    UNION ALL SELECT 'Kanban', 'In Progress', 1, FALSE, FALSE
    UNION ALL SELECT 'Kanban', 'Done', 2, FALSE, TRUE

    UNION ALL SELECT 'Software Development', 'Backlog', 0, TRUE, FALSE
    UNION ALL SELECT 'Software Development', 'To Do', 1, FALSE, FALSE
    UNION ALL SELECT 'Software Development', 'In Progress', 2, FALSE, FALSE
    UNION ALL SELECT 'Software Development', 'In Review', 3, FALSE, FALSE
    UNION ALL SELECT 'Software Development', 'Done', 4, FALSE, TRUE

    UNION ALL SELECT 'Legal Case', 'Intake', 0, TRUE, FALSE
    UNION ALL SELECT 'Legal Case', 'Discovery', 1, FALSE, FALSE
    UNION ALL SELECT 'Legal Case', 'Filings Due', 2, FALSE, FALSE
    UNION ALL SELECT 'Legal Case', 'Awaiting Hearing', 3, FALSE, FALSE
    UNION ALL SELECT 'Legal Case', 'Closed', 4, FALSE, TRUE

    UNION ALL SELECT 'Trip Planning', 'Ideas', 0, TRUE, FALSE
    UNION ALL SELECT 'Trip Planning', 'Researching', 1, FALSE, FALSE
    UNION ALL SELECT 'Trip Planning', 'Booked', 2, FALSE, FALSE
    UNION ALL SELECT 'Trip Planning', 'Packed', 3, FALSE, FALSE
    UNION ALL SELECT 'Trip Planning', 'Done', 4, FALSE, TRUE

    UNION ALL SELECT 'Recruitment', 'Applied', 0, TRUE, FALSE
    UNION ALL SELECT 'Recruitment', 'Screening', 1, FALSE, FALSE
    UNION ALL SELECT 'Recruitment', 'Interviewing', 2, FALSE, FALSE
    UNION ALL SELECT 'Recruitment', 'Offer', 3, FALSE, FALSE
    UNION ALL SELECT 'Recruitment', 'Decided', 4, FALSE, TRUE
) v ON v.tpl = t.name;

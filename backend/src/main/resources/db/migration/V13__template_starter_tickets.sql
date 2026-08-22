-- Templates gain starter tickets: the work a project of this kind always begins with.
--
-- A blank board is a poor start for repeatable work. A legal matter always opens with a
-- conflict check; a trip always needs somewhere to keep the booking confirmations. Those are
-- properties of the kind of project, which is exactly what a template is.
--
-- lane_name is a plain string, not a foreign key to project_template_lanes: it has to survive
-- the lane being renamed in the same submission, and it is resolved against the project's own
-- lanes at creation time anyway. A blank one means the board's starting lane.

CREATE TABLE project_template_tickets (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    template_id  BIGINT       NOT NULL,
    title        VARCHAR(200) NOT NULL,
    description  VARCHAR(4000) NULL,
    type         VARCHAR(20)  NOT NULL,
    priority     VARCHAR(20)  NOT NULL,
    lane_name    VARCHAR(60)  NULL,
    ticket_order INT          NOT NULL,
    CONSTRAINT pk_project_template_tickets PRIMARY KEY (id),
    CONSTRAINT fk_template_tickets_template FOREIGN KEY (template_id)
        REFERENCES project_templates (id) ON DELETE CASCADE
);

CREATE INDEX idx_template_tickets_order ON project_template_tickets (template_id, ticket_order);

-- Starters for the built-in templates. Kanban deliberately gets none: it is the "nothing more
-- specific fits" board, and prescribing work would defeat that.
INSERT INTO project_template_tickets (template_id, title, description, type, priority, lane_name, ticket_order)
SELECT t.id, v.title, v.description, v.type, v.priority, v.lane_name, v.ticket_order
FROM project_templates t
JOIN (
    SELECT 'Software Development' AS tpl, 'Set up the repository and CI' AS title,
           'Branch protection, a green pipeline on **main**, and a README that says how to run it.' AS description,
           'TASK' AS type, 'HIGH' AS priority, 'To Do' AS lane_name, 0 AS ticket_order
    UNION ALL SELECT 'Software Development', 'Agree the definition of done',
        'What has to be true before a ticket reaches the finished lane: review, tests, docs.',
        'TASK', 'MEDIUM', 'Backlog', 1
    UNION ALL SELECT 'Software Development', 'Write the first release notes',
        'Even an empty release deserves a page to add to.', 'TASK', 'LOW', 'Backlog', 2

    UNION ALL SELECT 'Legal Case', 'Run the conflict check',
        'Confirm no conflict of interest before any substantive work. **Attach the signed check.**',
        'TASK', 'HIGHEST', 'Intake', 0
    UNION ALL SELECT 'Legal Case', 'Engagement letter signed and filed',
        'Attach the countersigned letter here so the whole matter can point at one copy.',
        'TASK', 'HIGH', 'Intake', 1
    UNION ALL SELECT 'Legal Case', 'Client documents',
        'The home for everything the client sends: contracts, correspondence, evidence. Attach as they arrive.',
        'TASK', 'MEDIUM', 'Intake', 2
    UNION ALL SELECT 'Legal Case', 'Key dates and limitation period',
        'Diarise the deadlines that cannot be missed, with the limitation date first.',
        'TASK', 'HIGHEST', 'Intake', 3

    UNION ALL SELECT 'Trip Planning', 'Travel documents',
        'Booking confirmations, tickets, insurance and visas. **Attach each one as it is booked** so the whole trip has a single place to look.',
        'TASK', 'HIGHEST', 'Ideas', 0
    UNION ALL SELECT 'Trip Planning', 'Check passports and visas',
        'Expiry dates, and whether anyone needs a visa. Do this first: it has the longest lead time.',
        'TASK', 'HIGH', 'Ideas', 1
    UNION ALL SELECT 'Trip Planning', 'Book transport',
        'Flights, trains, transfers.', 'TASK', 'HIGH', 'Ideas', 2
    UNION ALL SELECT 'Trip Planning', 'Book accommodation',
        'Where you sleep each night of the trip.', 'TASK', 'HIGH', 'Ideas', 3
    UNION ALL SELECT 'Trip Planning', 'Packing list',
        'Built up over the weeks before, not the night before.', 'TASK', 'LOW', 'Ideas', 4

    UNION ALL SELECT 'Recruitment', 'Write the job description',
        'What the role does, what it needs, and what it pays.', 'TASK', 'HIGH', 'Applied', 0
    UNION ALL SELECT 'Recruitment', 'Agree the interview loop',
        'Who interviews, in what order, and what each stage is actually testing.',
        'TASK', 'MEDIUM', 'Applied', 1
    UNION ALL SELECT 'Recruitment', 'Agree the scorecard',
        'Decide how candidates are assessed **before** meeting any of them.',
        'TASK', 'MEDIUM', 'Applied', 2
) v ON v.tpl = t.name;

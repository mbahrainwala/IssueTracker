-- Corrects the Recruitment template, and gives its starter tickets a home that fits them.
--
-- Recruitment's lanes are Applied -> Screening -> Interviewing -> Offer -> Decided: on that
-- board a ticket IS a candidate, moving through the pipeline. The starter tickets it shipped
-- with ("Write the job description", "Agree the interview loop", "Agree the scorecard") are
-- the work of opening a vacancy, not candidates - dropped into Applied they would sit there
-- pretending to be applicants.
--
-- So Recruitment loses its starters entirely: you cannot pre-create the people who apply. The
-- setup work moves to Job Opening, a project for getting a vacancy written, approved and
-- advertised, which is a different piece of work with a different shape.

DELETE FROM project_template_tickets
WHERE template_id = (SELECT id FROM project_templates WHERE name = 'Recruitment');

INSERT INTO project_templates (name, description, built_in, created_at, updated_at) VALUES
    ('Job Opening',
     'Getting a vacancy written, approved and advertised. Pairs with Recruitment, which tracks the candidates it attracts.',
     TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO project_template_lanes (template_id, name, lane_order, initial_lane, done_lane)
SELECT t.id, v.name, v.lane_order, v.initial_lane, v.done_lane
FROM project_templates t
JOIN (
    SELECT 'Drafting' AS name, 0 AS lane_order, TRUE AS initial_lane, FALSE AS done_lane
    UNION ALL SELECT 'In Review', 1, FALSE, FALSE
    UNION ALL SELECT 'Approved', 2, FALSE, FALSE
    UNION ALL SELECT 'Advertised', 3, FALSE, FALSE
    UNION ALL SELECT 'Closed', 4, FALSE, TRUE
) v ON t.name = 'Job Opening';

INSERT INTO project_template_tickets (template_id, title, description, type, priority, lane_name, ticket_order)
SELECT t.id, v.title, v.description, v.type, v.priority, v.lane_name, v.ticket_order
FROM project_templates t
JOIN (
    SELECT 'Write the job description' AS title,
           'What the role does, what it needs, and what it does not. **Attach the final version** once it is agreed.' AS description,
           'TASK' AS type, 'HIGH' AS priority, 'Drafting' AS lane_name, 0 AS ticket_order
    UNION ALL SELECT 'Agree the salary band and headcount approval',
        'Signed off before the ad goes out, not after a candidate asks.',
        'TASK', 'HIGHEST', 'Drafting', 1
    UNION ALL SELECT 'Agree the interview loop',
        'Who interviews, in what order, and what each stage is actually testing.',
        'TASK', 'MEDIUM', 'Drafting', 2
    UNION ALL SELECT 'Agree the scorecard',
        'Decide how candidates are assessed **before** meeting any of them.',
        'TASK', 'MEDIUM', 'Drafting', 3
    UNION ALL SELECT 'Choose where to advertise',
        'Job boards, referrals, agencies - and what each costs.',
        'TASK', 'MEDIUM', 'In Review', 4
) v ON t.name = 'Job Opening';

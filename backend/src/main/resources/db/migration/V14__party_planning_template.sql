-- A built-in template for planning a party.
--
-- Added after the fact rather than folded into V12, because V12 has already been applied on
-- running installations and an applied migration is never edited. Same shape as the others:
-- a template, its lanes, and the work this kind of project always begins with.
--
-- The lanes follow how a party is actually organised - a pile of ideas, a list of things that
-- must be booked, the things now confirmed, what happens on the day - rather than a generic
-- to-do/doing/done, which is the whole point of templates being configurable.

INSERT INTO project_templates (name, description, built_in, created_at, updated_at) VALUES
    ('Party Planning',
     'Organising an event: guests, budget, the things that must be booked, and what happens on the day.',
     TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO project_template_lanes (template_id, name, lane_order, initial_lane, done_lane)
SELECT t.id, v.name, v.lane_order, v.initial_lane, v.done_lane
FROM project_templates t
JOIN (
    SELECT 'Ideas' AS name, 0 AS lane_order, TRUE AS initial_lane, FALSE AS done_lane
    UNION ALL SELECT 'To Book', 1, FALSE, FALSE
    UNION ALL SELECT 'Booked', 2, FALSE, FALSE
    UNION ALL SELECT 'On The Day', 3, FALSE, FALSE
    UNION ALL SELECT 'Done', 4, FALSE, TRUE
) v ON t.name = 'Party Planning';

INSERT INTO project_template_tickets (template_id, title, description, type, priority, lane_name, ticket_order)
SELECT t.id, v.title, v.description, v.type, v.priority, v.lane_name, v.ticket_order
FROM project_templates t
JOIN (
    SELECT 'Guest list and invitations' AS title,
           'Who is coming, how they are invited, and who has replied. **Attach the RSVP list** so there is one copy everybody works from.' AS description,
           'TASK' AS type, 'HIGHEST' AS priority, 'Ideas' AS lane_name, 0 AS ticket_order
    UNION ALL SELECT 'Set the budget',
        'What the whole thing may cost, and roughly how it splits across venue, food and everything else. Decide this before booking anything.',
        'TASK', 'HIGHEST', 'Ideas', 1
    UNION ALL SELECT 'Pick the date and time',
        'Check it against anything else the key guests cannot miss.',
        'TASK', 'HIGH', 'Ideas', 2
    UNION ALL SELECT 'Book the venue',
        'Confirm capacity, access times for setting up, and what they do and do not provide. **Attach the contract and the deposit receipt.**',
        'TASK', 'HIGHEST', 'To Book', 3
    UNION ALL SELECT 'Arrange food and drink',
        'Catering or self-catered, and **the dietary requirements and allergies from the guest list** - collect them early, not on the day.',
        'TASK', 'HIGH', 'To Book', 4
    UNION ALL SELECT 'Arrange music and entertainment',
        'Band, DJ or a playlist, plus whatever they need: power, space, a sound limit the venue enforces.',
        'TASK', 'MEDIUM', 'To Book', 5
    UNION ALL SELECT 'Cake and decorations',
        'Ordered in good time, and someone named to collect them.',
        'TASK', 'MEDIUM', 'To Book', 6
    UNION ALL SELECT 'Photography',
        'A hired photographer, or simply somebody asked in advance to take pictures.',
        'TASK', 'LOW', 'To Book', 7
    UNION ALL SELECT 'Running order for the day',
        'When the setup starts, when guests arrive, when the food, the speeches and the cake happen, and when it ends. **Attach the final version** and share it with anyone helping.',
        'TASK', 'HIGH', 'On The Day', 8
    UNION ALL SELECT 'Send thank-you notes',
        'The last thing, and the one most easily forgotten.',
        'TASK', 'LOW', 'Ideas', 9
) v ON t.name = 'Party Planning';

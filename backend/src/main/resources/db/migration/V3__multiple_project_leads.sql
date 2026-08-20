-- Leadership becomes a membership role instead of a column on the project.
--
-- projects.lead_id allowed exactly one lead per project and duplicated information already
-- expressible as project_members.project_role = 'LEAD', leaving two sources of truth that
-- could disagree. Folding it into project_members lets a project have several leads and a
-- user lead several projects, with one place to look.

-- 1. Promote an existing membership row where the lead is already a member.
UPDATE project_members pm
SET pm.project_role = 'LEAD'
WHERE EXISTS (
    SELECT 1 FROM projects p
    WHERE p.id = pm.project_id
      AND p.lead_id = pm.user_id
);

-- 2. Add a row for any lead that has no membership row yet.
INSERT INTO project_members (project_id, user_id, project_role)
SELECT p.id, p.lead_id, 'LEAD'
FROM projects p
WHERE p.lead_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM project_members pm
      WHERE pm.project_id = p.id
        AND pm.user_id = p.lead_id
  );

-- 3. Retire the column. MySQL requires the constraint to go first, and H2 in MySQL mode
--    accepts the same DROP FOREIGN KEY syntax.
ALTER TABLE projects DROP FOREIGN KEY fk_projects_lead;
ALTER TABLE projects DROP COLUMN lead_id;

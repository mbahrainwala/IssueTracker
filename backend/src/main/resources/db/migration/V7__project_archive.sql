-- Projects archive the same way tickets do: archived_at is both the flag and the timestamp,
-- and NULL means active. Archiving is reversible; deleting a project is not.

ALTER TABLE projects ADD COLUMN archived_at TIMESTAMP NULL;
ALTER TABLE projects ADD COLUMN archived_by_id BIGINT NULL;

ALTER TABLE projects
    ADD CONSTRAINT fk_projects_archived_by FOREIGN KEY (archived_by_id) REFERENCES users (id);

CREATE INDEX idx_projects_archived ON projects (archived_at);

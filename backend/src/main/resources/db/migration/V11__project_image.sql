-- An optional picture for each project, shown on its tile and beside its name.
--
-- Only the metadata is here. The image is a file under app.projects.image-directory named
-- after the project id, so a project has at most one and replacing it overwrites in place -
-- there is no key to store and no way to leave an orphan behind.
--
-- image_content_type doubles as the "is there one?" flag, and image_updated_at as the
-- cache-busting version the browser sees in the URL.

ALTER TABLE projects ADD COLUMN image_content_type VARCHAR(100) NULL;
ALTER TABLE projects ADD COLUMN image_filename VARCHAR(255) NULL;
ALTER TABLE projects ADD COLUMN image_updated_at TIMESTAMP NULL;

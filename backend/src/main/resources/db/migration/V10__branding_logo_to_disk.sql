-- The logo moves out of the database and onto disk, under app.branding.directory.
--
-- The metadata columns are cleared alongside dropping the bytes on purpose: they are what
-- `hasLogo` is computed from, so leaving them set would advertise a logo that no longer has
-- any bytes behind it and hand every caller a 404. Any installation that had already uploaded
-- one re-uploads it; the file did not exist on disk to migrate.

ALTER TABLE app_branding DROP COLUMN logo;

UPDATE app_branding
SET logo_content_type = NULL,
    logo_filename     = NULL,
    logo_updated_at   = NULL
WHERE id = 1;

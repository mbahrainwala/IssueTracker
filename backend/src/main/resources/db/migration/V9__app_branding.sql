-- Company name and logo shown in the title bar. One row, id 1, seeded here so nothing has to
-- decide later whether it exists.
--
-- The logo lives in the database rather than the attachment store on purpose: it is a single
-- small image, it needs no mounted volume to survive a redeploy, and the nightly orphan sweep
-- only knows about files an attachment row points at - a logo sitting in that directory would
-- look exactly like an orphan and be swept away.

CREATE TABLE app_branding (
    id                BIGINT       NOT NULL,
    company_name      VARCHAR(120) NULL,
    logo              LONGBLOB     NULL,
    logo_content_type VARCHAR(100) NULL,
    logo_filename     VARCHAR(255) NULL,
    logo_updated_at   TIMESTAMP    NULL,
    updated_at        TIMESTAMP    NOT NULL,
    updated_by_id     BIGINT       NULL,
    CONSTRAINT pk_app_branding PRIMARY KEY (id),
    CONSTRAINT fk_branding_user FOREIGN KEY (updated_by_id) REFERENCES users (id)
);

INSERT INTO app_branding (id, updated_at) VALUES (1, CURRENT_TIMESTAMP);

package behrainwala.issuetracker.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * The one row of installation-wide branding. There is exactly one, seeded by the migration
 * with {@link #SINGLETON_ID}, so callers read and update rather than create.
 * <p>
 * Both fields are optional: with no company name the app calls itself Issue Tracker, and with
 * no logo it draws its own mark.
 */
@Entity
@Table(name = "app_branding")
public class Branding {

    public static final long SINGLETON_ID = 1L;

    @Id
    private Long id = SINGLETON_ID;

    @Column(name = "company_name", length = 120)
    private String companyName;

    // The logo bytes live in this table's `logo` column but are deliberately NOT mapped here;
    // BrandingLogoStore reads and writes them over plain JDBC. See that class for why.

    @Column(name = "logo_content_type", length = 100)
    private String logoContentType;

    @Column(name = "logo_filename", length = 255)
    private String logoFilename;

    /** Doubles as the cache-busting version the browser sees in the logo URL. */
    @Column(name = "logo_updated_at")
    private Instant logoUpdatedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by_id")
    private User updatedBy;

    protected Branding() {
    }

    public void setCompanyName(String companyName, User by) {
        this.companyName = companyName;
        touch(by);
    }

    /** Records that a logo is present. The bytes themselves go through BrandingLogoStore. */
    public void setLogoMetadata(String contentType, String filename, User by) {
        this.logoContentType = contentType;
        this.logoFilename = filename;
        this.logoUpdatedAt = Instant.now();
        touch(by);
    }

    public void clearLogoMetadata(User by) {
        this.logoContentType = null;
        this.logoFilename = null;
        this.logoUpdatedAt = null;
        touch(by);
    }

    private void touch(User by) {
        this.updatedAt = Instant.now();
        this.updatedBy = by;
    }

    public Long getId() {
        return id;
    }

    public String getCompanyName() {
        return companyName;
    }

    public String getLogoContentType() {
        return logoContentType;
    }

    public String getLogoFilename() {
        return logoFilename;
    }

    public Instant getLogoUpdatedAt() {
        return logoUpdatedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public User getUpdatedBy() {
        return updatedBy;
    }
}

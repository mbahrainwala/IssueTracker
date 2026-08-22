package behrainwala.issuetracker.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "projects")
public class Project extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Uppercase short code that prefixes every ticket key, e.g. PROJ1 -> PROJ1-1232. */
    @Column(name = "project_key", nullable = false, unique = true, length = 10)
    private String projectKey;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 4000)
    private String description;

    /** Non-null once archived; doubles as the flag and the "when". */
    @Column(name = "archived_at")
    private Instant archivedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "archived_by_id")
    private User archivedBy;

    /**
     * The project picture is a file on disk named after this project's id; these columns are
     * all the database keeps. A non-null content type is what "has an image" means.
     */
    @Column(name = "image_content_type", length = 100)
    private String imageContentType;

    @Column(name = "image_filename", length = 255)
    private String imageFilename;

    /** Doubles as the cache-busting version the browser sees in the image URL. */
    @Column(name = "image_updated_at")
    private Instant imageUpdatedAt;

    /** Last issued ticket number for this project; incremented under a row lock. */
    @Column(name = "ticket_seq", nullable = false)
    private long ticketSeq = 0L;

    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private Set<ProjectMember> members = new LinkedHashSet<>();

    protected Project() {
    }

    public Project(String projectKey, String name, String description) {
        this.projectKey = projectKey;
        this.name = name;
        this.description = description;
    }

    /**
     * A project may have several leads, and leadership is held as a membership role rather
     * than a column here, so there is a single place to look.
     */
    public List<User> getLeads() {
        return members.stream()
                .filter(m -> m.getProjectRole() == ProjectRole.LEAD)
                .map(ProjectMember::getUser)
                .sorted(Comparator.comparing(User::getDisplayName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public long nextTicketNumber() {
        return ++ticketSeq;
    }

    public Long getId() {
        return id;
    }

    public String getProjectKey() {
        return projectKey;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isArchived() {
        return archivedAt != null;
    }

    public void archive(User by) {
        this.archivedAt = Instant.now();
        this.archivedBy = by;
    }

    public void restore() {
        this.archivedAt = null;
        this.archivedBy = null;
    }

    /** Records that a picture is present. The bytes themselves go through ProjectImageStore. */
    public void setImageMetadata(String contentType, String filename) {
        this.imageContentType = contentType;
        this.imageFilename = filename;
        this.imageUpdatedAt = Instant.now();
    }

    public void clearImageMetadata() {
        this.imageContentType = null;
        this.imageFilename = null;
        this.imageUpdatedAt = null;
    }

    public boolean hasImage() {
        return imageContentType != null;
    }

    public String getImageContentType() {
        return imageContentType;
    }

    public String getImageFilename() {
        return imageFilename;
    }

    public Instant getImageUpdatedAt() {
        return imageUpdatedAt;
    }

    public Instant getArchivedAt() {
        return archivedAt;
    }

    public User getArchivedBy() {
        return archivedBy;
    }

    public long getTicketSeq() {
        return ticketSeq;
    }

    public Set<ProjectMember> getMembers() {
        return members;
    }
}

package behrainwala.issuetracker.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "tickets")
public class Ticket extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(name = "ticket_number", nullable = false)
    private long ticketNumber;

    /** Denormalised "PROJ1-1232" so lookups and search stay a single indexed read. */
    @Column(name = "ticket_key", nullable = false, unique = true, length = 64)
    private String ticketKey;

    @Column(nullable = false)
    private String title;

    @Column(length = 4000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TicketType type = TicketType.TASK;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TicketStatus status = TicketStatus.BACKLOG;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TicketPriority priority = TicketPriority.MEDIUM;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reporter_id", nullable = false)
    private User reporter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id")
    private User assignee;

    /**
     * The epic this ticket belongs to, or null. Always a ticket of type EPIC in the same
     * project; epics themselves never have one.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "epic_id")
    private Ticket epic;

    /** Non-null once archived; doubles as the flag and the "when". */
    @Column(name = "archived_at")
    private Instant archivedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "archived_by_id")
    private User archivedBy;

    @Column(name = "story_points")
    private Integer storyPoints;

    @Column(name = "due_date")
    private LocalDate dueDate;

    protected Ticket() {
    }

    public Ticket(Project project, long ticketNumber, String title, User reporter) {
        this.project = project;
        this.ticketNumber = ticketNumber;
        this.ticketKey = project.getProjectKey() + "-" + ticketNumber;
        this.title = title;
        this.reporter = reporter;
    }

    public Long getId() {
        return id;
    }

    public Project getProject() {
        return project;
    }

    public long getTicketNumber() {
        return ticketNumber;
    }

    public String getTicketKey() {
        return ticketKey;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public TicketType getType() {
        return type;
    }

    public void setType(TicketType type) {
        this.type = type;
    }

    public TicketStatus getStatus() {
        return status;
    }

    public void setStatus(TicketStatus status) {
        this.status = status;
    }

    public TicketPriority getPriority() {
        return priority;
    }

    public void setPriority(TicketPriority priority) {
        this.priority = priority;
    }

    public User getReporter() {
        return reporter;
    }

    public User getAssignee() {
        return assignee;
    }

    public void setAssignee(User assignee) {
        this.assignee = assignee;
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

    public Instant getArchivedAt() {
        return archivedAt;
    }

    public User getArchivedBy() {
        return archivedBy;
    }

    public Ticket getEpic() {
        return epic;
    }

    public void setEpic(Ticket epic) {
        this.epic = epic;
    }

    public Integer getStoryPoints() {
        return storyPoints;
    }

    public void setStoryPoints(Integer storyPoints) {
        this.storyPoints = storyPoints;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }
}

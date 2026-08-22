package behrainwala.issuetracker.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/** One move of a ticket from one status bucket to another. */
@Entity
@Table(name = "ticket_status_changes")
public class TicketStatusChange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false)
    private Ticket ticket;

    /**
     * Lane names as they read at the time of the move - a snapshot, not a reference. A lane
     * can later be renamed or deleted; what the trail says happened must not change with it.
     */
    @Column(name = "from_status", nullable = false, length = 60)
    private String fromStatus;

    @Column(name = "to_status", nullable = false, length = 60)
    private String toStatus;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "moved_by_id", nullable = false)
    private User movedBy;

    @Column(name = "moved_at", nullable = false)
    private Instant movedAt;

    protected TicketStatusChange() {
    }

    public TicketStatusChange(Ticket ticket, String fromStatus, String toStatus, User movedBy) {
        this.ticket = ticket;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.movedBy = movedBy;
        this.movedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Ticket getTicket() {
        return ticket;
    }

    public String getFromStatus() {
        return fromStatus;
    }

    public String getToStatus() {
        return toStatus;
    }

    public User getMovedBy() {
        return movedBy;
    }

    public Instant getMovedAt() {
        return movedAt;
    }
}

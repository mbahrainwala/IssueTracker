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

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", nullable = false, length = 20)
    private TicketStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 20)
    private TicketStatus toStatus;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "moved_by_id", nullable = false)
    private User movedBy;

    @Column(name = "moved_at", nullable = false)
    private Instant movedAt;

    protected TicketStatusChange() {
    }

    public TicketStatusChange(Ticket ticket, TicketStatus fromStatus, TicketStatus toStatus, User movedBy) {
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

    public TicketStatus getFromStatus() {
        return fromStatus;
    }

    public TicketStatus getToStatus() {
        return toStatus;
    }

    public User getMovedBy() {
        return movedBy;
    }

    public Instant getMovedAt() {
        return movedAt;
    }
}

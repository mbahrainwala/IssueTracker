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

/**
 * Somebody was named with {@code @username} in a comment or a ticket description, and has not
 * yet said they have seen it.
 * <p>
 * Acknowledging is deliberately not a button that just dismisses the flag - it requires a
 * comment, so "I have read this" leaves a trace on the ticket that everyone else can see. The
 * comment they wrote is kept in {@link #getAcknowledgement()}.
 */
@Entity
@Table(name = "ticket_mentions")
public class TicketMention {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false)
    private Ticket ticket;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mentioned_user_id", nullable = false)
    private User mentionedUser;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mentioned_by_id", nullable = false)
    private User mentionedBy;

    /** Null when the mention came from the ticket description rather than a comment. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_comment_id")
    private Comment sourceComment;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    /** Non-null once acknowledged; doubles as the flag and the "when". */
    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "acknowledgement_id")
    private Comment acknowledgement;

    protected TicketMention() {
    }

    public TicketMention(Ticket ticket, User mentionedUser, User mentionedBy, Comment sourceComment) {
        this.ticket = ticket;
        this.mentionedUser = mentionedUser;
        this.mentionedBy = mentionedBy;
        this.sourceComment = sourceComment;
    }

    public void acknowledge(Comment comment) {
        this.acknowledgedAt = Instant.now();
        this.acknowledgement = comment;
    }

    public boolean isOutstanding() {
        return acknowledgedAt == null;
    }

    public Long getId() {
        return id;
    }

    public Ticket getTicket() {
        return ticket;
    }

    public User getMentionedUser() {
        return mentionedUser;
    }

    public User getMentionedBy() {
        return mentionedBy;
    }

    public Comment getSourceComment() {
        return sourceComment;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getAcknowledgedAt() {
        return acknowledgedAt;
    }

    public Comment getAcknowledgement() {
        return acknowledgement;
    }
}

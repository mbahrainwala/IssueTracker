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

@Entity
@Table(name = "ticket_links")
public class TicketLink extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_ticket_id", nullable = false)
    private Ticket source;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "target_ticket_id", nullable = false)
    private Ticket target;

    @Enumerated(EnumType.STRING)
    @Column(name = "link_type", nullable = false, length = 30)
    private LinkType linkType;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_id", nullable = false)
    private User createdBy;

    protected TicketLink() {
    }

    public TicketLink(Ticket source, Ticket target, LinkType linkType, User createdBy) {
        this.source = source;
        this.target = target;
        this.linkType = linkType;
        this.createdBy = createdBy;
    }

    public Long getId() {
        return id;
    }

    public Ticket getSource() {
        return source;
    }

    public Ticket getTarget() {
        return target;
    }

    public LinkType getLinkType() {
        return linkType;
    }

    public User getCreatedBy() {
        return createdBy;
    }
}

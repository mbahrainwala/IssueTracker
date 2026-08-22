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

/**
 * A ticket a template creates for every project made from it - the work this kind of project
 * always begins with.
 * <p>
 * {@link #getLaneName()} is a plain string rather than a link to a template lane, because the
 * lane it names has to survive being renamed in the same submission, and it is resolved
 * against the <em>project's</em> lanes at creation time regardless. Blank means "wherever this
 * board starts".
 */
@Entity
@Table(name = "project_template_tickets")
public class TemplateTicket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "template_id", nullable = false)
    private ProjectTemplate template;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 4000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TicketType type = TicketType.TASK;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TicketPriority priority = TicketPriority.MEDIUM;

    @Column(name = "lane_name", length = 60)
    private String laneName;

    @Column(name = "ticket_order", nullable = false)
    private int ticketOrder;

    protected TemplateTicket() {
    }

    public TemplateTicket(ProjectTemplate template, String title, String description,
                          TicketType type, TicketPriority priority, String laneName, int ticketOrder) {
        this.template = template;
        this.title = title;
        this.description = description;
        this.type = type;
        this.priority = priority;
        this.laneName = laneName;
        this.ticketOrder = ticketOrder;
    }

    public Long getId() {
        return id;
    }

    public ProjectTemplate getTemplate() {
        return template;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public TicketType getType() {
        return type;
    }

    public TicketPriority getPriority() {
        return priority;
    }

    public String getLaneName() {
        return laneName;
    }

    public int getTicketOrder() {
        return ticketOrder;
    }
}

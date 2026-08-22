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
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import org.hibernate.annotations.BatchSize;

import java.util.ArrayList;
import java.util.List;

/**
 * A reusable blueprint for a project's board, defined once by an administrator.
 * <p>
 * A template is a starting point and nothing more: creating a project <em>copies</em> its
 * lanes. Editing the template afterwards never reaches back into projects already made from
 * it, so improving a template cannot rearrange a board somebody is working on.
 */
@Entity
@Table(name = "project_templates")
public class ProjectTemplate extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 1000)
    private String description;

    /** Ships with the app. Editable, but not deletable - the list must never be empty. */
    @Column(name = "built_in", nullable = false)
    private boolean builtIn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id")
    private User createdBy;

    @OneToMany(mappedBy = "template", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
    @OrderBy("laneOrder asc")
    private List<TemplateLane> lanes = new ArrayList<>();

    /**
     * Tickets every project made from this template starts with. May be empty.
     * <p>
     * Batch-loaded rather than fetch-joined: Hibernate refuses to fetch two list associations
     * in one query, and lanes are the one the picker always needs.
     */
    @BatchSize(size = 50)
    @OneToMany(mappedBy = "template", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
    @OrderBy("ticketOrder asc")
    private List<TemplateTicket> starterTickets = new ArrayList<>();

    protected ProjectTemplate() {
    }

    public ProjectTemplate(String name, String description, User createdBy) {
        this.name = name;
        this.description = description;
        this.createdBy = createdBy;
    }

    public Long getId() {
        return id;
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

    public boolean isBuiltIn() {
        return builtIn;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public List<TemplateLane> getLanes() {
        return lanes;
    }

    public List<TemplateTicket> getStarterTickets() {
        return starterTickets;
    }
}

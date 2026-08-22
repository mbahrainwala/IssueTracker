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

/**
 * One swim lane on one project's board.
 * <p>
 * The {@link #getName() name} is not a label for some hidden code - it <em>is</em> the value
 * tickets in that lane carry in {@code tickets.status}. A lane called "Awaiting Hearing" means
 * tickets read "Awaiting Hearing", which is why renaming one has to rewrite its tickets.
 */
@Entity
@Table(name = "project_statuses")
public class ProjectStatus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(nullable = false, length = 60)
    private String name;

    /** Left-to-right position on the board. Contiguous from 0, renumbered on every change. */
    @Column(name = "lane_order", nullable = false)
    private int laneOrder;

    /** Where a newly created ticket lands. Exactly one lane per project has this. */
    @Column(name = "initial_lane", nullable = false)
    private boolean initialLane;

    /**
     * Finished work. Exactly one lane per project has this, and it is what "only completed
     * tickets can be archived" now means - the DONE constant is gone.
     */
    @Column(name = "done_lane", nullable = false)
    private boolean doneLane;

    protected ProjectStatus() {
    }

    public ProjectStatus(Project project, String name, int laneOrder,
                         boolean initialLane, boolean doneLane) {
        this.project = project;
        this.name = name;
        this.laneOrder = laneOrder;
        this.initialLane = initialLane;
        this.doneLane = doneLane;
    }

    public Long getId() {
        return id;
    }

    public Project getProject() {
        return project;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getLaneOrder() {
        return laneOrder;
    }

    public void setLaneOrder(int laneOrder) {
        this.laneOrder = laneOrder;
    }

    public boolean isInitialLane() {
        return initialLane;
    }

    public void setInitialLane(boolean initialLane) {
        this.initialLane = initialLane;
    }

    public boolean isDoneLane() {
        return doneLane;
    }

    public void setDoneLane(boolean doneLane) {
        this.doneLane = doneLane;
    }
}

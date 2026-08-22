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

/** One lane in a template - the same shape as {@link ProjectStatus}, before it belongs to a project. */
@Entity
@Table(name = "project_template_lanes")
public class TemplateLane {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "template_id", nullable = false)
    private ProjectTemplate template;

    @Column(nullable = false, length = 60)
    private String name;

    @Column(name = "lane_order", nullable = false)
    private int laneOrder;

    @Column(name = "initial_lane", nullable = false)
    private boolean initialLane;

    @Column(name = "done_lane", nullable = false)
    private boolean doneLane;

    protected TemplateLane() {
    }

    public TemplateLane(ProjectTemplate template, String name, int laneOrder,
                        boolean initialLane, boolean doneLane) {
        this.template = template;
        this.name = name;
        this.laneOrder = laneOrder;
        this.initialLane = initialLane;
        this.doneLane = doneLane;
    }

    public Long getId() {
        return id;
    }

    public ProjectTemplate getTemplate() {
        return template;
    }

    public String getName() {
        return name;
    }

    public int getLaneOrder() {
        return laneOrder;
    }

    public boolean isInitialLane() {
        return initialLane;
    }

    public boolean isDoneLane() {
        return doneLane;
    }
}

package behrainwala.issuetracker.service;

import behrainwala.issuetracker.domain.Project;
import behrainwala.issuetracker.domain.ProjectStatus;
import behrainwala.issuetracker.domain.ProjectTemplate;
import behrainwala.issuetracker.domain.TemplateLane;
import behrainwala.issuetracker.dto.WorkflowDtos.LaneDto;
import behrainwala.issuetracker.dto.WorkflowDtos.LaneRequest;
import behrainwala.issuetracker.repo.ProjectStatusRepository;
import behrainwala.issuetracker.web.ConflictException;
import behrainwala.issuetracker.web.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * A project's swim lanes: what they are, what a ticket may be set to, and what counts as
 * finished. The one place that answers "is this a valid status?", now that the answer is a
 * query against the project rather than an enum constant.
 */
@Service
@Transactional(readOnly = true)
public class WorkflowService {

    private final ProjectStatusRepository statusRepository;

    public WorkflowService(ProjectStatusRepository statusRepository) {
        this.statusRepository = statusRepository;
    }

    public List<ProjectStatus> lanesOf(Project project) {
        return statusRepository.findByProjectIdOrderByLaneOrderAsc(project.getId());
    }

    public List<LaneDto> laneDtos(Project project) {
        return lanesOf(project).stream().map(LaneDto::from).toList();
    }

    /**
     * Copies a template's lanes onto a brand-new project. A copy, not a link: editing the
     * template afterwards must not rearrange a board people are already working on.
     */
    @Transactional
    public void applyTemplate(Project project, ProjectTemplate template) {
        List<TemplateLane> lanes = template.getLanes().stream()
                .sorted(java.util.Comparator.comparingInt(TemplateLane::getLaneOrder))
                .toList();
        int order = 0;
        for (TemplateLane lane : lanes) {
            statusRepository.save(new ProjectStatus(
                    project, lane.getName(), order++, lane.isInitialLane(), lane.isDoneLane()));
        }
    }

    /** Where a newly created ticket lands. */
    public ProjectStatus initialLane(Project project) {
        return statusRepository.findByProjectIdAndInitialLaneIsTrue(project.getId())
                .orElseThrow(() -> new IllegalStateException(
                        project.getProjectKey() + " has no starting lane"));
    }

    /**
     * Resolves a status the caller supplied against this project's board, case-insensitively,
     * and returns the lane's canonical name. An unknown value is a client error naming what
     * the board actually offers - the alternative is a ticket in a lane that does not exist.
     */
    public String requireLaneName(Project project, String status) {
        return statusRepository.findByProjectIdAndNameIgnoreCase(project.getId(), status)
                .map(ProjectStatus::getName)
                .orElseThrow(() -> new IllegalArgumentException(
                        "%s has no lane called \"%s\". Its lanes are: %s"
                                .formatted(project.getProjectKey(), status, laneNames(project))));
    }

    /**
     * The lane's canonical name if this project has it, otherwise the supplied fallback. For
     * seeding, where a lane the project lacks should place the ticket somewhere sensible
     * rather than abort the whole creation.
     */
    public String laneNameOrDefault(Project project, String status, String fallback) {
        return statusRepository.findByProjectIdAndNameIgnoreCase(project.getId(), status)
                .map(ProjectStatus::getName)
                .orElse(fallback);
    }

    /** True when the ticket's lane is the one this project treats as finished work. */
    public boolean isDone(Project project, String status) {
        return statusRepository.findByProjectIdAndDoneLaneIsTrue(project.getId())
                .map(lane -> lane.getName().equalsIgnoreCase(status))
                .orElse(false);
    }

    public String doneLaneName(Project project) {
        return statusRepository.findByProjectIdAndDoneLaneIsTrue(project.getId())
                .map(ProjectStatus::getName)
                .orElse("the finished lane");
    }

    /**
     * Replaces the whole board in one go: the submitted list is the new set of lanes, in the
     * new order. Sent whole rather than as add/rename/reorder/remove calls because a board is
     * edited as a shape - reordering is just the same names in a different sequence.
     * <p>
     * Lanes are matched to the existing ones <em>by id</em>, not by position. A lane that
     * keeps its id and changes its name is a rename, and its tickets follow it; a lane whose
     * id is absent from the submission is being removed, and must be empty first. Matching by
     * position instead would read the removal of a middle lane as a chain of renames and drag
     * every ticket one lane along with it.
     */
    @Transactional
    public List<LaneDto> replaceLanes(Project project, List<LaneRequest> requested) {
        validate(requested);

        Map<Long, ProjectStatus> existing = new LinkedHashMap<>();
        for (ProjectStatus lane : lanesOf(project)) {
            existing.put(lane.getId(), lane);
        }
        Set<Long> keptIds = new HashSet<>();

        for (int i = 0; i < requested.size(); i++) {
            LaneRequest request = requested.get(i);
            String name = request.name().trim();
            ProjectStatus lane = request.id() == null ? null : existing.get(request.id());

            if (lane == null) {
                if (request.id() != null) {
                    throw new IllegalArgumentException(
                            "Lane " + request.id() + " does not belong to " + project.getProjectKey());
                }
                statusRepository.save(new ProjectStatus(
                        project, name, i, request.initial(), request.done()));
                continue;
            }

            if (!lane.getName().equals(name)) {
                // The lane name is the value its tickets hold, so a rename rewrites them.
                statusRepository.renameTicketStatuses(project.getId(), lane.getName(), name);
                lane.setName(name);
            }
            lane.setLaneOrder(i);
            lane.setInitialLane(request.initial());
            lane.setDoneLane(request.done());
            keptIds.add(lane.getId());
        }

        for (ProjectStatus lane : existing.values()) {
            if (keptIds.contains(lane.getId())) {
                continue;
            }
            long tickets = statusRepository.countTicketsIn(project.getId(), lane.getName());
            if (tickets > 0) {
                throw new ConflictException(
                        "Lane \"%s\" still holds %d ticket%s - move them before removing it"
                                .formatted(lane.getName(), tickets, tickets == 1 ? "" : "s"));
            }
            statusRepository.delete(lane);
        }

        statusRepository.flush();
        return laneDtos(project);
    }

    /**
     * A board needs a starting lane and a finishing lane, and cannot have two of either:
     * without them "where do new tickets go" and "what may be archived" have no answer.
     */
    public static void validate(List<LaneRequest> lanes) {
        if (lanes == null || lanes.isEmpty()) {
            throw new IllegalArgumentException("A board needs at least one lane");
        }
        Set<String> seen = new HashSet<>();
        for (LaneRequest lane : lanes) {
            String name = lane.name() == null ? "" : lane.name().trim();
            if (name.isEmpty()) {
                throw new IllegalArgumentException("A lane needs a name");
            }
            if (!seen.add(name.toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("Two lanes cannot both be called \"" + name + "\"");
            }
        }
        long initial = lanes.stream().filter(LaneRequest::initial).count();
        if (initial != 1) {
            throw new IllegalArgumentException(
                    "Exactly one lane must be the starting lane, where new tickets appear");
        }
        long done = lanes.stream().filter(LaneRequest::done).count();
        if (done != 1) {
            throw new IllegalArgumentException(
                    "Exactly one lane must be the finished lane, the one tickets are archived from");
        }
    }

    private String laneNames(Project project) {
        List<ProjectStatus> lanes = lanesOf(project);
        if (lanes.isEmpty()) {
            throw new NotFoundException(project.getProjectKey() + " has no lanes configured");
        }
        return lanes.stream().map(ProjectStatus::getName).reduce((a, b) -> a + ", " + b).orElse("");
    }
}

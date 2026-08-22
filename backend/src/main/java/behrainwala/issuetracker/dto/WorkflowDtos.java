package behrainwala.issuetracker.dto;

import behrainwala.issuetracker.domain.ProjectStatus;
import behrainwala.issuetracker.domain.ProjectTemplate;
import behrainwala.issuetracker.domain.TemplateLane;
import behrainwala.issuetracker.domain.TemplateTicket;
import behrainwala.issuetracker.domain.TicketPriority;
import behrainwala.issuetracker.domain.TicketType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class WorkflowDtos {

    private WorkflowDtos() {
    }

    /**
     * One lane, as the board draws it. {@code name} is both the column heading and the value
     * tickets in it carry, so the client never has to translate between the two.
     */
    public record LaneDto(Long id, String name, int order, boolean initial, boolean done) {

        public static LaneDto from(ProjectStatus status) {
            return new LaneDto(status.getId(), status.getName(), status.getLaneOrder(),
                    status.isInitialLane(), status.isDoneLane());
        }

        public static LaneDto from(TemplateLane lane) {
            return new LaneDto(lane.getId(), lane.getName(), lane.getLaneOrder(),
                    lane.isInitialLane(), lane.isDoneLane());
        }
    }

    /**
     * A lane as the client submits it. The order is the position in the list rather than a
     * field, so a drag-and-drop reorder is just the array in its new sequence.
     * <p>
     * {@code id} identifies an existing lane and is what makes a rename distinguishable from
     * a removal-plus-addition. Without it the server could only match lanes by position, and
     * dropping a lane from the middle would read as a chain of renames - quietly dragging
     * every ticket one lane to the left. Null means a lane that did not exist before.
     */
    public record LaneRequest(
            Long id,
            @NotBlank @Size(max = 60) String name,
            boolean initial,
            boolean done) {
    }

    /** The whole board in one payload: reorder, rename, add and remove in a single save. */
    public record LanesRequest(@NotEmpty @Valid List<LaneRequest> lanes) {
    }

    /**
     * A ticket every project made from the template starts with. {@code lane} names one of the
     * template's own lanes; blank means the board's starting lane.
     */
    public record StarterTicketRequest(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 4000) String description,
            TicketType type,
            TicketPriority priority,
            @Size(max = 60) String lane) {
    }

    public record StarterTicketDto(
            Long id,
            String title,
            String description,
            TicketType type,
            TicketPriority priority,
            String lane) {

        public static StarterTicketDto from(TemplateTicket ticket) {
            return new StarterTicketDto(ticket.getId(), ticket.getTitle(), ticket.getDescription(),
                    ticket.getType(), ticket.getPriority(), ticket.getLaneName());
        }
    }

    public record TemplateRequest(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 1000) String description,
            @NotEmpty @Valid List<LaneRequest> lanes,
            /** Optional: a template may prescribe no starting work at all. */
            @Valid List<StarterTicketRequest> starterTickets) {
    }

    public record TemplateDto(
            Long id,
            String name,
            String description,
            boolean builtIn,
            List<LaneDto> lanes,
            List<StarterTicketDto> starterTickets,
            Instant createdAt) {

        public static TemplateDto from(ProjectTemplate template) {
            return new TemplateDto(
                    template.getId(),
                    template.getName(),
                    template.getDescription(),
                    template.isBuiltIn(),
                    template.getLanes().stream().map(LaneDto::from).toList(),
                    template.getStarterTickets().stream().map(StarterTicketDto::from).toList(),
                    template.getCreatedAt());
        }
    }
}

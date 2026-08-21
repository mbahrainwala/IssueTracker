package behrainwala.issuetracker.dto;

import behrainwala.issuetracker.domain.Ticket;
import behrainwala.issuetracker.domain.TicketPriority;
import behrainwala.issuetracker.domain.TicketStatus;
import behrainwala.issuetracker.domain.TicketType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class TicketDtos {

    private TicketDtos() {
    }

    public record CreateTicketRequest(
            @NotBlank @Size(max = 255) String title,
            @Size(max = 4000) String description,
            TicketType type,
            TicketStatus status,
            TicketPriority priority,
            Long assigneeId,
            Integer storyPoints,
            LocalDate dueDate,
            String epicKey) {
    }

    /** Every field is optional; nulls leave the current value untouched. */
    public record UpdateTicketRequest(
            @Size(max = 255) String title,
            @Size(max = 4000) String description,
            TicketType type,
            TicketStatus status,
            TicketPriority priority,
            Long assigneeId,
            boolean clearAssignee,
            Integer storyPoints,
            LocalDate dueDate,
            String epicKey,
            boolean clearEpic) {
    }

    /** Attaches existing tickets to an epic from the epic's own page. */
    public record AddChildrenRequest(
            @NotEmpty List<@NotBlank String> ticketKeys) {
    }

    /** Just enough of the parent epic to render a chip linking back to it. */
    public record EpicRefDto(Long id, String ticketKey, String title) {

        public static EpicRefDto from(Ticket epic) {
            return epic == null ? null : new EpicRefDto(epic.getId(), epic.getTicketKey(), epic.getTitle());
        }
    }

    public record TicketDto(
            Long id,
            String ticketKey,
            Long projectId,
            String projectKey,
            String title,
            String description,
            TicketType type,
            TicketStatus status,
            TicketPriority priority,
            UserDto reporter,
            UserDto assignee,
            EpicRefDto epic,
            boolean archived,
            Instant archivedAt,
            UserDto archivedBy,
            Integer storyPoints,
            LocalDate dueDate,
            Instant createdAt,
            Instant updatedAt) {

        public static TicketDto from(Ticket t) {
            return new TicketDto(
                    t.getId(),
                    t.getTicketKey(),
                    t.getProject().getId(),
                    t.getProject().getProjectKey(),
                    t.getTitle(),
                    t.getDescription(),
                    t.getType(),
                    t.getStatus(),
                    t.getPriority(),
                    UserDto.from(t.getReporter()),
                    UserDto.from(t.getAssignee()),
                    EpicRefDto.from(t.getEpic()),
                    t.isArchived(),
                    t.getArchivedAt(),
                    UserDto.from(t.getArchivedBy()),
                    t.getStoryPoints(),
                    t.getDueDate(),
                    t.getCreatedAt(),
                    t.getUpdatedAt());
        }
    }
}

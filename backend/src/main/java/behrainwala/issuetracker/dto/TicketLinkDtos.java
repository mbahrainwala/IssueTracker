package behrainwala.issuetracker.dto;

import behrainwala.issuetracker.domain.LinkType;
import behrainwala.issuetracker.domain.Ticket;
import behrainwala.issuetracker.domain.TicketPriority;
import behrainwala.issuetracker.domain.TicketType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public final class TicketLinkDtos {

    private TicketLinkDtos() {
    }

    public record CreateLinkRequest(
            @NotNull LinkType linkType,
            @NotBlank String targetTicketKey) {
    }

    /** Enough of the other ticket to render a link row without a second request. */
    public record LinkedTicketDto(
            Long id,
            String ticketKey,
            String projectKey,
            String title,
            TicketType type,
            String status,
            TicketPriority priority,
            UserDto assignee) {

        public static LinkedTicketDto from(Ticket t) {
            return new LinkedTicketDto(t.getId(), t.getTicketKey(), t.getProject().getProjectKey(),
                    t.getTitle(), t.getType(), t.getStatus(), t.getPriority(), UserDto.from(t.getAssignee()));
        }
    }

    /**
     * A link as seen from one particular ticket. {@code linkType} is already oriented for
     * the ticket being viewed, so the UI never has to work out the inverse itself.
     */
    public record TicketLinkDto(
            Long id,
            LinkType linkType,
            String label,
            LinkedTicketDto ticket) {
    }
}

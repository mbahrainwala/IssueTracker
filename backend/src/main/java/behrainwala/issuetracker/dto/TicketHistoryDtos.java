package behrainwala.issuetracker.dto;

import behrainwala.issuetracker.domain.TicketStatus;
import behrainwala.issuetracker.domain.TicketStatusChange;

import java.time.Instant;

public final class TicketHistoryDtos {

    private TicketHistoryDtos() {
    }

    /**
     * One recorded move. {@code summary} is the ready-made sentence
     * ("moved from Backlog to To Do by Alice Nguyen"), so every client renders it the same way;
     * the individual fields are there for anything that wants to format its own.
     */
    public record StatusChangeDto(
            Long id,
            TicketStatus fromStatus,
            TicketStatus toStatus,
            UserDto movedBy,
            Instant movedAt,
            String summary) {

        public static StatusChangeDto from(TicketStatusChange change) {
            String summary = "moved from %s to %s by %s".formatted(
                    change.getFromStatus().getLabel(),
                    change.getToStatus().getLabel(),
                    change.getMovedBy().getDisplayName());
            return new StatusChangeDto(
                    change.getId(),
                    change.getFromStatus(),
                    change.getToStatus(),
                    UserDto.from(change.getMovedBy()),
                    change.getMovedAt(),
                    summary);
        }
    }
}

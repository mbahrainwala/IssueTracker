package behrainwala.issuetracker.dto;

import behrainwala.issuetracker.domain.TicketMention;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class MentionDtos {

    private MentionDtos() {
    }

    /**
     * One outstanding mention. Carries enough to render a list without fetching each ticket:
     * where it is, who asked, and the text they wrote.
     */
    public record MentionDto(
            Long id,
            String ticketKey,
            String projectKey,
            String ticketTitle,
            UserDto mentionedBy,
            /** The comment the mention came from, or null when it was the description. */
            String excerpt,
            Instant mentionedAt) {

        /** Long comments are cut down: this is a prompt to go and read it, not the thing itself. */
        private static final int EXCERPT_LIMIT = 280;

        public static MentionDto from(TicketMention mention) {
            String body = mention.getSourceComment() == null
                    ? mention.getTicket().getDescription()
                    : mention.getSourceComment().getBody();
            return new MentionDto(
                    mention.getId(),
                    mention.getTicket().getTicketKey(),
                    mention.getTicket().getProject().getProjectKey(),
                    mention.getTicket().getTitle(),
                    UserDto.from(mention.getMentionedBy()),
                    excerpt(body),
                    mention.getCreatedAt());
        }

        private static String excerpt(String body) {
            if (body == null || body.isBlank()) {
                return null;
            }
            String trimmed = body.strip();
            return trimmed.length() <= EXCERPT_LIMIT
                    ? trimmed
                    : trimmed.substring(0, EXCERPT_LIMIT) + "…";
        }
    }

    /**
     * Acknowledging takes a comment, and the comment is required. The point of the feature is
     * that "I have seen this" is visible to the person who asked, which a silent dismissal
     * would not be.
     */
    public record AcknowledgeRequest(@NotBlank @Size(max = 4000) String body) {
    }
}

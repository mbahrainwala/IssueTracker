package behrainwala.issuetracker.dto;

import behrainwala.issuetracker.domain.Comment;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class CommentDtos {

    private CommentDtos() {
    }

    public record CommentRequest(@NotBlank @Size(max = 4000) String body) {
    }

    public record CommentDto(Long id, UserDto author, String body, Instant createdAt, Instant updatedAt) {

        public static CommentDto from(Comment c) {
            return new CommentDto(c.getId(), UserDto.from(c.getAuthor()), c.getBody(),
                    c.getCreatedAt(), c.getUpdatedAt());
        }
    }
}

package behrainwala.issuetracker.dto;

import behrainwala.issuetracker.domain.Attachment;

import java.time.Instant;

public final class AttachmentDtos {

    private AttachmentDtos() {
    }

    /**
     * What the ticket page needs to list a document. The storage key and checksum stay on
     * the server: clients address an attachment by its id and get it only after the same
     * project check that guards the ticket itself.
     */
    public record AttachmentDto(
            Long id,
            String filename,
            String contentType,
            long sizeBytes,
            UserDto uploadedBy,
            Instant uploadedAt) {

        public static AttachmentDto from(Attachment a) {
            return new AttachmentDto(
                    a.getId(),
                    a.getFilename(),
                    a.getContentType(),
                    a.getSizeBytes(),
                    UserDto.from(a.getUploadedBy()),
                    a.getCreatedAt());
        }
    }
}

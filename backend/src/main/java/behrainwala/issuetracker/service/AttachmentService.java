package behrainwala.issuetracker.service;

import behrainwala.issuetracker.domain.Attachment;
import behrainwala.issuetracker.domain.Ticket;
import behrainwala.issuetracker.domain.User;
import behrainwala.issuetracker.dto.AttachmentDtos.AttachmentDto;
import behrainwala.issuetracker.repo.AttachmentRepository;
import behrainwala.issuetracker.web.ConflictException;
import behrainwala.issuetracker.web.NotFoundException;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * Attachments inherit the access rules of the ticket they hang off: you may read them if
 * you can view the project, add them if you can write to it, and an archived ticket accepts
 * no new ones. Nothing here is addressable without going through one of those checks first.
 */
@Service
@Transactional(readOnly = true)
public class AttachmentService {

    /** Enough to recognise every signature the policy screens for. */
    private static final int MAGIC_BYTES = 8;

    private final AttachmentRepository attachmentRepository;
    private final TicketService ticketService;
    private final AttachmentStorage storage;
    private final AttachmentPolicy policy;
    private final AccessGuard accessGuard;

    public AttachmentService(AttachmentRepository attachmentRepository,
                             TicketService ticketService,
                             AttachmentStorage storage,
                             AttachmentPolicy policy,
                             AccessGuard accessGuard) {
        this.attachmentRepository = attachmentRepository;
        this.ticketService = ticketService;
        this.storage = storage;
        this.policy = policy;
        this.accessGuard = accessGuard;
    }

    public List<AttachmentDto> list(String ticketKey, User current) {
        Ticket ticket = ticketService.requireByKey(ticketKey);
        accessGuard.requireView(ticket.getProject(), current);
        return attachmentRepository.findByTicketId(ticket.getId()).stream()
                .map(AttachmentDto::from)
                .toList();
    }

    /**
     * Stores one document. The order matters: permission first, then the cheap metadata
     * checks, and only then are any bytes written - a rejected upload never reaches disk.
     */
    @Transactional
    public AttachmentDto upload(String ticketKey, MultipartFile file, User current) {
        Ticket ticket = ticketService.requireByKey(ticketKey);
        accessGuard.requireWrite(ticket, current);

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("The file is empty");
        }
        if (file.getSize() > policy.maxBytes()) {
            throw new IllegalArgumentException(
                    "That file is %s; the limit is %s"
                            .formatted(humanSize(file.getSize()), humanSize(policy.maxBytes())));
        }
        if (attachmentRepository.countByTicketId(ticket.getId()) >= policy.maxPerTicket()) {
            throw new ConflictException(
                    "%s already has the maximum of %d attachments"
                            .formatted(ticket.getTicketKey(), policy.maxPerTicket()));
        }

        String filename = policy.sanitizeFilename(file.getOriginalFilename());
        String contentType = policy.resolveContentType(filename, headOf(file));

        String storageKey = storage.newStorageKey();
        String sha256 = storage.store(file, storageKey);

        Attachment attachment = new Attachment(
                ticket, filename, contentType, file.getSize(), storageKey, sha256, current);
        return AttachmentDto.from(attachmentRepository.save(attachment));
    }

    /**
     * The bytes, for a caller who can view the project. Returned as a record rather than a
     * bare Resource so the controller can set the download headers without re-reading the row.
     */
    public Download download(Long attachmentId, User current) {
        Attachment attachment = requireAttachment(attachmentId);
        accessGuard.requireView(attachment.getTicket().getProject(), current);

        if (!storage.exists(attachment.getStorageKey())) {
            throw new NotFoundException(
                    "The stored copy of " + attachment.getFilename() + " is missing");
        }
        return new Download(
                storage.read(attachment.getStorageKey()), attachment.getFilename(), attachment.getSizeBytes());
    }

    /** A stored document ready to be streamed back. */
    public record Download(Resource resource, String filename, long sizeBytes) {
    }

    /**
     * Removing a document follows the same rule as removing a comment: its own uploader or
     * an administrator, and never on an archived ticket.
     */
    @Transactional
    public void delete(Long attachmentId, User current) {
        Attachment attachment = requireAttachment(attachmentId);
        Ticket ticket = attachment.getTicket();

        accessGuard.requireOwnerOrAdmin(attachment.getUploadedBy(), current,
                "Only the uploader or an administrator can remove this attachment");
        // The uploader shortcut skips requireWrite, so the archive check has to be explicit.
        accessGuard.requireActive(ticket);

        attachmentRepository.delete(attachment);
        storage.delete(attachment.getStorageKey());
    }

    private Attachment requireAttachment(Long attachmentId) {
        return attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new NotFoundException("Attachment " + attachmentId + " not found"));
    }

    /** The first few bytes, for the signature check, without buffering the whole upload. */
    private static byte[] headOf(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            return in.readNBytes(MAGIC_BYTES);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the uploaded file", e);
        }
    }

    private static String humanSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return "%.0f KB".formatted(bytes / 1024d);
        }
        return "%.1f MB".formatted(bytes / (1024d * 1024d));
    }
}

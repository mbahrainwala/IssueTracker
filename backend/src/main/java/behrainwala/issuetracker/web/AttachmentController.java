package behrainwala.issuetracker.web;

import behrainwala.issuetracker.dto.AttachmentDtos.AttachmentDto;
import behrainwala.issuetracker.service.AttachmentService;
import behrainwala.issuetracker.service.UserService;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api")
public class AttachmentController {

    private final AttachmentService attachmentService;
    private final UserService userService;

    public AttachmentController(AttachmentService attachmentService, UserService userService) {
        this.attachmentService = attachmentService;
        this.userService = userService;
    }

    @GetMapping("/tickets/{ticketKey}/attachments")
    public List<AttachmentDto> list(@PathVariable String ticketKey, Authentication auth) {
        return attachmentService.list(ticketKey, userService.currentUser(auth));
    }

    @PostMapping("/tickets/{ticketKey}/attachments")
    public ResponseEntity<AttachmentDto> upload(@PathVariable String ticketKey,
                                                @RequestParam("file") MultipartFile file,
                                                Authentication auth) {
        AttachmentDto created = attachmentService.upload(ticketKey, file, userService.currentUser(auth));
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Serves the stored bytes to anyone who can view the project.
     * <p>
     * Everything goes out as an octet-stream with an {@code attachment} disposition and
     * sniffing disabled, whatever the file actually is. That is deliberate: an uploaded
     * HTML page or SVG handed back with its real type would run script on this origin with
     * the viewer's session, so nothing is ever rendered in place - it is only ever saved.
     */
    @GetMapping("/attachments/{attachmentId}")
    public ResponseEntity<Resource> download(@PathVariable Long attachmentId, Authentication auth) {
        AttachmentService.Download download =
                attachmentService.download(attachmentId, userService.currentUser(auth));

        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(download.filename(), StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(download.sizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "default-src 'none'; sandbox")
                // A document is per-user authorised; a shared cache must not hold it.
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(download.resource());
    }

    @DeleteMapping("/attachments/{attachmentId}")
    public ResponseEntity<Void> delete(@PathVariable Long attachmentId, Authentication auth) {
        attachmentService.delete(attachmentId, userService.currentUser(auth));
        return ResponseEntity.noContent().build();
    }
}

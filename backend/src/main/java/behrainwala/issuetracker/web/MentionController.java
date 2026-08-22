package behrainwala.issuetracker.web;

import behrainwala.issuetracker.dto.CommentDtos.CommentDto;
import behrainwala.issuetracker.dto.MentionDtos.AcknowledgeRequest;
import behrainwala.issuetracker.dto.MentionDtos.MentionDto;
import behrainwala.issuetracker.service.CommentService;
import behrainwala.issuetracker.service.MentionService;
import behrainwala.issuetracker.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Outstanding {@code @username} mentions for whoever is asking. Always scoped to the caller -
 * there is no way to read somebody else's, because "what have I been asked to look at" is not
 * a question anyone else gets to answer.
 */
@RestController
@RequestMapping("/api")
public class MentionController {

    private final MentionService mentionService;
    private final CommentService commentService;
    private final UserService userService;

    public MentionController(MentionService mentionService,
                             CommentService commentService,
                             UserService userService) {
        this.mentionService = mentionService;
        this.commentService = commentService;
        this.userService = userService;
    }

    /** Everything waiting for me, newest first. Drives the highlight on boards and lists. */
    @GetMapping("/mentions")
    public List<MentionDto> outstanding(Authentication auth) {
        return mentionService.outstandingFor(userService.currentUser(auth));
    }

    /**
     * Acknowledges every mention outstanding for me on this ticket, by leaving a comment. The
     * comment is required - the acknowledgement has to be visible to whoever asked.
     */
    @PostMapping("/tickets/{ticketKey}/mentions/acknowledge")
    public CommentDto acknowledge(@PathVariable String ticketKey,
                                  @Valid @RequestBody AcknowledgeRequest request,
                                  Authentication auth) {
        return commentService.acknowledgeMentions(ticketKey, request, userService.currentUser(auth));
    }
}

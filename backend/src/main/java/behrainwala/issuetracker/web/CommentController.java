package behrainwala.issuetracker.web;

import behrainwala.issuetracker.dto.CommentDtos.CommentDto;
import behrainwala.issuetracker.dto.CommentDtos.CommentRequest;
import behrainwala.issuetracker.service.CommentService;
import behrainwala.issuetracker.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class CommentController {

    private final CommentService commentService;
    private final UserService userService;

    public CommentController(CommentService commentService, UserService userService) {
        this.commentService = commentService;
        this.userService = userService;
    }

    @GetMapping("/tickets/{ticketKey}/comments")
    public List<CommentDto> list(@PathVariable String ticketKey, Authentication auth) {
        return commentService.list(ticketKey, userService.currentUser(auth));
    }

    @PostMapping("/tickets/{ticketKey}/comments")
    public ResponseEntity<CommentDto> add(@PathVariable String ticketKey,
                                          @Valid @RequestBody CommentRequest request,
                                          Authentication auth) {
        CommentDto created = commentService.add(ticketKey, request, userService.currentUser(auth));
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/comments/{commentId}")
    public CommentDto update(@PathVariable Long commentId,
                             @Valid @RequestBody CommentRequest request,
                             Authentication auth) {
        return commentService.update(commentId, request, userService.currentUser(auth));
    }

    @DeleteMapping("/comments/{commentId}")
    public ResponseEntity<Void> delete(@PathVariable Long commentId, Authentication auth) {
        commentService.delete(commentId, userService.currentUser(auth));
        return ResponseEntity.noContent().build();
    }
}

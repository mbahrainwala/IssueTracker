package behrainwala.issuetracker.service;

import behrainwala.issuetracker.domain.Comment;
import behrainwala.issuetracker.domain.Ticket;
import behrainwala.issuetracker.domain.User;
import behrainwala.issuetracker.dto.CommentDtos.CommentDto;
import behrainwala.issuetracker.dto.CommentDtos.CommentRequest;
import behrainwala.issuetracker.repo.CommentRepository;
import behrainwala.issuetracker.web.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class CommentService {

    private final CommentRepository commentRepository;
    private final TicketService ticketService;
    private final AccessGuard accessGuard;

    public CommentService(CommentRepository commentRepository,
                          TicketService ticketService,
                          AccessGuard accessGuard) {
        this.commentRepository = commentRepository;
        this.ticketService = ticketService;
        this.accessGuard = accessGuard;
    }

    public List<CommentDto> list(String ticketKey, User current) {
        Ticket ticket = ticketService.requireByKey(ticketKey);
        accessGuard.requireView(ticket.getProject(), current);
        return commentRepository.findByTicketIdOrderByCreatedAtAsc(ticket.getId()).stream()
                .map(CommentDto::from)
                .toList();
    }

    @Transactional
    public CommentDto add(String ticketKey, CommentRequest request, User current) {
        Ticket ticket = ticketService.requireByKey(ticketKey);
        accessGuard.requireWrite(ticket, current);
        Comment comment = new Comment(ticket, current, request.body());
        return CommentDto.from(commentRepository.save(comment));
    }

    @Transactional
    public CommentDto update(Long commentId, CommentRequest request, User current) {
        Comment comment = requireComment(commentId);
        requireAuthorOrAdmin(comment, current);
        comment.setBody(request.body());
        return CommentDto.from(comment);
    }

    @Transactional
    public void delete(Long commentId, User current) {
        Comment comment = requireComment(commentId);
        requireAuthorOrAdmin(comment, current);
        commentRepository.delete(comment);
    }

    private Comment requireComment(Long commentId) {
        return commentRepository.findById(commentId)
                .orElseThrow(() -> new NotFoundException("Comment " + commentId + " not found"));
    }

    /** A comment is its author's to edit or delete, and otherwise only an administrator's. */
    private void requireAuthorOrAdmin(Comment comment, User current) {
        accessGuard.requireOwnerOrAdmin(comment.getAuthor(), current,
                "Only the author or an administrator can modify this comment");
        // The author shortcut skips requireWrite, so the archive check has to be explicit.
        accessGuard.requireActive(comment.getTicket());
    }
}

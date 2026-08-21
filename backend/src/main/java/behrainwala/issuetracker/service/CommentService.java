package behrainwala.issuetracker.service;

import behrainwala.issuetracker.domain.Comment;
import behrainwala.issuetracker.domain.Ticket;
import behrainwala.issuetracker.domain.User;
import behrainwala.issuetracker.dto.CommentDtos.CommentDto;
import behrainwala.issuetracker.dto.CommentDtos.CommentRequest;
import behrainwala.issuetracker.repo.CommentRepository;
import behrainwala.issuetracker.web.NotFoundException;
import org.springframework.security.access.AccessDeniedException;
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
        requireAuthorOrProjectAdmin(comment, current);
        comment.setBody(request.body());
        return CommentDto.from(comment);
    }

    @Transactional
    public void delete(Long commentId, User current) {
        Comment comment = requireComment(commentId);
        requireAuthorOrProjectAdmin(comment, current);
        commentRepository.delete(comment);
    }

    private Comment requireComment(Long commentId) {
        return commentRepository.findById(commentId)
                .orElseThrow(() -> new NotFoundException("Comment " + commentId + " not found"));
    }

    private void requireAuthorOrProjectAdmin(Comment comment, User current) {
        boolean isAuthor = comment.getAuthor().getId().equals(current.getId());
        if (!isAuthor && !accessGuard.canAdminister(comment.getTicket().getProject(), current)) {
            throw new AccessDeniedException("Only the author or a project lead can modify this comment");
        }
        // The author shortcut skips requireWrite, so the archive check has to be explicit.
        accessGuard.requireActive(comment.getTicket());
    }
}

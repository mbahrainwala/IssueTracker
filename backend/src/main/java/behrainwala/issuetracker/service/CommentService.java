package behrainwala.issuetracker.service;

import behrainwala.issuetracker.domain.Comment;
import behrainwala.issuetracker.domain.Ticket;
import behrainwala.issuetracker.domain.User;
import behrainwala.issuetracker.dto.CommentDtos.CommentDto;
import behrainwala.issuetracker.dto.CommentDtos.CommentRequest;
import behrainwala.issuetracker.dto.MentionDtos.AcknowledgeRequest;
import behrainwala.issuetracker.repo.CommentRepository;
import behrainwala.issuetracker.web.ConflictException;
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
    private final MentionService mentions;

    public CommentService(CommentRepository commentRepository,
                          TicketService ticketService,
                          AccessGuard accessGuard,
                          MentionService mentions) {
        this.commentRepository = commentRepository;
        this.ticketService = ticketService;
        this.accessGuard = accessGuard;
        this.mentions = mentions;
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
        Comment saved = commentRepository.saveAndFlush(new Comment(ticket, current, request.body()));
        // Flushed first so the mention can point at the comment that raised it.
        mentions.record(ticket, saved.getBody(), current, saved);
        return CommentDto.from(saved);
    }

    @Transactional
    public CommentDto update(Long commentId, CommentRequest request, User current) {
        Comment comment = requireComment(commentId);
        requireAuthorOrAdmin(comment, current);
        comment.setBody(request.body());
        // An edit can name somebody who was not named before; already-outstanding mentions
        // from this same comment are not raised twice.
        mentions.record(comment.getTicket(), comment.getBody(), current, comment);
        return CommentDto.from(comment);
    }

    /**
     * Acknowledges every mention outstanding for this person on the ticket, by posting the
     * comment they wrote. One operation rather than "post a comment, then dismiss": the
     * comment is the acknowledgement, so the two cannot come apart.
     */
    @Transactional
    public CommentDto acknowledgeMentions(String ticketKey, AcknowledgeRequest request, User current) {
        Ticket ticket = ticketService.requireByKey(ticketKey);
        accessGuard.requireWrite(ticket, current);

        if (mentions.outstandingOn(ticket, current).isEmpty()) {
            throw new ConflictException(
                    "You have nothing to acknowledge on " + ticket.getTicketKey());
        }

        Comment saved = commentRepository.saveAndFlush(new Comment(ticket, current, request.body()));
        mentions.acknowledge(ticket, current, saved);
        // An acknowledgement can name somebody in turn - replying "@alice done" is normal.
        mentions.record(ticket, saved.getBody(), current, saved);
        return CommentDto.from(saved);
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

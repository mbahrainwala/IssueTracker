package behrainwala.issuetracker.repo;

import behrainwala.issuetracker.domain.TicketMention;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TicketMentionRepository extends JpaRepository<TicketMention, Long> {

    /**
     * Everything still waiting for one person, newest first. The ticket and the mentioner are
     * fetched because the list renders both.
     */
    /**
     * Everything is fetched, not lazily loaded: this list is mapped to DTOs that read the
     * ticket's project and the comment behind the mention, and with open-in-view off a proxy
     * touched after the transaction throws.
     */
    @Query("""
            select m from TicketMention m
            join fetch m.ticket t
            join fetch t.project
            join fetch m.mentionedBy
            left join fetch m.sourceComment
            where m.mentionedUser.id = :userId
              and m.acknowledgedAt is null
            order by m.id desc
            """)
    List<TicketMention> findOutstandingFor(@Param("userId") Long userId);

    /** What this person still has to acknowledge on one ticket. */
    @Query("""
            select m from TicketMention m
            where m.ticket.id = :ticketId
              and m.mentionedUser.id = :userId
              and m.acknowledgedAt is null
            """)
    List<TicketMention> findOutstandingOn(@Param("ticketId") Long ticketId,
                                          @Param("userId") Long userId);

    /**
     * Whether this person already owes an acknowledgement for this exact source. Editing a
     * comment that already named someone should not queue the same nudge twice.
     */
    @Query("""
            select count(m) > 0 from TicketMention m
            where m.ticket.id = :ticketId
              and m.mentionedUser.id = :userId
              and m.acknowledgedAt is null
              and (:commentId is null and m.sourceComment is null
                or m.sourceComment.id = :commentId)
            """)
    boolean alreadyOutstanding(@Param("ticketId") Long ticketId,
                               @Param("userId") Long userId,
                               @Param("commentId") Long commentId);
}

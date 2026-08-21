package behrainwala.issuetracker.repo;

import behrainwala.issuetracker.domain.TicketStatusChange;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TicketStatusChangeRepository extends JpaRepository<TicketStatusChange, Long> {

    /**
     * Oldest first, by id: MySQL TIMESTAMP is second-precision and would tie. Every row
     * names its mover, so it is fetched rather than lazily loaded one query at a time.
     */
    @Query("""
            select c from TicketStatusChange c
            left join fetch c.movedBy
            where c.ticket.id = :ticketId
            order by c.id asc
            """)
    List<TicketStatusChange> findByTicketIdOrderByIdAsc(@Param("ticketId") Long ticketId);

    Optional<TicketStatusChange> findFirstByTicketIdOrderByIdDesc(Long ticketId);
}

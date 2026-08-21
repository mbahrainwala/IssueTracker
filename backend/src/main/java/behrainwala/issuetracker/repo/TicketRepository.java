package behrainwala.issuetracker.repo;

import behrainwala.issuetracker.domain.Ticket;
import behrainwala.issuetracker.domain.TicketType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TicketRepository extends JpaRepository<Ticket, Long> {

    Optional<Ticket> findByTicketKeyIgnoreCase(String ticketKey);

    List<Ticket> findByProjectIdOrderByTicketNumberDesc(Long projectId);

    /** Project cards count live work, not the archive. */
    long countByProjectIdAndArchivedAtIsNull(Long projectId);

    /** All children, archived included - an epic's archive-readiness depends on seeing them. */
    List<Ticket> findByEpicIdOrderByTicketNumberDesc(Long epicId);

    /** What still stands between an epic and being archivable. */
    long countByEpicIdAndArchivedAtIsNull(Long epicId);

    /** Epics available as a parent, i.e. the EPIC-typed tickets of one project. */
    List<Ticket> findByProjectIdAndTypeOrderByTicketNumberDesc(Long projectId, TicketType type);

    /**
     * Tickets that could be filed under the given epic. Restricted to the epic's own project -
     * an epic never gathers tickets from elsewhere - and excludes epics themselves plus
     * anything already in this epic.
     */
    @Query("""
            select t from Ticket t
            where t.project.id = :projectId
              and t.type <> behrainwala.issuetracker.domain.TicketType.EPIC
              and t.archivedAt is null
              and (t.epic is null or t.epic.id <> :epicId)
              and (:q is null or lower(t.ticketKey) like lower(concat('%', :q, '%'))
                              or lower(t.title) like lower(concat('%', :q, '%')))
            order by t.ticketNumber desc
            """)
    List<Ticket> findEpicCandidates(@Param("projectId") Long projectId,
                                    @Param("epicId") Long epicId,
                                    @Param("q") String q,
                                    Pageable pageable);

    /** Cross-project lookup for the "link a ticket" picker; callers filter by visibility. */
    @Query("""
            select t from Ticket t
            where lower(t.ticketKey) like lower(concat('%', :q, '%'))
               or lower(t.title) like lower(concat('%', :q, '%'))
            order by t.updatedAt desc
            """)
    List<Ticket> searchAll(@Param("q") String q, Pageable pageable);

    /**
     * The board and the archived tab are the same query on opposite sides of the archive
     * flag, so {@code archived} picks the side rather than there being two copies to keep
     * in sync. {@code archivedBy} is fetched because the archived tab renders it for every
     * row. Status is bound as a string rather than the enum so a null value needs no
     * explicit type hint for Hibernate's parameter inference.
     */
    @Query(value = """
            select t from Ticket t
            left join fetch t.archivedBy
            where t.project.id = :projectId
              and (:archived = true and t.archivedAt is not null
                or :archived = false and t.archivedAt is null)
              and (:status is null or str(t.status) = :status)
              and (:assigneeId is null or t.assignee.id = :assigneeId)
              and (:q is null or lower(t.title) like lower(concat('%', :q, '%'))
                              or lower(t.ticketKey) like lower(concat('%', :q, '%')))
            """,
            countQuery = """
            select count(t) from Ticket t
            where t.project.id = :projectId
              and (:archived = true and t.archivedAt is not null
                or :archived = false and t.archivedAt is null)
              and (:status is null or str(t.status) = :status)
              and (:assigneeId is null or t.assignee.id = :assigneeId)
              and (:q is null or lower(t.title) like lower(concat('%', :q, '%'))
                              or lower(t.ticketKey) like lower(concat('%', :q, '%')))
            """)
    Page<Ticket> search(@Param("projectId") Long projectId,
                        @Param("archived") boolean archived,
                        @Param("status") String status,
                        @Param("assigneeId") Long assigneeId,
                        @Param("q") String q,
                        Pageable pageable);
}

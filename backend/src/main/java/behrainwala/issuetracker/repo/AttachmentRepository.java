package behrainwala.issuetracker.repo;

import behrainwala.issuetracker.domain.Attachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {

    /**
     * Newest first. Every row names its uploader, so that is fetched rather than lazily
     * loaded one query at a time.
     */
    @Query("""
            select a from Attachment a
            left join fetch a.uploadedBy
            where a.ticket.id = :ticketId
            order by a.id desc
            """)
    List<Attachment> findByTicketId(@Param("ticketId") Long ticketId);

    long countByTicketId(Long ticketId);

    /**
     * The stored files belonging to a ticket. Deleting the ticket cascades the rows away in
     * the database, so the keys have to be read before that happens to clear the disk too.
     */
    @Query("select a.storageKey from Attachment a where a.ticket.id = :ticketId")
    List<String> findStorageKeysByTicketId(@Param("ticketId") Long ticketId);

    /** Every key the database still claims, for the nightly orphan sweep to compare against. */
    @Query("select a.storageKey from Attachment a")
    List<String> findAllStorageKeys();
}

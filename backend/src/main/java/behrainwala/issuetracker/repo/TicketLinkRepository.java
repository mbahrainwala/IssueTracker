package behrainwala.issuetracker.repo;

import behrainwala.issuetracker.domain.LinkType;
import behrainwala.issuetracker.domain.TicketLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TicketLinkRepository extends JpaRepository<TicketLink, Long> {

    List<TicketLink> findBySourceId(Long ticketId);

    List<TicketLink> findByTargetId(Long ticketId);

    Optional<TicketLink> findBySourceIdAndTargetIdAndLinkType(Long sourceId, Long targetId, LinkType linkType);

    /** Used to spot a link already recorded from the opposite direction. */
    Optional<TicketLink> findBySourceIdAndTargetId(Long sourceId, Long targetId);
}

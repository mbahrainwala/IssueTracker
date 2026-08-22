package behrainwala.issuetracker.repo;

import behrainwala.issuetracker.domain.ProjectStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProjectStatusRepository extends JpaRepository<ProjectStatus, Long> {

    /** A project's board, left to right. */
    List<ProjectStatus> findByProjectIdOrderByLaneOrderAsc(Long projectId);

    Optional<ProjectStatus> findByProjectIdAndNameIgnoreCase(Long projectId, String name);

    Optional<ProjectStatus> findByProjectIdAndInitialLaneIsTrue(Long projectId);

    Optional<ProjectStatus> findByProjectIdAndDoneLaneIsTrue(Long projectId);

    long countByProjectId(Long projectId);

    /**
     * Renaming a lane has to carry its tickets with it, because the lane name is the value
     * they hold. Done as one statement rather than loading every ticket to change a string.
     */
    @Modifying
    @Query("update Ticket t set t.status = :to where t.project.id = :projectId and t.status = :from")
    int renameTicketStatuses(@Param("projectId") Long projectId,
                             @Param("from") String from,
                             @Param("to") String to);

    /** How many tickets sit in a lane - a lane with any is not safe to delete. */
    @Query("select count(t) from Ticket t where t.project.id = :projectId and t.status = :name")
    long countTicketsIn(@Param("projectId") Long projectId, @Param("name") String name);
}

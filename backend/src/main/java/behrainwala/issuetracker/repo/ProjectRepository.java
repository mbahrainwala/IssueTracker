package behrainwala.issuetracker.repo;

import behrainwala.issuetracker.domain.Project;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    Optional<Project> findByProjectKeyIgnoreCase(String projectKey);

    boolean existsByProjectKeyIgnoreCase(String projectKey);

    /**
     * Locks the project row so concurrent ticket creation cannot hand out the same
     * ticket number twice.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Project p where p.id = :id")
    Optional<Project> findByIdForUpdate(@Param("id") Long id);

    /**
     * Projects the user is a member of, in any role. Members are fetched alongside so
     * rendering the lead list does not fire a query per project.
     */
    @Query("""
            select distinct p from Project p
            left join fetch p.members m
            left join fetch m.user
            where p.archivedAt is null
              and exists (
                select 1 from ProjectMember pm
                where pm.project = p and pm.user.id = :userId
            )
            order by p.projectKey
            """)
    List<Project> findAllForUser(@Param("userId") Long userId);

    /** The archived tab, still scoped to what the user is assigned to. */
    @Query("""
            select distinct p from Project p
            left join fetch p.members m
            left join fetch m.user
            where p.archivedAt is not null
              and exists (
                select 1 from ProjectMember pm
                where pm.project = p and pm.user.id = :userId
            )
            order by p.projectKey
            """)
    List<Project> findArchivedForUser(@Param("userId") Long userId);

    /** Admin view: every project, members fetched for the same reason. */
    @Query("""
            select distinct p from Project p
            left join fetch p.members m
            left join fetch m.user
            where p.archivedAt is null
            order by p.projectKey
            """)
    List<Project> findAllWithMembers();

    @Query("""
            select distinct p from Project p
            left join fetch p.members m
            left join fetch m.user
            where p.archivedAt is not null
            order by p.projectKey
            """)
    List<Project> findArchivedWithMembers();
}

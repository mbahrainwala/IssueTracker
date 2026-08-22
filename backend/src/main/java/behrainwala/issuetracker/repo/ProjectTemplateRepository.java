package behrainwala.issuetracker.repo;

import behrainwala.issuetracker.domain.ProjectTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ProjectTemplateRepository extends JpaRepository<ProjectTemplate, Long> {

    boolean existsByNameIgnoreCase(String name);

    Optional<ProjectTemplate> findByNameIgnoreCase(String name);

    /** The picker list. Lanes are fetched because the picker previews them. */
    @Query("""
            select distinct t from ProjectTemplate t
            left join fetch t.lanes
            order by t.name
            """)
    List<ProjectTemplate> findAllWithLanes();
}

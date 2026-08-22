package behrainwala.issuetracker.repo;

import behrainwala.issuetracker.domain.Branding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface BrandingRepository extends JpaRepository<Branding, Long> {

    /**
     * Name and logo metadata without the logo itself. Every page load asks for this, and
     * loading the image bytes to answer "what is the company called?" would be a waste on
     * every one of them.
     */
    @Query("""
            select b.companyName as companyName,
                   b.logoContentType as logoContentType,
                   b.logoUpdatedAt as logoUpdatedAt
            from Branding b
            where b.id = :id
            """)
    Optional<BrandingView> findView(@Param("id") Long id);

    /** Projection over the branding row that leaves the image behind. */
    interface BrandingView {
        String getCompanyName();

        String getLogoContentType();

        Instant getLogoUpdatedAt();
    }
}

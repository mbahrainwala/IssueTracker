package behrainwala.issuetracker.dto;

import behrainwala.issuetracker.repo.BrandingRepository.BrandingView;
import jakarta.validation.constraints.Size;

public final class BrandingDtos {

    private BrandingDtos() {
    }

    /** A blank name clears it and puts the default title back. */
    public record UpdateBrandingRequest(@Size(max = 120) String companyName) {
    }

    /**
     * What the title bar needs. {@code logoVersion} is the logo's last-updated stamp, appended
     * to the image URL by the client so a replaced logo is fetched rather than served from
     * cache; it is null when there is no logo.
     */
    public record BrandingDto(String companyName, boolean hasLogo, Long logoVersion) {

        public static BrandingDto from(BrandingView view) {
            boolean hasLogo = view.getLogoContentType() != null;
            return new BrandingDto(
                    view.getCompanyName(),
                    hasLogo,
                    hasLogo && view.getLogoUpdatedAt() != null
                            ? view.getLogoUpdatedAt().toEpochMilli()
                            : null);
        }

        /** Before the row is read, or if it somehow went missing: the app's own identity. */
        public static BrandingDto empty() {
            return new BrandingDto(null, false, null);
        }
    }
}

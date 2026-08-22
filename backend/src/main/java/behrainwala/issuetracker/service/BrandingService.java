package behrainwala.issuetracker.service;

import behrainwala.issuetracker.config.AppProperties;
import behrainwala.issuetracker.domain.Branding;
import behrainwala.issuetracker.domain.User;
import behrainwala.issuetracker.dto.BrandingDtos.BrandingDto;
import behrainwala.issuetracker.dto.BrandingDtos.UpdateBrandingRequest;
import behrainwala.issuetracker.repo.BrandingRepository;
import behrainwala.issuetracker.web.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;


/**
 * The company name and logo shown in the title bar.
 * <p>
 * Reading is deliberately open to anyone, signed in or not, because the login page wears the
 * branding too - the company name is the one thing here meant to be seen before sign-in.
 * Changing it is reserved for a global administrator.
 */
@Service
@Transactional(readOnly = true)
public class BrandingService {

    private final BrandingRepository brandingRepository;
    private final BrandingLogoStore logoStore;
    private final ImagePolicy imagePolicy;
    private final AppProperties.Branding settings;

    public BrandingService(BrandingRepository brandingRepository,
                           BrandingLogoStore logoStore,
                           ImagePolicy imagePolicy,
                           AppProperties properties) {
        this.brandingRepository = brandingRepository;
        this.logoStore = logoStore;
        this.imagePolicy = imagePolicy;
        this.settings = properties.getBranding();
    }

    public BrandingDto get() {
        return brandingRepository.findView(Branding.SINGLETON_ID)
                .map(BrandingDto::from)
                .orElseGet(BrandingDto::empty);
    }

    /** The stored image. The metadata says whether there is one; the file holds the bytes. */
    public Logo logo() {
        String contentType = brandingRepository.findView(Branding.SINGLETON_ID)
                .map(BrandingRepository.BrandingView::getLogoContentType)
                .orElse(null);
        byte[] bytes = contentType == null ? null : logoStore.read().orElse(null);
        if (bytes == null) {
            throw new NotFoundException("No logo has been set");
        }
        return new Logo(bytes, contentType);
    }

    public record Logo(byte[] bytes, String contentType) {
    }

    @Transactional
    public BrandingDto update(UpdateBrandingRequest request, User current) {
        Branding branding = requireRow();
        String name = request.companyName() == null || request.companyName().isBlank()
                ? null
                : request.companyName().trim();
        branding.setCompanyName(name, current);
        return get();
    }

    /** Replaces the logo. Screened by the shared image policy, same as a project image. */
    @Transactional
    public BrandingDto setLogo(MultipartFile file, User current) {
        Branding branding = requireRow();
        ImagePolicy.Image image = imagePolicy.validate(file, settings.getMaxLogoBytes(), "A logo");

        // The file is written first: if the metadata update then fails, the transaction rolls
        // back and the row still says there is no logo, leaving an unreferenced file the
        // next upload overwrites. The reverse order would advertise a logo that is not there.
        logoStore.write(image.bytes());
        branding.setLogoMetadata(image.contentType(), image.filename(), current);
        return get();
    }

    @Transactional
    public BrandingDto clearLogo(User current) {
        requireRow().clearLogoMetadata(current);
        // Deferred until this commits, so a rollback cannot leave the row pointing at a file
        // that has already been unlinked.
        logoStore.delete();
        return get();
    }

    /** The migration seeds this row, so its absence is a broken installation, not a case. */
    private Branding requireRow() {
        return brandingRepository.findById(Branding.SINGLETON_ID)
                .orElseThrow(() -> new IllegalStateException(
                        "The app_branding row is missing - the V9 migration seeds it"));
    }

}

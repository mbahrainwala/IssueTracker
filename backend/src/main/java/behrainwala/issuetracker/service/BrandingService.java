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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;

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

    /** Extension to the type served back. Images only, and nothing is ever run. */
    private static final Map<String, String> ALLOWED_LOGOS = Map.of(
            "png", "image/png",
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "gif", "image/gif",
            "webp", "image/webp",
            "svg", "image/svg+xml");

    /** Enough to recognise every signature the executable screen looks for. */
    private static final int MAGIC_BYTES = 8;

    private final BrandingRepository brandingRepository;
    private final BrandingLogoStore logoStore;
    private final AttachmentPolicy uploadPolicy;
    private final AppProperties.Branding settings;

    public BrandingService(BrandingRepository brandingRepository,
                           BrandingLogoStore logoStore,
                           AttachmentPolicy uploadPolicy,
                           AppProperties properties) {
        this.brandingRepository = brandingRepository;
        this.logoStore = logoStore;
        this.uploadPolicy = uploadPolicy;
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

    /**
     * Replaces the logo. Images only, screened the same way an attachment is: the extension
     * must be on the list, and the bytes must not look executable whatever it is called.
     */
    @Transactional
    public BrandingDto setLogo(MultipartFile file, User current) {
        Branding branding = requireRow();

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("The file is empty");
        }
        if (file.getSize() > settings.getMaxLogoBytes()) {
            throw new IllegalArgumentException(
                    "A logo must be %d KB or smaller".formatted(settings.getMaxLogoBytes() / 1024));
        }

        String filename = uploadPolicy.sanitizeFilename(file.getOriginalFilename());
        String extension = extensionOf(filename);
        String contentType = ALLOWED_LOGOS.get(extension);
        if (contentType == null) {
            throw new IllegalArgumentException(
                    "A logo must be a PNG, JPEG, GIF, WebP or SVG image");
        }

        byte[] bytes = readAll(file);
        uploadPolicy.rejectExecutableContent(Arrays.copyOf(bytes, Math.min(bytes.length, MAGIC_BYTES)));

        // The file is written first: if the metadata update then fails, the transaction rolls
        // back and the row still says there is no logo, leaving an unreferenced file the
        // next upload overwrites. The reverse order would advertise a logo that is not there.
        logoStore.write(bytes);
        branding.setLogoMetadata(contentType, filename, current);
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

    private static byte[] readAll(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the uploaded logo", e);
        }
    }

    private static String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 0 || dot == filename.length() - 1
                ? ""
                : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}

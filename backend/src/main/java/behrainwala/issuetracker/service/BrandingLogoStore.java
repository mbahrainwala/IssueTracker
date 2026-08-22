package behrainwala.issuetracker.service;

import behrainwala.issuetracker.config.AppProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

/**
 * The logo file. There is only ever one, so it needs no key: it is a single fixed file in
 * {@code app.branding.directory}, and the database row beside it says what type it is.
 * <p>
 * That directory is deliberately <em>not</em> the attachment directory. The
 * {@link OrphanedAttachmentSweeper} deletes files in the attachment store that no attachment
 * row points at, and the logo - which no attachment row will ever point at - would look
 * exactly like an orphan and be swept away. {@link #rejectSharedDirectory()} refuses to start
 * if the two are ever configured to the same place.
 */
@Component
public class BrandingLogoStore {

    private static final Logger log = LoggerFactory.getLogger(BrandingLogoStore.class);

    /** One logo, one file. The stored type lives in the database, not in an extension. */
    private static final String LOGO_FILE = "logo";
    private static final String TEMP_FILE = "logo.tmp";

    private final Path root;
    private final Path attachmentRoot;

    public BrandingLogoStore(AppProperties properties) {
        this.root = Path.of(properties.getBranding().getDirectory()).toAbsolutePath().normalize();
        this.attachmentRoot =
                Path.of(properties.getAttachments().getDirectory()).toAbsolutePath().normalize();
    }

    /**
     * Refuses to start when the logo would sit inside the swept attachment directory. This is
     * a configuration mistake that would otherwise look fine for six hours and then silently
     * delete the logo, so it fails loudly at boot instead.
     */
    @PostConstruct
    void rejectSharedDirectory() {
        if (root.equals(attachmentRoot) || root.startsWith(attachmentRoot)) {
            throw new IllegalStateException(
                    ("app.branding.directory (%s) must not sit inside app.attachments.directory (%s) - "
                            + "the nightly orphan sweep would delete the logo")
                            .formatted(root, attachmentRoot));
        }
    }

    public Optional<byte[]> read() {
        Path file = root.resolve(LOGO_FILE);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readAllBytes(file));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the logo", e);
        }
    }

    public boolean exists() {
        return Files.isRegularFile(root.resolve(LOGO_FILE));
    }

    /**
     * Replaces the logo. Written to a temporary file and moved into place, so a failed or
     * half-finished write never leaves a truncated image where the whole one used to be.
     */
    public void write(byte[] bytes) {
        try {
            Files.createDirectories(root);
            Path temp = root.resolve(TEMP_FILE);
            Files.write(temp, bytes);
            try {
                Files.move(temp, root.resolve(LOGO_FILE),
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                // Some filesystems cannot promise atomicity; the replace is still correct.
                Files.move(temp, root.resolve(LOGO_FILE), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not store the logo", e);
        }
    }

    /** Removed once the transaction that cleared the metadata has actually committed. */
    public void delete() {
        AfterCommit.run(() -> {
            try {
                Files.deleteIfExists(root.resolve(LOGO_FILE));
            } catch (IOException e) {
                log.warn("Could not delete the logo file - it is now orphaned", e);
            }
        });
    }
}

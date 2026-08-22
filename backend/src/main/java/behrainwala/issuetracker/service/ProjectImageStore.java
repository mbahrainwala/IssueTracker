package behrainwala.issuetracker.service;

import behrainwala.issuetracker.config.AppProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

/**
 * Project pictures on disk, one file per project, named after the project id.
 * <p>
 * Naming by id rather than by a generated key means a project has at most one image and
 * replacing it overwrites in place: there is no key to store, no second file to clean up
 * afterwards, and no way to leave an orphan behind. The id comes from the database and is a
 * number, so it can never steer the write anywhere - but {@link #pathOf} checks anyway.
 * <p>
 * The directory sits outside the attachment store, which the nightly sweep empties of
 * anything no attachment row claims - and no attachment row will ever claim these.
 */
@Component
public class ProjectImageStore {

    private static final Logger log = LoggerFactory.getLogger(ProjectImageStore.class);

    private final Path root;
    private final Path attachmentRoot;

    public ProjectImageStore(AppProperties properties) {
        this.root = ManagedDirectory.resolve(properties.getProjects().getImageDirectory());
        this.attachmentRoot = ManagedDirectory.resolve(properties.getAttachments().getDirectory());
    }

    @PostConstruct
    void rejectSharedDirectory() {
        ManagedDirectory.rejectInsideAttachments(
                root, attachmentRoot, "app.projects.image-directory");
    }

    public Optional<byte[]> read(Long projectId) {
        Path file = pathOf(projectId);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readAllBytes(file));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the image for project " + projectId, e);
        }
    }

    public boolean exists(Long projectId) {
        return Files.isRegularFile(pathOf(projectId));
    }

    /**
     * Written to a temporary file and moved into place, so a failed or half-finished write
     * never leaves a truncated image where the whole one used to be.
     */
    public void write(Long projectId, byte[] bytes) {
        try {
            Files.createDirectories(root);
            Path temp = root.resolve(projectId + ".tmp");
            Files.write(temp, bytes);
            try {
                Files.move(temp, pathOf(projectId),
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                // Some filesystems cannot promise atomicity; the replace is still correct.
                Files.move(temp, pathOf(projectId), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not store the image for project " + projectId, e);
        }
    }

    /** Removed once the transaction that cleared the metadata has actually committed. */
    public void delete(Long projectId) {
        AfterCommit.run(() -> {
            try {
                Files.deleteIfExists(pathOf(projectId));
            } catch (IOException e) {
                log.warn("Could not delete the image for project {} - it is now orphaned",
                        projectId, e);
            }
        });
    }

    private Path pathOf(Long projectId) {
        if (projectId == null) {
            throw new IllegalArgumentException("A project id is required");
        }
        Path resolved = root.resolve(String.valueOf(projectId)).normalize();
        if (!resolved.getParent().equals(root)) {
            throw new IllegalArgumentException("Invalid project id");
        }
        return resolved;
    }
}

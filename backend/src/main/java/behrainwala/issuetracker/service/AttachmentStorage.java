package behrainwala.issuetracker.service;

import behrainwala.issuetracker.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * The bytes half of an attachment. Every file is written under a fresh UUID with no
 * extension, in one flat directory - the uploaded name never touches the filesystem, so
 * there is no path to traverse and no way to plant something the web server would serve
 * or execute on its own.
 */
@Component
public class AttachmentStorage {

    private static final Logger log = LoggerFactory.getLogger(AttachmentStorage.class);

    private final Path root;

    public AttachmentStorage(AppProperties properties) {
        this.root = Path.of(properties.getAttachments().getDirectory()).toAbsolutePath().normalize();
    }

    /** One key per stored file, opaque and unguessable. */
    public String newStorageKey() {
        return UUID.randomUUID().toString();
    }

    /**
     * Streams the upload to disk and returns its SHA-256. Streaming rather than holding the
     * array keeps a large attachment off the heap.
     */
    public String store(MultipartFile file, String storageKey) {
        try {
            Files.createDirectories(root);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = new DigestInputStream(file.getInputStream(), digest)) {
                Files.copy(in, pathOf(storageKey), StandardCopyOption.REPLACE_EXISTING);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException e) {
            throw new UncheckedIOException("Could not store attachment", e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public Resource read(String storageKey) {
        return new FileSystemResource(pathOf(storageKey));
    }

    public boolean exists(String storageKey) {
        return Files.isRegularFile(pathOf(storageKey));
    }

    /**
     * Every file currently in the store, by key. The directory is flat and holds nothing but
     * attachments, so a file name is a storage key.
     */
    public List<String> listStorageKeys() {
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(root)) {
            return files.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not list the attachment directory", e);
        }
    }

    /**
     * When the file was last written, or {@link Instant#now()} if that cannot be read - an
     * unreadable timestamp makes a file look brand new, which keeps the sweeper off it.
     */
    public Instant lastModified(String storageKey) {
        try {
            return Files.getLastModifiedTime(pathOf(storageKey)).toInstant();
        } catch (IOException e) {
            return Instant.now();
        }
    }

    /**
     * Clears stored files once the surrounding transaction commits.
     * <p>
     * Deleting them inline would lose the bytes for good if the transaction then rolled back,
     * leaving rows pointing at nothing. Waiting for the commit inverts the failure: if the
     * unlink fails the file is merely orphaned, which wastes disk but loses nothing. Outside a
     * transaction - which is how a test or a plain call reaches this - it deletes immediately.
     */
    public void deleteAfterCommit(Collection<String> storageKeys) {
        if (storageKeys.isEmpty()) {
            return;
        }
        List<String> keys = List.copyOf(storageKeys);
        AfterCommit.run(() -> keys.forEach(this::delete));
    }

    /**
     * Best effort: a file that is already gone must not fail the caller, and neither must one
     * the OS refuses to unlink - by the time this runs the row it belonged to is committed.
     */
    public void delete(String storageKey) {
        try {
            Files.deleteIfExists(pathOf(storageKey));
        } catch (IOException e) {
            log.warn("Could not delete attachment file {} - it is now orphaned", storageKey, e);
        }
    }

    /**
     * Keys are generated here, never supplied by a caller, but this still refuses anything
     * that would resolve outside the root - the guarantee should not rest on every future
     * caller staying honest.
     */
    private Path pathOf(String storageKey) {
        Path resolved = root.resolve(storageKey).normalize();
        if (!resolved.getParent().equals(root)) {
            throw new IllegalArgumentException("Invalid storage key");
        }
        return resolved;
    }
}

package behrainwala.issuetracker.service;

import behrainwala.issuetracker.config.AppProperties;
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
import java.util.HexFormat;
import java.util.UUID;

/**
 * The bytes half of an attachment. Every file is written under a fresh UUID with no
 * extension, in one flat directory - the uploaded name never touches the filesystem, so
 * there is no path to traverse and no way to plant something the web server would serve
 * or execute on its own.
 */
@Component
public class AttachmentStorage {

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

    /** Best effort: a missing file must not block deleting the row that points at it. */
    public void delete(String storageKey) {
        try {
            Files.deleteIfExists(pathOf(storageKey));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not delete attachment " + storageKey, e);
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

package behrainwala.issuetracker.service;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;

/**
 * What may be uploaded as a picture - the company logo, a project image. One place, because
 * both answer the same question and a second copy of the answer would drift.
 * <p>
 * Reuses {@link AttachmentPolicy} for the two checks that are plain upload hygiene rather
 * than anything image-specific: reducing the filename to something safe, and refusing bytes
 * that look executable whatever the file is called.
 */
@Component
public class ImagePolicy {

    /** Extension to the type served back. Images only, and nothing is ever run. */
    private static final Map<String, String> ALLOWED = Map.of(
            "png", "image/png",
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "gif", "image/gif",
            "webp", "image/webp",
            "svg", "image/svg+xml");

    /** Enough to recognise every signature the executable screen looks for. */
    private static final int MAGIC_BYTES = 8;

    private final AttachmentPolicy uploadPolicy;

    public ImagePolicy(AttachmentPolicy uploadPolicy) {
        this.uploadPolicy = uploadPolicy;
    }

    /** The extensions the file picker should offer, so the UI never suggests a refusal. */
    public static String acceptAttribute() {
        return ".png,.jpg,.jpeg,.gif,.webp,.svg";
    }

    /**
     * Validates an uploaded image and returns the bytes plus the type to serve it as.
     *
     * @param what how to name this image in an error message, e.g. "A logo"
     */
    public Image validate(MultipartFile file, long maxBytes, String what) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("The file is empty");
        }
        if (file.getSize() > maxBytes) {
            throw new IllegalArgumentException(
                    "%s must be %d KB or smaller".formatted(what, maxBytes / 1024));
        }

        String filename = uploadPolicy.sanitizeFilename(file.getOriginalFilename());
        String contentType = ALLOWED.get(extensionOf(filename));
        if (contentType == null) {
            throw new IllegalArgumentException(
                    "%s must be a PNG, JPEG, GIF, WebP or SVG image".formatted(what));
        }

        byte[] bytes = readAll(file);
        uploadPolicy.rejectExecutableContent(
                Arrays.copyOf(bytes, Math.min(bytes.length, MAGIC_BYTES)));
        return new Image(bytes, contentType, filename);
    }

    /** A validated image, ready to store. */
    public record Image(byte[] bytes, String contentType, String filename) {
    }

    private static byte[] readAll(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the uploaded image", e);
        }
    }

    private static String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 0 || dot == filename.length() - 1
                ? ""
                : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}

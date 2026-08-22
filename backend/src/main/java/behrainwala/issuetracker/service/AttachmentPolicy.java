package behrainwala.issuetracker.service;

import behrainwala.issuetracker.config.AppProperties;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Decides whether an upload may be stored at all. Three independent gates, in order:
 * the extension must be on the allow-list, the bytes must not look executable, and the
 * name must survive sanitising. An allow-list is used rather than a list of banned
 * executable extensions because the banned list is never finished - a format nobody
 * thought of is allowed by default under a deny-list, and rejected by default here.
 */
@Component
public class AttachmentPolicy {

    /**
     * Extension to the content type we serve it back as. Everything else is refused.
     * SVG is accepted as a document but, like every other entry, is only ever handed back
     * as an octet-stream download, so it cannot execute script in our origin.
     */
    private static final Map<String, String> ALLOWED = Map.ofEntries(
            Map.entry("pdf", "application/pdf"),
            Map.entry("doc", "application/msword"),
            Map.entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            Map.entry("xls", "application/vnd.ms-excel"),
            Map.entry("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            Map.entry("ppt", "application/vnd.ms-powerpoint"),
            Map.entry("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"),
            Map.entry("txt", "text/plain"),
            Map.entry("md", "text/markdown"),
            Map.entry("csv", "text/csv"),
            Map.entry("json", "application/json"),
            Map.entry("png", "image/png"),
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("gif", "image/gif"),
            Map.entry("webp", "image/webp"),
            Map.entry("svg", "image/svg+xml"),
            Map.entry("zip", "application/zip"));

    /**
     * Leading bytes of the executable formats worth naming, checked no matter what the
     * extension claims: renaming {@code payload.exe} to {@code report.pdf} gets it past the
     * allow-list but not past this.
     */
    private static final Map<String, byte[]> EXECUTABLE_MAGIC = Map.of(
            "a Windows executable", new byte[]{'M', 'Z'},
            "a Linux ELF binary", new byte[]{0x7F, 'E', 'L', 'F'},
            "a shell script", new byte[]{'#', '!'},
            "a Java class file", new byte[]{(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE},
            "a Mach-O binary", new byte[]{(byte) 0xFE, (byte) 0xED, (byte) 0xFA, (byte) 0xCE},
            "a Mach-O binary (64-bit)", new byte[]{(byte) 0xCF, (byte) 0xFA, (byte) 0xED, (byte) 0xFE});

    /** A .docx or .xlsx is a zip underneath, so these legitimately start with "PK". */
    private static final Set<String> ZIP_BASED =
            Set.of("zip", "docx", "xlsx", "pptx");

    private static final byte[] ZIP_MAGIC = {'P', 'K'};

    private final AppProperties.Attachments settings;

    public AttachmentPolicy(AppProperties properties) {
        this.settings = properties.getAttachments();
    }

    public long maxBytes() {
        return settings.getMaxSizeBytes();
    }

    public int maxPerTicket() {
        return settings.getMaxPerTicket();
    }

    /** The extensions offered to the file picker, so the UI never suggests a rejected type. */
    public Set<String> allowedExtensions() {
        return ALLOWED.keySet();
    }

    /**
     * Reduces an uploaded name to something safe to store and echo back: no directory
     * separators, no control characters, no leading dots. The result is display metadata -
     * it is never used to build a path - but a name like {@code ../../etc/passwd} should
     * not be sitting in the database or rendered into a page either.
     */
    public String sanitizeFilename(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("A file name is required");
        }
        // Browsers may send a full path (notably older IE); keep only the last segment.
        String name = raw.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        name = name.replaceAll("[\\p{Cntrl}]", "").trim();
        while (name.startsWith(".")) {
            name = name.substring(1);
        }
        if (name.length() > 200) {
            // Keep the extension: it is what the allow-list check reads.
            String ext = extensionOf(name);
            name = name.substring(0, 200 - ext.length() - 1) + "." + ext;
        }
        if (name.isBlank()) {
            throw new IllegalArgumentException("That file name is not usable");
        }
        return name;
    }

    /**
     * Returns the content type to store for this upload, or throws with a reason the user
     * can act on. The declared type from the browser is deliberately ignored in favour of
     * the one mapped from the extension: a client can claim anything.
     */
    public String resolveContentType(String filename, byte[] head) {
        String ext = extensionOf(filename);
        String contentType = ALLOWED.get(ext);
        if (contentType == null) {
            throw new IllegalArgumentException(
                    "%s files cannot be attached. Allowed: %s"
                            .formatted(ext.isEmpty() ? "Extensionless" : "." + ext, allowedList()));
        }
        rejectExecutableContent(head);
        if (ZIP_BASED.contains(ext) && !startsWith(head, ZIP_MAGIC) && head.length > 0) {
            throw new IllegalArgumentException(
                    "That file is not really a ." + ext + " - its contents do not match its name");
        }
        return contentType;
    }

    /**
     * Reads the magic bytes regardless of what the file is called. Public because it is plain
     * upload hygiene rather than anything attachment-specific - the branding logo is screened
     * with the same check.
     */
    public void rejectExecutableContent(byte[] head) {
        for (Map.Entry<String, byte[]> entry : EXECUTABLE_MAGIC.entrySet()) {
            if (startsWith(head, entry.getValue())) {
                throw new IllegalArgumentException(
                        "That file is " + entry.getKey() + " - executables cannot be attached");
            }
        }
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private static String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 0 || dot == filename.length() - 1
                ? ""
                : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String allowedList() {
        return ALLOWED.keySet().stream().sorted().reduce((a, b) -> a + ", " + b).orElse("");
    }
}

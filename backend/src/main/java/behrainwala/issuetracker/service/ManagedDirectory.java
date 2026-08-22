package behrainwala.issuetracker.service;

import java.nio.file.Path;

/**
 * Resolving and sanity-checking the directories the app writes files into.
 * <p>
 * Every store other than the attachment store holds files that no attachment row will ever
 * point at - which is precisely what {@link OrphanedAttachmentSweeper} deletes. Put one of
 * them inside the swept directory and its contents quietly disappear on the next run, hours
 * later and with nothing to connect cause to effect. So each store checks at startup instead.
 */
final class ManagedDirectory {

    private ManagedDirectory() {
    }

    static Path resolve(String directory) {
        return Path.of(directory).toAbsolutePath().normalize();
    }

    /**
     * Fails startup if {@code root} is the swept attachment directory or sits inside it.
     *
     * @param property the setting to name in the error, so the fix is obvious
     */
    static void rejectInsideAttachments(Path root, Path attachmentRoot, String property) {
        if (root.equals(attachmentRoot) || root.startsWith(attachmentRoot)) {
            throw new IllegalStateException(
                    ("%s (%s) must not sit inside app.attachments.directory (%s) - "
                            + "the nightly orphan sweep would delete its contents")
                            .formatted(property, root, attachmentRoot));
        }
    }
}

package behrainwala.issuetracker.service;

import behrainwala.issuetracker.config.AppProperties;
import behrainwala.issuetracker.repo.AttachmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Removes attachment files that no database row points at.
 * <p>
 * They should not exist. Deleting a ticket or an attachment clears its files after the
 * transaction commits, and an upload writes its row in the same transaction as its file. But
 * both of those can be interrupted - a crash between writing the file and committing the row,
 * an unlink the OS refuses - and the residue is a file nobody can reach and nobody will
 * notice. This sweeps them up nightly rather than letting the directory grow forever.
 * <p>
 * The sweep only ever deletes; it never writes a row. A file with no row is the one thing it
 * acts on, so the worst case for a bug here is deleting a file that was about to be claimed -
 * which is what the grace period exists to prevent.
 */
@Component
public class OrphanedAttachmentSweeper {

    private static final Logger log = LoggerFactory.getLogger(OrphanedAttachmentSweeper.class);

    private final AttachmentRepository attachmentRepository;
    private final AttachmentStorage storage;
    private final AppProperties.Attachments settings;

    public OrphanedAttachmentSweeper(AttachmentRepository attachmentRepository,
                                     AttachmentStorage storage,
                                     AppProperties properties) {
        this.attachmentRepository = attachmentRepository;
        this.storage = storage;
        this.settings = properties.getAttachments();
    }

    /**
     * Nightly, off-peak. Set {@code app.attachments.sweep-cron} to {@code -} to turn the
     * schedule off; the sweep itself stays callable.
     */
    @Scheduled(cron = "${app.attachments.sweep-cron}", zone = "${app.attachments.sweep-zone:UTC}")
    public void scheduledSweep() {
        int removed = sweep();
        if (removed > 0) {
            log.info("Nightly attachment sweep removed {} orphaned file(s)", removed);
        }
    }

    /**
     * Deletes every stored file with no attachment row, and returns how many went.
     * <p>
     * Files younger than the grace period are left alone whatever the database says: an
     * upload writes its bytes before its row commits, so for a moment a perfectly good file
     * looks exactly like an orphan. Waiting a few hours makes that window irrelevant, and an
     * orphan is in no hurry.
     */
    @Transactional(readOnly = true)
    public int sweep() {
        List<String> onDisk = storage.listStorageKeys();
        if (onDisk.isEmpty()) {
            return 0;
        }
        Set<String> known = Set.copyOf(attachmentRepository.findAllStorageKeys());
        Instant cutoff = Instant.now().minus(settings.getOrphanGrace());

        int removed = 0;
        for (String key : onDisk) {
            if (known.contains(key) || storage.lastModified(key).isAfter(cutoff)) {
                continue;
            }
            log.info("Removing orphaned attachment file {}", key);
            storage.delete(key);
            removed++;
        }
        return removed;
    }
}

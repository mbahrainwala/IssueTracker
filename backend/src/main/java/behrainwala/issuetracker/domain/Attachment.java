package behrainwala.issuetracker.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * A document attached to a ticket. The row holds only metadata: the bytes sit on disk under
 * {@link #getStorageKey()}, a server-generated UUID. Nothing the uploader controls ever
 * reaches the filesystem, so {@code filename} is display text and nothing more.
 */
@Entity
@Table(name = "ticket_attachments")
public class Attachment extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false)
    private Ticket ticket;

    @Column(nullable = false, length = 255)
    private String filename;

    @Column(name = "content_type", nullable = false, length = 150)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "storage_key", nullable = false, length = 64, updatable = false)
    private String storageKey;

    /** Lets a later integrity check tell a corrupted file from a tampered one. */
    @Column(nullable = false, length = 64, updatable = false)
    private String sha256;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uploaded_by_id", nullable = false)
    private User uploadedBy;

    protected Attachment() {
    }

    public Attachment(Ticket ticket, String filename, String contentType, long sizeBytes,
                      String storageKey, String sha256, User uploadedBy) {
        this.ticket = ticket;
        this.filename = filename;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.storageKey = storageKey;
        this.sha256 = sha256;
        this.uploadedBy = uploadedBy;
    }

    public Long getId() {
        return id;
    }

    public Ticket getTicket() {
        return ticket;
    }

    public String getFilename() {
        return filename;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public String getSha256() {
        return sha256;
    }

    public User getUploadedBy() {
        return uploadedBy;
    }
}

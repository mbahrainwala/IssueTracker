package behrainwala.issuetracker;

import behrainwala.issuetracker.config.AppProperties;
import behrainwala.issuetracker.repo.AttachmentRepository;
import behrainwala.issuetracker.service.AttachmentStorage;
import behrainwala.issuetracker.service.BrandingLogoStore;
import behrainwala.issuetracker.service.OrphanedAttachmentSweeper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The nightly sweep: files with no row go, files with a row stay, and a file too young to
 * judge is left for the next run.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:issuetracker-sweep;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "app.attachments.directory=${java.io.tmpdir}/issuetracker-sweep-test",
        // A real grace period, so the "too young" rule is actually exercised.
        "app.attachments.orphan-grace=6h",
})
class OrphanedAttachmentSweepTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private OrphanedAttachmentSweeper sweeper;

    @Autowired
    private AttachmentStorage storage;

    @Autowired
    private AttachmentRepository attachmentRepository;

    @Autowired
    private AppProperties properties;

    @Autowired
    private BrandingLogoStore logoStore;

    private static String token;
    private static String ticketKey;

    @BeforeEach
    void setUp() throws Exception {
        // The store is a directory on disk and outlives the test JVM, while the in-memory
        // database does not. Left alone, a file this class backdated on an earlier run comes
        // back as a genuinely aged orphan and shifts the counts these tests assert on.
        clearStore();

        if (token != null) {
            return;
        }
        token = mapper.readTree(mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"sweeper","email":"sweeper@example.com",
                                 "password":"password1","displayName":"Sam Sweeper"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("token").asText();

        mvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"projectKey\":\"SWP\",\"name\":\"Sweeping\"}"))
                .andExpect(status().isCreated());

        ticketKey = mapper.readTree(mvc.perform(post("/api/projects/SWP/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"title\":\"Keeps a document\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("ticketKey").asText();
    }

    private void clearStore() throws Exception {
        Path dir = Path.of(properties.getAttachments().getDirectory());
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (var files = Files.list(dir)) {
            for (Path file : files.toList()) {
                Files.deleteIfExists(file);
            }
        }
    }

    /** Writes a file straight to the store, so nothing in the database points at it. */
    private String orphan() {
        String key = storage.newStorageKey();
        storage.store(new MockMultipartFile("file", "stray.pdf", "application/pdf",
                "%PDF-1.4 stray".getBytes(StandardCharsets.UTF_8)), key);
        return key;
    }

    /** Backdates a file so it is older than the grace period. */
    private void age(String storageKey, int hours) throws Exception {
        Path path = Path.of(properties.getAttachments().getDirectory()).resolve(storageKey);
        Files.setLastModifiedTime(path, FileTime.from(Instant.now().minus(hours, ChronoUnit.HOURS)));
    }

    @Test
    void removesAnOrphanAndKeepsWhatTheDatabaseClaims() throws Exception {
        long attachmentId = mapper.readTree(mvc.perform(multipart("/api/tickets/" + ticketKey + "/attachments")
                        .file(new MockMultipartFile("file", "kept.pdf", "application/pdf",
                                "%PDF-1.4 kept".getBytes(StandardCharsets.UTF_8)))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
        String keptKey = attachmentRepository.findById(attachmentId).orElseThrow().getStorageKey();

        String strayKey = orphan();
        // Backdate both: age alone must not decide anything, only age plus having no row.
        age(strayKey, 24);
        age(keptKey, 24);

        assertThat(sweeper.sweep()).isEqualTo(1);
        assertThat(storage.exists(strayKey)).isFalse();
        assertThat(storage.exists(keptKey)).isTrue();
        assertThat(attachmentRepository.findById(attachmentId)).isPresent();
    }

    @Test
    void leavesAFreshOrphanAloneUntilTheGracePeriodPasses() throws Exception {
        String strayKey = orphan();

        // Just written: indistinguishable from an upload whose row has not committed yet.
        assertThat(sweeper.sweep()).isZero();
        assertThat(storage.exists(strayKey)).isTrue();

        age(strayKey, 24);
        assertThat(sweeper.sweep()).isEqualTo(1);
        assertThat(storage.exists(strayKey)).isFalse();
    }

    @Test
    void sweepingAnAlreadyCleanStoreDoesNothing() throws Exception {
        assertThat(sweeper.sweep()).isZero();
    }

    /**
     * The logo is a file no attachment row will ever point at, which is exactly what the sweep
     * deletes. It survives only because it lives in a different directory - so that is what is
     * asserted here rather than left to the configuration to get right quietly.
     */
    @Test
    void theBrandingLogoIsNotSwept() throws Exception {
        logoStore.write("a logo, not an attachment".getBytes(StandardCharsets.UTF_8));
        assertThat(logoStore.exists()).isTrue();

        String strayKey = orphan();
        age(strayKey, 24);

        assertThat(sweeper.sweep()).isEqualTo(1);
        assertThat(storage.exists(strayKey)).isFalse();
        assertThat(logoStore.exists()).isTrue();
    }
}

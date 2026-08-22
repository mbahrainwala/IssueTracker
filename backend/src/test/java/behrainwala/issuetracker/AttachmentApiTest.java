package behrainwala.issuetracker;

import behrainwala.issuetracker.repo.AttachmentRepository;
import behrainwala.issuetracker.service.AttachmentStorage;
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
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Attachments, from the angle that matters: who can reach a stored document, and what is
 * allowed to become one in the first place.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties =
        "spring.datasource.url=jdbc:h2:mem:issuetracker-attach;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
class AttachmentApiTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    /** Injected so the assertions can look at the disk, not just the API responses. */
    @Autowired
    private AttachmentRepository attachmentRepository;

    @Autowired
    private AttachmentStorage storage;

    private static String ownerToken;
    private static String outsiderToken;
    private static String ticketKey;

    @BeforeEach
    void setUp() throws Exception {
        if (ownerToken != null) {
            return;
        }
        ownerToken = register("docowner", "Dana Owner");
        outsiderToken = register("docoutsider", "Otto Outsider");

        mvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + ownerToken)
                        .content("{\"projectKey\":\"DOC\",\"name\":\"Documents\"}"))
                .andExpect(status().isCreated());

        ticketKey = mapper.readTree(mvc.perform(post("/api/projects/DOC/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + ownerToken)
                        .content("{\"title\":\"Has documents\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("ticketKey").asText();
    }

    private String register(String username, String displayName) throws Exception {
        return mapper.readTree(mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","email":"%s@example.com",
                                 "password":"password1","displayName":"%s"}
                                """.formatted(username, username, displayName)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("token").asText();
    }

    private ResultActions upload(String token, String filename, byte[] bytes) throws Exception {
        return mvc.perform(multipart("/api/tickets/" + ticketKey + "/attachments")
                .file(new MockMultipartFile("file", filename, "application/octet-stream", bytes))
                .header("Authorization", "Bearer " + token));
    }

    private static byte[] pdf(String text) {
        return ("%PDF-1.4\n" + text).getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void severalDocumentsCanBeAttachedAndReadBack() throws Exception {
        upload(ownerToken, "spec.pdf", pdf("spec")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.filename").value("spec.pdf"))
                .andExpect(jsonPath("$.contentType").value("application/pdf"))
                .andExpect(jsonPath("$.uploadedBy.displayName").value("Dana Owner"));
        upload(ownerToken, "notes.txt", "plain notes".getBytes(StandardCharsets.UTF_8))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/tickets/" + ticketKey + "/attachments")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void executablesAreRefusedByExtensionAndByContent() throws Exception {
        upload(ownerToken, "setup.exe", "MZ harmless here".getBytes(StandardCharsets.UTF_8))
                .andExpect(status().isBadRequest());

        // The interesting case: an executable wearing an allowed extension.
        upload(ownerToken, "invoice.pdf", new byte[]{'M', 'Z', (byte) 0x90, 0x00})
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(
                        "That file is a Windows executable - executables cannot be attached"));

        upload(ownerToken, "run.sh", "#!/bin/sh\nrm -rf /".getBytes(StandardCharsets.UTF_8))
                .andExpect(status().isBadRequest());
        upload(ownerToken, "payload.pdf", "#!/bin/sh\necho hi".getBytes(StandardCharsets.UTF_8))
                .andExpect(status().isBadRequest());
        upload(ownerToken, "app.jar", "PK".getBytes(StandardCharsets.UTF_8))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aFileNameCannotCarryAPath() throws Exception {
        String stored = mapper.readTree(
                        upload(ownerToken, "../../../etc/passwd.txt", "not really".getBytes(StandardCharsets.UTF_8))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString())
                .get("filename").asText();

        assertThat(stored).isEqualTo("passwd.txt");
    }

    @Test
    void downloadsAreForcedToSaveRatherThanRender() throws Exception {
        // An HTML payload in an allowed wrapper: it must never come back as renderable HTML.
        long id = mapper.readTree(upload(ownerToken, "evil.svg",
                        "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>"
                                .getBytes(StandardCharsets.UTF_8))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString()).get("id").asLong();

        mvc.perform(get("/api/attachments/" + id).header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_OCTET_STREAM))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.startsWith("attachment;")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    void onlyProjectMembersCanListOrDownload() throws Exception {
        long id = mapper.readTree(upload(ownerToken, "private.pdf", pdf("secret"))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString()).get("id").asLong();

        mvc.perform(get("/api/tickets/" + ticketKey + "/attachments")
                        .header("Authorization", "Bearer " + outsiderToken))
                .andExpect(status().isForbidden());

        // Knowing the id is not access: the download re-checks the project.
        mvc.perform(get("/api/attachments/" + id).header("Authorization", "Bearer " + outsiderToken))
                .andExpect(status().isForbidden());

        mvc.perform(delete("/api/attachments/" + id).header("Authorization", "Bearer " + outsiderToken))
                .andExpect(status().isForbidden());

        // ...and no token at all is not access either.
        mvc.perform(get("/api/attachments/" + id)).andExpect(status().isUnauthorized());
    }

    @Test
    void anArchivedTicketTakesNoNewDocuments() throws Exception {
        String key = mapper.readTree(mvc.perform(post("/api/projects/DOC/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + ownerToken)
                        .content("{\"title\":\"Going to the archive\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("ticketKey").asText();

        long id = mapper.readTree(mvc.perform(multipart("/api/tickets/" + key + "/attachments")
                        .file(new MockMultipartFile("file", "before.pdf", "application/pdf", pdf("before")))
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asLong();

        mvc.perform(patch("/api/tickets/" + key + "/status").param("status", "Done")
                .header("Authorization", "Bearer " + ownerToken)).andExpect(status().isOk());
        mvc.perform(post("/api/tickets/" + key + "/archive")
                .header("Authorization", "Bearer " + ownerToken)).andExpect(status().isOk());

        mvc.perform(multipart("/api/tickets/" + key + "/attachments")
                        .file(new MockMultipartFile("file", "after.pdf", "application/pdf", pdf("after")))
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isConflict());
        mvc.perform(delete("/api/attachments/" + id).header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isConflict());

        // Reading an archived ticket's documents is still fine.
        mvc.perform(get("/api/attachments/" + id).header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());
    }

    @Test
    void deletingATicketClearsItsDocumentsFromDisk() throws Exception {
        String key = mapper.readTree(mvc.perform(post("/api/projects/DOC/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + ownerToken)
                        .content("{\"title\":\"Doomed, with documents\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("ticketKey").asText();

        for (String name : new String[]{"one.pdf", "two.pdf"}) {
            mvc.perform(multipart("/api/tickets/" + key + "/attachments")
                            .file(new MockMultipartFile("file", name, "application/pdf", pdf(name)))
                            .header("Authorization", "Bearer " + ownerToken))
                    .andExpect(status().isCreated());
        }

        long ticketId = mapper.readTree(mvc.perform(get("/api/tickets/" + key)
                        .header("Authorization", "Bearer " + ownerToken))
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
        List<String> keys = attachmentRepository.findStorageKeysByTicketId(ticketId);
        assertThat(keys).hasSize(2);
        assertThat(keys).allMatch(storage::exists);

        mvc.perform(delete("/api/tickets/" + key).header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNoContent());

        // The rows go with the ticket; the files must not be left behind on disk.
        assertThat(attachmentRepository.findStorageKeysByTicketId(ticketId)).isEmpty();
        assertThat(keys).noneMatch(storage::exists);
    }

    @Test
    void removingOneDocumentClearsOnlyItsOwnFile() throws Exception {
        long kept = mapper.readTree(upload(ownerToken, "kept.pdf", pdf("kept"))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString()).get("id").asLong();
        long removed = mapper.readTree(upload(ownerToken, "removed.pdf", pdf("removed"))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString()).get("id").asLong();

        String keptKey = attachmentRepository.findById(kept).orElseThrow().getStorageKey();
        String removedKey = attachmentRepository.findById(removed).orElseThrow().getStorageKey();

        mvc.perform(delete("/api/attachments/" + removed).header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNoContent());

        assertThat(storage.exists(removedKey)).isFalse();
        assertThat(storage.exists(keptKey)).isTrue();
    }

    @Test
    void theUploaderCanRemoveTheirOwnDocument() throws Exception {
        long id = mapper.readTree(upload(ownerToken, "temporary.csv", "a,b\n1,2".getBytes(StandardCharsets.UTF_8))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString()).get("id").asLong();

        mvc.perform(delete("/api/attachments/" + id).header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/attachments/" + id).header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNotFound());
    }
}

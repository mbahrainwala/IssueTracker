package behrainwala.issuetracker;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Who may remove a comment or an attachment: the person who added it, or a global
 * administrator. Nobody else - notably not a project lead, who leads the project but does
 * not own what other people wrote in it - and nobody at all once the ticket is archived.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties =
        "spring.datasource.url=jdbc:h2:mem:issuetracker-ownership;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
class ContentOwnershipApiTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    /** The first account registered becomes the global ADMIN, and leads the project. */
    private static String adminToken;
    /** A LEAD on the project, but an ordinary USER globally. */
    private static String leadToken;
    /** The person whose comment and attachment everyone else tries to remove. */
    private static String authorToken;
    private static String ticketKey;

    @BeforeEach
    void setUp() throws Exception {
        if (adminToken != null) {
            return;
        }
        adminToken = register("owneradmin", "Ada Admin");
        leadToken = register("ownerlead", "Lena Lead");
        authorToken = register("ownerauthor", "Amir Author");

        mvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + adminToken)
                        .content("{\"projectKey\":\"OWN\",\"name\":\"Ownership\"}"))
                .andExpect(status().isCreated());

        addMember("ownerlead", "LEAD");
        addMember("ownerauthor", "MEMBER");

        ticketKey = mapper.readTree(mvc.perform(post("/api/projects/OWN/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + adminToken)
                        .content("{\"title\":\"Whose content is it\"}"))
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

    private void addMember(String username, String projectRole) throws Exception {
        long userId = userIdOf(username);
        mvc.perform(post("/api/projects/OWN/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + adminToken)
                        .content("{\"userId\":%d,\"projectRole\":\"%s\"}".formatted(userId, projectRole)))
                .andExpect(status().isOk());
    }

    private long userIdOf(String username) throws Exception {
        var users = mapper.readTree(mvc.perform(get("/api/users")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        for (var user : users) {
            if (username.equals(user.get("username").asText())) {
                return user.get("id").asLong();
            }
        }
        throw new IllegalStateException("No user " + username);
    }

    private long commentAsAuthor(String body) throws Exception {
        return mapper.readTree(mvc.perform(post("/api/tickets/" + ticketKey + "/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + authorToken)
                        .content("{\"body\":\"%s\"}".formatted(body)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    private long attachmentAsAuthor(String filename) throws Exception {
        return mapper.readTree(mvc.perform(multipart("/api/tickets/" + ticketKey + "/attachments")
                        .file(new MockMultipartFile("file", filename, "application/pdf",
                                "%PDF-1.4 owned".getBytes(StandardCharsets.UTF_8)))
                        .header("Authorization", "Bearer " + authorToken))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    @Test
    void aProjectLeadCannotRemoveSomeoneElsesComment() throws Exception {
        long id = commentAsAuthor("Mine, not the lead's");

        mvc.perform(delete("/api/comments/" + id).header("Authorization", "Bearer " + leadToken))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/comments/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + leadToken)
                        .content("{\"body\":\"Rewritten by the lead\"}"))
                .andExpect(status().isForbidden());

        // Unchanged, and still there.
        mvc.perform(get("/api/tickets/" + ticketKey + "/comments")
                        .header("Authorization", "Bearer " + authorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + id + ")].body").value("Mine, not the lead's"));
    }

    @Test
    void aProjectLeadCannotRemoveSomeoneElsesAttachment() throws Exception {
        long id = attachmentAsAuthor("lead-cannot-touch.pdf");

        mvc.perform(delete("/api/attachments/" + id).header("Authorization", "Bearer " + leadToken))
                .andExpect(status().isForbidden());
        // Reading it is still fine - the lead is a member of the project.
        mvc.perform(get("/api/attachments/" + id).header("Authorization", "Bearer " + leadToken))
                .andExpect(status().isOk());
    }

    @Test
    void theAuthorAndTheAdminMayBothRemoveContent() throws Exception {
        long ownComment = commentAsAuthor("The author removes this one");
        mvc.perform(delete("/api/comments/" + ownComment).header("Authorization", "Bearer " + authorToken))
                .andExpect(status().isNoContent());

        long otherComment = commentAsAuthor("The admin removes this one");
        mvc.perform(delete("/api/comments/" + otherComment).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        long ownFile = attachmentAsAuthor("author-removes.pdf");
        mvc.perform(delete("/api/attachments/" + ownFile).header("Authorization", "Bearer " + authorToken))
                .andExpect(status().isNoContent());

        long adminFile = attachmentAsAuthor("admin-removes.pdf");
        mvc.perform(delete("/api/attachments/" + adminFile).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());
    }

    @Test
    void onlyAnAdministratorCanDeleteATicket() throws Exception {
        String key = mapper.readTree(mvc.perform(post("/api/projects/OWN/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + adminToken)
                        .content("{\"title\":\"Who can delete me\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("ticketKey").asText();

        // A project lead may run the project, but deleting a ticket is irreversible.
        mvc.perform(delete("/api/tickets/" + key).header("Authorization", "Bearer " + leadToken))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/tickets/" + key).header("Authorization", "Bearer " + authorToken))
                .andExpect(status().isForbidden());

        // Still there, and the lead can still archive it instead.
        mvc.perform(get("/api/tickets/" + key).header("Authorization", "Bearer " + leadToken))
                .andExpect(status().isOk());

        mvc.perform(delete("/api/tickets/" + key).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/tickets/" + key).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void archivingFreezesCommentsAndAttachmentsForEveryone() throws Exception {
        String key = mapper.readTree(mvc.perform(post("/api/projects/OWN/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + adminToken)
                        .content("{\"title\":\"About to be frozen\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("ticketKey").asText();

        long commentId = mapper.readTree(mvc.perform(post("/api/tickets/" + key + "/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + authorToken)
                        .content("{\"body\":\"Written while it was live\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
        long attachmentId = mapper.readTree(mvc.perform(multipart("/api/tickets/" + key + "/attachments")
                        .file(new MockMultipartFile("file", "frozen.pdf", "application/pdf",
                                "%PDF-1.4 frozen".getBytes(StandardCharsets.UTF_8)))
                        .header("Authorization", "Bearer " + authorToken))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asLong();

        mvc.perform(patch("/api/tickets/" + key + "/status").param("status", "DONE")
                .header("Authorization", "Bearer " + adminToken)).andExpect(status().isOk());
        mvc.perform(post("/api/tickets/" + key + "/archive")
                .header("Authorization", "Bearer " + adminToken)).andExpect(status().isOk());

        // Not the author, not the admin - archived means archived.
        for (String token : new String[]{authorToken, adminToken}) {
            mvc.perform(delete("/api/comments/" + commentId).header("Authorization", "Bearer " + token))
                    .andExpect(status().isConflict());
            mvc.perform(put("/api/comments/" + commentId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("Authorization", "Bearer " + token)
                            .content("{\"body\":\"Edited after archiving\"}"))
                    .andExpect(status().isConflict());
            mvc.perform(post("/api/tickets/" + key + "/comments")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("Authorization", "Bearer " + token)
                            .content("{\"body\":\"Added after archiving\"}"))
                    .andExpect(status().isConflict());

            mvc.perform(delete("/api/attachments/" + attachmentId)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isConflict());
            mvc.perform(multipart("/api/tickets/" + key + "/attachments")
                            .file(new MockMultipartFile("file", "after.pdf", "application/pdf",
                                    "%PDF-1.4 after".getBytes(StandardCharsets.UTF_8)))
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isConflict());
        }

        // Reading is untouched.
        mvc.perform(get("/api/tickets/" + key + "/comments").header("Authorization", "Bearer " + authorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/attachments/" + attachmentId).header("Authorization", "Bearer " + authorToken))
                .andExpect(status().isOk());
    }
}

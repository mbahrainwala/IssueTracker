package behrainwala.issuetracker;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * "Archived is read-only" applied to the routes that do not pass through the ordinary
 * write guard: comment editing by its own author, unlinking, and admin-only membership
 * changes. Each of these could otherwise mutate archived content.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties =
        "spring.datasource.url=jdbc:h2:mem:issuetracker-readonly;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
class ArchiveReadOnlyApiTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    private static String token;
    private static long otherUserId;

    @BeforeEach
    void setUp() throws Exception {
        if (token != null) {
            return;
        }
        token = mapper.readTree(mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"keeper","email":"keeper@example.com",
                                 "password":"password1","displayName":"Kim Keeper"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("token").asText();

        otherUserId = mapper.readTree(mvc.perform(post("/api/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("""
                                {"username":"helper","email":"helper@example.com","password":"password1",
                                 "displayName":"Hal Helper","role":"USER"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asLong();

        for (String key : new String[]{"RO", "RO2"}) {
            mvc.perform(post("/api/projects")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("Authorization", "Bearer " + token)
                            .content("{\"projectKey\":\"%s\",\"name\":\"%s\"}".formatted(key, key)))
                    .andExpect(status().isCreated());
        }
    }

    private String ticket(String project, String title, String type) throws Exception {
        return mapper.readTree(mvc.perform(post("/api/projects/" + project + "/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"title\":\"%s\",\"type\":\"%s\"}".formatted(title, type)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("ticketKey").asText();
    }

    private void archiveTicket(String key) throws Exception {
        mvc.perform(patch("/api/tickets/" + key + "/status").param("status", "Done")
                .header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        mvc.perform(post("/api/tickets/" + key + "/archive")
                .header("Authorization", "Bearer " + token)).andExpect(status().isOk());
    }

    private long comment(String ticketKey) throws Exception {
        return mapper.readTree(mvc.perform(post("/api/tickets/" + ticketKey + "/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"body\":\"Before the archive\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    @Test
    void anArchivedTicketAcceptsNoComments() throws Exception {
        String t = ticket("RO", "No new comments", "TASK");
        long existing = comment(t);
        archiveTicket(t);

        mvc.perform(post("/api/tickets/" + t + "/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"body\":\"After the archive\"}"))
                .andExpect(status().isConflict());

        // The author shortcut must not become a back door.
        mvc.perform(put("/api/comments/" + existing)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"body\":\"Edited after the archive\"}"))
                .andExpect(status().isConflict());

        mvc.perform(delete("/api/comments/" + existing).header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());

        // Reading is still fine.
        mvc.perform(get("/api/tickets/" + t + "/comments").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].body").value("Before the archive"));
    }

    @Test
    void commentsInAnArchivedProjectAreFrozenForTheirAuthorToo() throws Exception {
        String t = ticket("RO2", "Project gets archived", "TASK");
        long existing = comment(t);

        mvc.perform(post("/api/projects/RO2/archive").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mvc.perform(put("/api/comments/" + existing)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"body\":\"Edited in an archived project\"}"))
                .andExpect(status().isConflict());
        mvc.perform(delete("/api/comments/" + existing).header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());

        mvc.perform(post("/api/projects/RO2/restore").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/comments/" + existing).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    @Test
    void archivedTicketsCannotBeLinkedOrUnlinked() throws Exception {
        String a = ticket("RO", "Link end A", "TASK");
        String b = ticket("RO", "Link end B", "TASK");
        String c = ticket("RO", "Link end C", "TASK");

        long linkId = mapper.readTree(mvc.perform(post("/api/tickets/" + a + "/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"linkType\":\"RELATES_TO\",\"targetTicketKey\":\"%s\"}".formatted(b)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get(0).get("id").asLong();

        archiveTicket(a);

        // Neither end may gain a link...
        mvc.perform(post("/api/tickets/" + a + "/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"linkType\":\"BLOCKS\",\"targetTicketKey\":\"%s\"}".formatted(c)))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/tickets/" + c + "/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"linkType\":\"BLOCKS\",\"targetTicketKey\":\"%s\"}".formatted(a)))
                .andExpect(status().isConflict());

        // ...nor lose one. Unlinking skips the write guard, so this is the interesting case.
        mvc.perform(delete("/api/links/" + linkId).header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());

        mvc.perform(get("/api/tickets/" + a + "/links").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void liveWorkCannotBeFiledUnderAnArchivedEpic() throws Exception {
        String epic = ticket("RO", "Archived epic", "EPIC");
        archiveTicket(epic);
        String live = ticket("RO", "Still open", "TASK");

        mvc.perform(patch("/api/tickets/" + live)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"epicKey\":\"%s\"}".formatted(epic)))
                .andExpect(status().isConflict());

        mvc.perform(post("/api/projects/RO/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"title\":\"Born into an archived epic\",\"epicKey\":\"%s\"}".formatted(epic)))
                .andExpect(status().isConflict());
    }

    @Test
    void anArchivedEpicKeepsItsChildren() throws Exception {
        String epic = ticket("RO", "Frozen epic", "EPIC");
        String child = ticket("RO", "Child of a frozen epic", "TASK");
        mvc.perform(post("/api/tickets/" + epic + "/children")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"ticketKeys\":[\"%s\"]}".formatted(child)))
                .andExpect(status().isOk());

        archiveTicket(child);
        archiveTicket(epic);

        // Detaching goes through its own path, so the archive check has to hold there too.
        mvc.perform(delete("/api/tickets/" + epic + "/children/" + child)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());

        mvc.perform(get("/api/tickets/" + epic + "/children").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void adminAssignmentCannotReshuffleAnArchivedProject() throws Exception {
        mvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"projectKey\":\"ROADM\",\"name\":\"Admin path\"}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/projects/ROADM/archive").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // The admin assignment endpoint bypasses AccessGuard, so it needs its own check.
        mvc.perform(put("/api/admin/users/" + otherUserId + "/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"assignments\":[{\"projectKey\":\"ROADM\",\"projectRole\":\"MEMBER\"}]}"))
                .andExpect(status().isConflict());
    }
}

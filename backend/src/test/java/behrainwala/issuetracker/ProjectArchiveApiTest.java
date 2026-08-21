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

/** Archiving a project hides and freezes it, and can always be undone. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties =
        "spring.datasource.url=jdbc:h2:mem:issuetracker-projarchive;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
class ProjectArchiveApiTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    private static String token;
    private static long outsiderId;

    @BeforeEach
    void setUp() throws Exception {
        if (token != null) {
            return;
        }
        token = mapper.readTree(mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"boss","email":"boss@example.com",
                                 "password":"password1","displayName":"Bea Boss"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("token").asText();

        outsiderId = mapper.readTree(mvc.perform(post("/api/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("""
                                {"username":"newbie","email":"newbie@example.com","password":"password1",
                                 "displayName":"New Bie","role":"USER"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    private void createProject(String key) throws Exception {
        mvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"projectKey\":\"%s\",\"name\":\"%s\"}".formatted(key, key)))
                .andExpect(status().isCreated());
    }

    private void archive(String key, int expected) throws Exception {
        mvc.perform(post("/api/projects/" + key + "/archive").header("Authorization", "Bearer " + token))
                .andExpect(status().is(expected));
    }

    private void restore(String key, int expected) throws Exception {
        mvc.perform(post("/api/projects/" + key + "/restore").header("Authorization", "Bearer " + token))
                .andExpect(status().is(expected));
    }

    @Test
    void archivedProjectsMoveToTheirOwnTabAndComeBack() throws Exception {
        createProject("KEEP");
        createProject("SHELVE");

        archive("SHELVE", 200);

        mvc.perform(get("/api/projects").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.projectKey == 'KEEP')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.projectKey == 'SHELVE')]").isEmpty());

        mvc.perform(get("/api/projects").param("archived", "true")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.projectKey == 'SHELVE')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.projectKey == 'SHELVE')].archivedBy.displayName").value("Bea Boss"))
                .andExpect(jsonPath("$[?(@.projectKey == 'KEEP')]").isEmpty());

        // Reversible, which is what separates archiving from deleting.
        restore("SHELVE", 200);
        mvc.perform(get("/api/projects").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$[?(@.projectKey == 'SHELVE')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.projectKey == 'SHELVE')].archived").value(false));
    }

    @Test
    void anArchivedProjectIsFrozen() throws Exception {
        createProject("FROZEN");
        String ticket = mapper.readTree(mvc.perform(post("/api/projects/FROZEN/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"title\":\"Made before archiving\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("ticketKey").asText();

        archive("FROZEN", 200);

        mvc.perform(post("/api/projects/FROZEN/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"title\":\"Should be refused\"}"))
                .andExpect(status().isConflict());

        mvc.perform(patch("/api/tickets/" + ticket + "/status")
                        .param("status", "TODO")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());

        mvc.perform(post("/api/tickets/" + ticket + "/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"body\":\"Should be refused\"}"))
                .andExpect(status().isConflict());

        mvc.perform(put("/api/projects/FROZEN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"name\":\"Renamed while archived\"}"))
                .andExpect(status().isConflict());

        mvc.perform(post("/api/projects/FROZEN/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"userId\":%d,\"projectRole\":\"MEMBER\"}".formatted(outsiderId)))
                .andExpect(status().isConflict());
    }

    @Test
    void readingAnArchivedProjectStillWorks() throws Exception {
        createProject("READABLE");
        mvc.perform(post("/api/projects/READABLE/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"title\":\"Still visible\"}"))
                .andExpect(status().isCreated());

        archive("READABLE", 200);

        mvc.perform(get("/api/projects/READABLE").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archived").value(true))
                .andExpect(jsonPath("$.archivedAt").isNotEmpty());

        mvc.perform(get("/api/projects/READABLE/tickets").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));

        mvc.perform(get("/api/projects/READABLE/members").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void everythingIsUsableAgainAfterRestoring() throws Exception {
        createProject("BACKUP");
        archive("BACKUP", 200);
        restore("BACKUP", 200);

        mvc.perform(post("/api/projects/BACKUP/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"title\":\"Works again\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void rejectsDoubleArchiveAndPointlessRestore() throws Exception {
        createProject("ONCE");

        restore("ONCE", 409);
        archive("ONCE", 200);
        archive("ONCE", 409);
    }

    @Test
    void anArchivedProjectCanStillBeDeleted() throws Exception {
        createProject("DOOMED");
        archive("DOOMED", 200);

        mvc.perform(delete("/api/projects/DOOMED").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/projects/DOOMED").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void aProjectWithTicketsCannotBeDeleted() throws Exception {
        createProject("FULL");
        String key = mapper.readTree(mvc.perform(post("/api/projects/FULL/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"title\":\"In the way\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("ticketKey").asText();

        mvc.perform(delete("/api/projects/FULL").header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(
                        "FULL still has 1 ticket, archived included - delete them before deleting the project"));

        // Archiving the ticket is not the same as removing it, so the project is still blocked.
        mvc.perform(patch("/api/tickets/" + key + "/status").param("status", "DONE")
                .header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        mvc.perform(post("/api/tickets/" + key + "/archive")
                .header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        mvc.perform(delete("/api/projects/FULL").header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());

        // Emptied, it goes.
        mvc.perform(delete("/api/tickets/" + key).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/api/projects/FULL").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    @Test
    void onlyLeadsAndAdminsMayArchive() throws Exception {
        createProject("GUARDED");
        mvc.perform(post("/api/projects/GUARDED/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"userId\":%d,\"projectRole\":\"MEMBER\"}".formatted(outsiderId)))
                .andExpect(status().isOk());

        String memberToken = mapper.readTree(mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usernameOrEmail\":\"newbie\",\"password\":\"password1\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("token").asText();

        mvc.perform(post("/api/projects/GUARDED/archive").header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isForbidden());
    }
}

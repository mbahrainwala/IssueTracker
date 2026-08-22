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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Reshaping a board that is already in use. The awkward part is that a lane's name is the
 * value its tickets carry, so renaming one has to take them with it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties =
        "spring.datasource.url=jdbc:h2:mem:issuetracker-lanes;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
class ProjectLanesApiTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    private static String leadToken;
    private static String memberToken;

    @BeforeEach
    void setUp() throws Exception {
        if (leadToken != null) {
            return;
        }
        leadToken = register("laneowner", "Lana Owner");
        memberToken = register("lanemember", "Milo Member");

        mvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + leadToken)
                        .content("{\"projectKey\":\"LANE\",\"name\":\"Lanes\"}"))
                .andExpect(status().isCreated());

        var users = mapper.readTree(mvc.perform(get("/api/users")
                        .header("Authorization", "Bearer " + leadToken))
                .andReturn().getResponse().getContentAsString());
        long memberId = 0;
        for (var u : users) {
            if ("lanemember".equals(u.get("username").asText())) {
                memberId = u.get("id").asLong();
            }
        }
        mvc.perform(post("/api/projects/LANE/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + leadToken)
                        .content("{\"userId\":%d,\"projectRole\":\"MEMBER\"}".formatted(memberId)))
                .andExpect(status().isOk());
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

    private String newTicket(String project, String title) throws Exception {
        return mapper.readTree(mvc.perform(post("/api/projects/" + project + "/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + leadToken)
                        .content("{\"title\":\"%s\"}".formatted(title)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("ticketKey").asText();
    }

    /** The board as the client sees it, so a submission can name lanes by id. */
    private com.fasterxml.jackson.databind.JsonNode lanesOf(String project) throws Exception {
        return mapper.readTree(mvc.perform(get("/api/projects/" + project + "/lanes")
                        .header("Authorization", "Bearer " + leadToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private long laneId(String project, String name) throws Exception {
        for (var lane : lanesOf(project)) {
            if (name.equals(lane.get("name").asText())) {
                return lane.get("id").asLong();
            }
        }
        throw new IllegalStateException("no lane " + name + " in " + project);
    }

    private void createProject(String key) throws Exception {
        mvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + leadToken)
                        .content("{\"projectKey\":\"%s\",\"name\":\"%s\"}".formatted(key, key)))
                .andExpect(status().isCreated());
    }

    @Test
    void renamingALaneCarriesItsTicketsAlong() throws Exception {
        createProject("RENAME");
        String ticket = newTicket("RENAME", "Sitting in the first lane");

        mvc.perform(get("/api/tickets/" + ticket).header("Authorization", "Bearer " + leadToken))
                .andExpect(jsonPath("$.status").value("To Do"));

        mvc.perform(put("/api/projects/RENAME/lanes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + leadToken)
                        .content("""
                                {"lanes":[{"id":%d,"name":"Up Next","initial":true,"done":false},
                                          {"id":%d,"name":"In Progress","initial":false,"done":false},
                                          {"id":%d,"name":"Done","initial":false,"done":true}]}
                                """.formatted(laneId("RENAME", "To Do"),
                                        laneId("RENAME", "In Progress"),
                                        laneId("RENAME", "Done"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Up Next"));

        // The ticket moved with the lane rather than being stranded in a lane that is gone.
        mvc.perform(get("/api/tickets/" + ticket).header("Authorization", "Bearer " + leadToken))
                .andExpect(jsonPath("$.status").value("Up Next"));
    }

    @Test
    void aLaneWithTicketsInItCannotBeRemoved() throws Exception {
        createProject("BUSY");
        newTicket("BUSY", "Holding the lane open");

        mvc.perform(put("/api/projects/BUSY/lanes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + leadToken)
                        // "To Do" is left out entirely - and it is where the ticket sits.
                        .content("""
                                {"lanes":[{"id":%d,"name":"In Progress","initial":true,"done":false},
                                          {"id":%d,"name":"Done","initial":false,"done":true}]}
                                """.formatted(laneId("BUSY", "In Progress"), laneId("BUSY", "Done"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(
                        org.hamcrest.Matchers.containsString("still holds")));
    }

    @Test
    void anEmptyLaneCanBeRemovedAndNewOnesAdded() throws Exception {
        createProject("GROW");

        mvc.perform(put("/api/projects/GROW/lanes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + leadToken)
                        // Two lanes renamed in place, two brand new ones (no id).
                        .content("""
                                {"lanes":[{"id":%d,"name":"Ideas","initial":true,"done":false},
                                          {"id":%d,"name":"Drafting","initial":false,"done":false},
                                          {"name":"Review","initial":false,"done":false},
                                          {"id":%d,"name":"Published","initial":false,"done":true}]}
                                """.formatted(laneId("GROW", "To Do"),
                                        laneId("GROW", "In Progress"),
                                        laneId("GROW", "Done"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[3].name").value("Published"));

        // New tickets follow the new starting lane.
        mvc.perform(post("/api/projects/GROW/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + leadToken)
                        .content("{\"title\":\"A thought\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("Ideas"));
    }

    @Test
    void onlyALeadOrAdminMayReshapeTheBoard() throws Exception {
        mvc.perform(put("/api/projects/LANE/lanes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + memberToken)
                        .content("""
                                {"lanes":[{"name":"Mine","initial":true,"done":true}]}
                                """))
                .andExpect(status().isForbidden());

        // Reading the board is open to any member.
        mvc.perform(get("/api/projects/LANE/lanes").header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    void movingTheFinishedLaneMovesWhatMayBeArchived() throws Exception {
        createProject("FIN");
        String ticket = newTicket("FIN", "Finished early");
        mvc.perform(patch("/api/tickets/" + ticket + "/status").param("status", "In Progress")
                .header("Authorization", "Bearer " + leadToken)).andExpect(status().isOk());

        // Not archivable yet: "Done" is the finished lane.
        mvc.perform(post("/api/tickets/" + ticket + "/archive")
                        .header("Authorization", "Bearer " + leadToken))
                .andExpect(status().isConflict());

        // Declare "In Progress" the finished lane instead, and the same ticket qualifies.
        mvc.perform(put("/api/projects/FIN/lanes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + leadToken)
                        .content("""
                                {"lanes":[{"id":%d,"name":"To Do","initial":true,"done":false},
                                          {"id":%d,"name":"In Progress","initial":false,"done":true},
                                          {"id":%d,"name":"Done","initial":false,"done":false}]}
                                """.formatted(laneId("FIN", "To Do"),
                                        laneId("FIN", "In Progress"),
                                        laneId("FIN", "Done"))))
                .andExpect(status().isOk());

        mvc.perform(post("/api/tickets/" + ticket + "/archive")
                        .header("Authorization", "Bearer " + leadToken))
                .andExpect(status().isOk());
    }
}

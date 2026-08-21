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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Moving a ticket between buckets records who moved it, from where and to where. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties =
        "spring.datasource.url=jdbc:h2:mem:issuetracker-history;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
class TicketStatusHistoryApiTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    private static String adminToken;
    private static String moverToken;

    @BeforeEach
    void setUp() throws Exception {
        if (adminToken != null) {
            return;
        }
        adminToken = mapper.readTree(mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"owner","email":"owner@example.com",
                                 "password":"password1","displayName":"Olivia Owner"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("token").asText();

        mvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + adminToken)
                        .content("{\"projectKey\":\"HIS\",\"name\":\"History\"}"))
                .andExpect(status().isCreated());

        long moverId = mapper.readTree(mvc.perform(post("/api/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + adminToken)
                        .content("""
                                {"username":"mover","email":"mover@example.com","password":"password1",
                                 "displayName":"Xyz Mover","role":"USER"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asLong();

        mvc.perform(post("/api/projects/HIS/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + adminToken)
                        .content("{\"userId\":%d,\"projectRole\":\"MEMBER\"}".formatted(moverId)))
                .andExpect(status().isOk());

        moverToken = mapper.readTree(mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usernameOrEmail\":\"mover\",\"password\":\"password1\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("token").asText();
    }

    private String newTicket(String title) throws Exception {
        return mapper.readTree(mvc.perform(post("/api/projects/HIS/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + adminToken)
                        .content("{\"title\":\"%s\"}".formatted(title)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("ticketKey").asText();
    }

    private void move(String ticketKey, String status, String token) throws Exception {
        mvc.perform(patch("/api/tickets/" + ticketKey + "/status")
                        .param("status", status)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void logsWhoMovedTheTicketAndBetweenWhichBuckets() throws Exception {
        String ticket = newTicket("Tracks its own moves");

        move(ticket, "TODO", moverToken);

        mvc.perform(get("/api/tickets/" + ticket + "/history")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].fromStatus").value("BACKLOG"))
                .andExpect(jsonPath("$[0].toStatus").value("TODO"))
                .andExpect(jsonPath("$[0].movedBy.displayName").value("Xyz Mover"))
                .andExpect(jsonPath("$[0].movedAt").isNotEmpty())
                .andExpect(jsonPath("$[0].summary").value("moved from Backlog to To Do by Xyz Mover"));
    }

    @Test
    void keepsTheWholeTrailInOrderWithTheLastMoverLast() throws Exception {
        String ticket = newTicket("Moves several times");

        move(ticket, "TODO", adminToken);
        move(ticket, "IN_PROGRESS", moverToken);
        move(ticket, "IN_REVIEW", adminToken);
        move(ticket, "DONE", moverToken);

        mvc.perform(get("/api/tickets/" + ticket + "/history")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].summary").value("moved from Backlog to To Do by Olivia Owner"))
                .andExpect(jsonPath("$[1].summary").value("moved from To Do to In Progress by Xyz Mover"))
                .andExpect(jsonPath("$[2].summary").value("moved from In Progress to In Review by Olivia Owner"))
                // The last entry is the last person who moved it.
                .andExpect(jsonPath("$[3].summary").value("moved from In Review to Done by Xyz Mover"))
                .andExpect(jsonPath("$[3].toStatus").value("DONE"));
    }

    @Test
    void logsMovesMadeThroughAFullUpdateToo() throws Exception {
        String ticket = newTicket("Moved via PATCH body");

        mvc.perform(patch("/api/tickets/" + ticket)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + moverToken)
                        .content("{\"status\":\"IN_PROGRESS\",\"priority\":\"HIGH\"}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/tickets/" + ticket + "/history")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].summary").value("moved from Backlog to In Progress by Xyz Mover"));
    }

    @Test
    void doesNotLogANonMove() throws Exception {
        String ticket = newTicket("Stays put");

        // Same bucket it is already in, twice, plus an unrelated edit.
        move(ticket, "BACKLOG", moverToken);
        mvc.perform(patch("/api/tickets/" + ticket)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + moverToken)
                        .content("{\"status\":\"BACKLOG\",\"title\":\"Renamed but not moved\"}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/tickets/" + ticket + "/history")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void historyFollowsProjectVisibility() throws Exception {
        String ticket = newTicket("Private history");
        move(ticket, "TODO", adminToken);

        // Someone with an account but no membership of this project.
        mvc.perform(post("/api/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + adminToken)
                        .content("""
                                {"username":"outsider","email":"outsider@example.com","password":"password1",
                                 "displayName":"Out Sider","role":"USER"}
                                """))
                .andExpect(status().isCreated());

        String outsiderToken = mapper.readTree(mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usernameOrEmail\":\"outsider\",\"password\":\"password1\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("token").asText();

        mvc.perform(get("/api/tickets/" + ticket + "/history")
                        .header("Authorization", "Bearer " + outsiderToken))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/tickets/" + ticket + "/history"))
                .andExpect(status().isUnauthorized());
    }
}

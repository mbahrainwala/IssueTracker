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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties =
        "spring.datasource.url=jdbc:h2:mem:issuetracker-links;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
class TicketLinkApiTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    private static String adminToken;

    @BeforeEach
    void seed() throws Exception {
        if (adminToken != null) {
            return;
        }
        String body = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"lead","email":"lead@example.com",
                                 "password":"password1","displayName":"Lead"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        adminToken = mapper.readTree(body).get("token").asText();

        for (String key : new String[]{"WEB", "API"}) {
            mvc.perform(post("/api/projects")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("Authorization", "Bearer " + adminToken)
                            .content("{\"projectKey\":\"%s\",\"name\":\"%s\"}".formatted(key, key)))
                    .andExpect(status().isCreated());
        }
    }

    /**
     * Every test mints its own tickets. The suite shares one database, so reusing fixed keys
     * would let links from one test leak into another's assertions.
     */
    private String newTicket(String project, String title, String type) throws Exception {
        String body = mvc.perform(post("/api/projects/" + project + "/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + adminToken)
                        .content("{\"title\":\"%s\",\"type\":\"%s\"}".formatted(title, type)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).get("ticketKey").asText();
    }

    private String link(String from, String type, String to) throws Exception {
        return mvc.perform(post("/api/tickets/" + from + "/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + adminToken)
                        .content("{\"linkType\":\"%s\",\"targetTicketKey\":\"%s\"}".formatted(type, to)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void linksABugToAnEarlierTaskAndShowsTheInverseOnTheOtherTicket() throws Exception {
        String task = newTicket("WEB", "Original task", "TASK");
        String bug = newTicket("WEB", "Regression bug", "BUG");

        link(bug, "IS_CAUSED_BY", task);

        mvc.perform(get("/api/tickets/" + bug + "/links").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].linkType").value("IS_CAUSED_BY"))
                .andExpect(jsonPath("$[0].label").value("is caused by"))
                .andExpect(jsonPath("$[0].ticket.ticketKey").value(task))
                .andExpect(jsonPath("$[0].ticket.title").value("Original task"));

        // The same row, read from the other end, reads the opposite way round.
        mvc.perform(get("/api/tickets/" + task + "/links").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].linkType").value("CAUSES"))
                .andExpect(jsonPath("$[0].label").value("causes"))
                .andExpect(jsonPath("$[0].ticket.ticketKey").value(bug));
    }

    @Test
    void linksAcrossProjects() throws Exception {
        String web = newTicket("WEB", "Needs the API change", "TASK");
        String api = newTicket("API", "Upstream change", "TASK");

        link(web, "IS_BLOCKED_BY", api);

        mvc.perform(get("/api/tickets/" + api + "/links").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].linkType").value("BLOCKS"))
                .andExpect(jsonPath("$[0].ticket.ticketKey").value(web))
                .andExpect(jsonPath("$[0].ticket.projectKey").value("WEB"));
    }

    @Test
    void rejectsSelfLinksAndDuplicates() throws Exception {
        String a = newTicket("WEB", "A side", "TASK");
        String b = newTicket("API", "B side", "TASK");

        mvc.perform(post("/api/tickets/" + a + "/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + adminToken)
                        .content("{\"linkType\":\"RELATES_TO\",\"targetTicketKey\":\"%s\"}".formatted(a)))
                .andExpect(status().isConflict());

        link(a, "RELATES_TO", b);

        mvc.perform(post("/api/tickets/" + a + "/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + adminToken)
                        .content("{\"linkType\":\"RELATES_TO\",\"targetTicketKey\":\"%s\"}".formatted(b)))
                .andExpect(status().isConflict());

        // Recording the mirror image of an existing link is the same link.
        mvc.perform(post("/api/tickets/" + b + "/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + adminToken)
                        .content("{\"linkType\":\"RELATES_TO\",\"targetTicketKey\":\"%s\"}".formatted(a)))
                .andExpect(status().isConflict());
    }

    @Test
    void unlinks() throws Exception {
        String a = newTicket("WEB", "Keeps a link briefly", "TASK");
        String b = newTicket("API", "The other end", "TASK");

        String links = link(a, "DUPLICATES", b);
        long linkId = mapper.readTree(links).get(0).get("id").asLong();

        mvc.perform(delete("/api/links/" + linkId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/tickets/" + a + "/links").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void deletingATicketRemovesItsLinks() throws Exception {
        String keeper = newTicket("WEB", "Survives", "TASK");
        String doomed = newTicket("API", "Throwaway", "TASK");
        link(keeper, "RELATES_TO", doomed);

        mvc.perform(delete("/api/tickets/" + doomed).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/tickets/" + keeper + "/links").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void linkPickerAndLinkRowsRespectProjectVisibility() throws Exception {
        // An ordinary user assigned only to WEB.
        String created = mvc.perform(post("/api/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + adminToken)
                        .content("""
                                {"username":"weber","email":"weber@example.com","password":"password1",
                                 "displayName":"Web Er","role":"USER"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = mapper.readTree(created).get("id").asLong();

        mvc.perform(put("/api/admin/users/" + id + "/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + adminToken)
                        .content("{\"assignments\":[{\"projectKey\":\"WEB\",\"projectRole\":\"MEMBER\"}]}"))
                .andExpect(status().isOk());

        String body = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usernameOrEmail\":\"weber\",\"password\":\"password1\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String userToken = mapper.readTree(body).get("token").asText();

        // The picker offers WEB tickets but never API ones.
        mvc.perform(get("/api/tickets/search").param("q", "-").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.projectKey == 'API')]").isEmpty())
                .andExpect(jsonPath("$[?(@.projectKey == 'WEB')]").isNotEmpty());

        // A cross-project link exists, but its API end must stay hidden from this user.
        String web = newTicket("WEB", "Visible to weber", "TASK");
        String api = newTicket("API", "Hidden from weber", "TASK");
        link(web, "BLOCKS", api);

        mvc.perform(get("/api/tickets/" + web + "/links").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0))
                .andExpect(jsonPath("$[?(@.ticket.projectKey == 'API')]").isEmpty());

        // The admin, who can see both projects, still sees it.
        mvc.perform(get("/api/tickets/" + web + "/links").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].ticket.ticketKey").value(api));
    }
}

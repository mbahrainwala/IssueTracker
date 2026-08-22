package behrainwala.issuetracker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class IssueTrackerApiTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    private static String token;

    @Test
    @Order(1)
    void registersFirstUserAsAdmin() throws Exception {
        String body = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"tester","email":"tester@example.com",
                                 "password":"password1","displayName":"Tester"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.role").value("ADMIN"))
                .andReturn().getResponse().getContentAsString();

        token = mapper.readTree(body).get("token").asText();
        assertThat(token).isNotBlank();
    }

    @Test
    @Order(2)
    void rejectsUnauthenticatedRequests() throws Exception {
        mvc.perform(get("/api/projects")).andExpect(status().isUnauthorized());
    }

    @Test
    @Order(3)
    void ticketKeysArePrefixedPerProjectAndNumberedIndependently() throws Exception {
        createProject("PROJ1", "Platform");
        createProject("PROJ2", "Web App");

        assertThat(createTicket("PROJ1", "First platform issue")).isEqualTo("PROJ1-1");
        assertThat(createTicket("PROJ1", "Second platform issue")).isEqualTo("PROJ1-2");
        // A second project restarts its own numbering rather than sharing a global sequence.
        assertThat(createTicket("PROJ2", "First web issue")).isEqualTo("PROJ2-1");
        assertThat(createTicket("proj2", "Second web issue")).isEqualTo("PROJ2-2");
    }

    @Test
    @Order(4)
    void transitionsAndCommentsOnATicket() throws Exception {
        mvc.perform(patch("/api/tickets/PROJ1-1/status")
                        .param("status", "In Progress")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("In Progress"));

        mvc.perform(post("/api/tickets/PROJ1-1/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"body\":\"Picking this up.\"}"))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/tickets/PROJ1-1/comments")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @Order(5)
    void rejectsDuplicateProjectKeys() throws Exception {
        mvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"projectKey\":\"PROJ1\",\"name\":\"Clash\"}"))
                .andExpect(status().isConflict());
    }

    private void createProject(String key, String name) throws Exception {
        mvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"projectKey\":\"%s\",\"name\":\"%s\"}".formatted(key, name)))
                .andExpect(status().isCreated());
    }

    private String createTicket(String projectKey, String title) throws Exception {
        String body = mvc.perform(post("/api/projects/" + projectKey + "/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"title\":\"%s\"}".formatted(title)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode node = mapper.readTree(body);
        return node.get("ticketKey").asText();
    }
}

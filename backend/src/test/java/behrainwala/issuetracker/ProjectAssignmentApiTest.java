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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers admin-driven project assignment and the visibility rule that follows from it:
 * a user sees only the projects they are assigned to.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties =
        "spring.datasource.url=jdbc:h2:mem:issuetracker-assign;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
class ProjectAssignmentApiTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    private static String adminToken;
    private static long zoeId;
    private static String zoeToken;

    @BeforeEach
    void setUp() throws Exception {
        if (adminToken != null) {
            return;
        }
        adminToken = register("boss", "boss@example.com", "Boss Admin");

        // Three projects, all led by the admin, so Zoe has no implicit access to any of them.
        for (String key : new String[]{"ALPHA", "BETA", "GAMMA"}) {
            mvc.perform(post("/api/projects")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("Authorization", "Bearer " + adminToken)
                            .content("{\"projectKey\":\"%s\",\"name\":\"%s\"}".formatted(key, key)))
                    .andExpect(status().isCreated());
        }

        String created = mvc.perform(post("/api/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + adminToken)
                        .content("""
                                {"username":"zoe","email":"zoe@example.com","password":"password1",
                                 "displayName":"Zoe Washburne","role":"USER"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        zoeId = mapper.readTree(created).get("id").asLong();
        zoeToken = login("zoe", "password1");
    }

    private String register(String username, String email, String displayName) throws Exception {
        String body = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","email":"%s","password":"password1","displayName":"%s"}
                                """.formatted(username, email, displayName)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).get("token").asText();
    }

    private String login(String username, String password) throws Exception {
        String body = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usernameOrEmail\":\"%s\",\"password\":\"%s\"}".formatted(username, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).get("token").asText();
    }

    private void assign(String json) throws Exception {
        mvc.perform(put("/api/admin/users/" + zoeId + "/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + adminToken)
                        .content(json))
                .andExpect(status().isOk());
    }

    @Test
    void unassignedUserSeesNoProjects() throws Exception {
        assign("{\"assignments\":[]}");

        mvc.perform(get("/api/projects").header("Authorization", "Bearer " + zoeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        // ...and cannot reach one directly by guessing its key.
        mvc.perform(get("/api/projects/ALPHA").header("Authorization", "Bearer " + zoeToken))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/projects/ALPHA/tickets").header("Authorization", "Bearer " + zoeToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminAssignsMultipleProjectsAndVisibilityFollows() throws Exception {
        assign("""
                {"assignments":[
                  {"projectKey":"ALPHA","projectRole":"MEMBER"},
                  {"projectKey":"GAMMA","projectRole":"VIEWER"}
                ]}""");

        mvc.perform(get("/api/projects").header("Authorization", "Bearer " + zoeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].projectKey").value("ALPHA"))
                .andExpect(jsonPath("$[1].projectKey").value("GAMMA"));

        mvc.perform(get("/api/projects/ALPHA").header("Authorization", "Bearer " + zoeToken))
                .andExpect(status().isOk());
        // BETA was never assigned.
        mvc.perform(get("/api/projects/BETA").header("Authorization", "Bearer " + zoeToken))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/admin/users/" + zoeId + "/projects")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].projectRole").value("MEMBER"))
                .andExpect(jsonPath("$[1].projectRole").value("VIEWER"));
    }

    @Test
    void reassigningReplacesTheWholeSet() throws Exception {
        assign("""
                {"assignments":[
                  {"projectKey":"ALPHA","projectRole":"MEMBER"},
                  {"projectKey":"BETA","projectRole":"MEMBER"}
                ]}""");
        assign("""
                {"assignments":[{"projectKey":"BETA","projectRole":"VIEWER"}]}""");

        mvc.perform(get("/api/projects").header("Authorization", "Bearer " + zoeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].projectKey").value("BETA"));

        // The role change took effect too: VIEWER cannot create tickets.
        mvc.perform(post("/api/projects/BETA/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + zoeToken)
                        .content("{\"title\":\"Should not be allowed\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void memberRoleCanCreateTickets() throws Exception {
        assign("""
                {"assignments":[{"projectKey":"ALPHA","projectRole":"MEMBER"}]}""");

        mvc.perform(post("/api/projects/ALPHA/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + zoeToken)
                        .content("{\"title\":\"Filed by an assigned member\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ticketKey").value("ALPHA-1"));
    }

    @Test
    void usersCanSeeTheirOwnAssignmentsInTheirProfile() throws Exception {
        assign("""
                {"assignments":[
                  {"projectKey":"ALPHA","projectRole":"MEMBER"},
                  {"projectKey":"BETA","projectRole":"VIEWER"}
                ]}""");

        // No admin rights needed for your own profile.
        mvc.perform(get("/api/auth/me/projects").header("Authorization", "Bearer " + zoeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.projectKey == 'ALPHA')].projectRole").value("MEMBER"))
                .andExpect(jsonPath("$[?(@.projectKey == 'BETA')].projectRole").value("VIEWER"))
                .andExpect(jsonPath("$[?(@.projectKey == 'GAMMA')]").isEmpty());

        mvc.perform(get("/api/auth/me/projects")).andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsUnknownProjectAndNonAdminCallers() throws Exception {
        mvc.perform(put("/api/admin/users/" + zoeId + "/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + adminToken)
                        .content("{\"assignments\":[{\"projectKey\":\"NOPE\",\"projectRole\":\"MEMBER\"}]}"))
                .andExpect(status().isNotFound());

        mvc.perform(put("/api/admin/users/" + zoeId + "/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + zoeToken)
                        .content("{\"assignments\":[]}"))
                .andExpect(status().isForbidden());
    }
}

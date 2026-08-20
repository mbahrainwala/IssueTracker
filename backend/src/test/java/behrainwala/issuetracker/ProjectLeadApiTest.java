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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A project may have several leads, a user may lead several projects, and any lead can staff
 * their own project without needing a system administrator.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties =
        "spring.datasource.url=jdbc:h2:mem:issuetracker-leads;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
class ProjectLeadApiTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    private static String adminToken;
    private static long annaId;
    private static long benId;
    private static long chrisId;
    private static String annaToken;
    private static String benToken;

    @BeforeEach
    void setUp() throws Exception {
        if (adminToken != null) {
            return;
        }
        adminToken = registerFirstAdmin();
        annaId = createUser("anna");
        benId = createUser("ben");
        chrisId = createUser("chris");
        annaToken = login("anna");
        benToken = login("ben");
    }

    private String registerFirstAdmin() throws Exception {
        String body = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"sysadmin","email":"sysadmin@example.com",
                                 "password":"password1","displayName":"Sys Admin"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).get("token").asText();
    }

    private long createUser(String username) throws Exception {
        String body = mvc.perform(post("/api/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + adminToken)
                        .content("""
                                {"username":"%s","email":"%s@example.com","password":"password1",
                                 "displayName":"%s","role":"USER"}
                                """.formatted(username, username, username)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).get("id").asLong();
    }

    private String login(String username) throws Exception {
        String body = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usernameOrEmail\":\"%s\",\"password\":\"password1\"}".formatted(username)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).get("token").asText();
    }

    private void createProject(String key, String token) throws Exception {
        mvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"projectKey\":\"%s\",\"name\":\"%s\"}".formatted(key, key)))
                .andExpect(status().isCreated());
    }

    private void addMember(String key, long userId, String role, String token, int expectedStatus) throws Exception {
        mvc.perform(post("/api/projects/" + key + "/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"userId\":%d,\"projectRole\":\"%s\"}".formatted(userId, role)))
                .andExpect(status().is(expectedStatus));
    }

    @Test
    void aProjectCanHaveSeveralLeads() throws Exception {
        createProject("MULTI", adminToken);
        addMember("MULTI", annaId, "LEAD", adminToken, 200);

        mvc.perform(get("/api/projects/MULTI").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.leads.length()").value(2))
                .andExpect(jsonPath("$.leads[?(@.username == 'anna')]").isNotEmpty())
                .andExpect(jsonPath("$.leads[?(@.username == 'sysadmin')]").isNotEmpty());
    }

    @Test
    void aLeadCanAddOtherUsersToTheirOwnProject() throws Exception {
        createProject("STAFF", adminToken);
        addMember("STAFF", annaId, "LEAD", adminToken, 200);

        // Anna is an ordinary USER globally, but a lead here - so she may staff the project.
        addMember("STAFF", benId, "MEMBER", annaToken, 200);

        mvc.perform(get("/api/projects/STAFF/members").header("Authorization", "Bearer " + annaToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.user.username == 'ben')].projectRole").value("MEMBER"));

        // A plain member may not.
        addMember("STAFF", chrisId, "MEMBER", benToken, 403);

        // Anna can promote Ben to co-lead, and then he can add people too.
        addMember("STAFF", benId, "LEAD", annaToken, 200);
        addMember("STAFF", chrisId, "VIEWER", benToken, 200);
    }

    @Test
    void aLeadCannotTouchProjectsTheyDoNotLead() throws Exception {
        createProject("OTHER", adminToken);
        createProject("ANNAS", annaToken);

        // Anna leads ANNAS but has no access at all to OTHER.
        addMember("OTHER", benId, "MEMBER", annaToken, 403);
        addMember("ANNAS", benId, "MEMBER", annaToken, 200);
    }

    @Test
    void aUserCanLeadSeveralProjects() throws Exception {
        createProject("LEADA", annaToken);
        createProject("LEADB", annaToken);
        createProject("LEADC", adminToken);
        addMember("LEADC", annaId, "LEAD", adminToken, 200);

        mvc.perform(get("/api/admin/users/" + annaId + "/projects")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.projectKey == 'LEADA')].projectRole").value("LEAD"))
                .andExpect(jsonPath("$[?(@.projectKey == 'LEADB')].projectRole").value("LEAD"))
                .andExpect(jsonPath("$[?(@.projectKey == 'LEADC')].projectRole").value("LEAD"));
    }

    @Test
    void refusesToLeaveAProjectWithoutALead() throws Exception {
        createProject("LONELY", adminToken);
        long adminId = mapper.readTree(mvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + adminToken))
                .andReturn().getResponse().getContentAsString()).get("id").asLong();

        // Sole lead: cannot be removed...
        mvc.perform(delete("/api/projects/LONELY/members/" + adminId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict());
        // ...nor demoted.
        addMember("LONELY", adminId, "MEMBER", adminToken, 409);

        // With a second lead in place, either may step down.
        addMember("LONELY", annaId, "LEAD", adminToken, 200);
        mvc.perform(delete("/api/projects/LONELY/members/" + adminId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/projects/LONELY").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.leads.length()").value(1))
                .andExpect(jsonPath("$.leads[0].username").value("anna"));
    }

    @Test
    void adminAssignmentCannotStripAProjectsOnlyLead() throws Exception {
        createProject("SOLO", annaToken);

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/admin/users/" + annaId + "/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + adminToken)
                        .content("{\"assignments\":[]}"))
                .andExpect(status().isConflict());
    }
}

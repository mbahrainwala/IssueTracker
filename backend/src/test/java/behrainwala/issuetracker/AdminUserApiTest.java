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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Runs against its own in-memory database: this suite relies on "the first registered
 * account becomes ADMIN", which only holds if no other suite has populated the schema.
 * The distinct datasource URL also gives the class its own cached Spring context.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties =
        "spring.datasource.url=jdbc:h2:mem:issuetracker-admin;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
class AdminUserApiTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    /** Static so the bootstrap registration happens once for the whole class. */
    private static String adminToken;

    @BeforeEach
    void bootstrapAdmin() throws Exception {
        if (adminToken == null) {
            adminToken = register("root", "root@example.com", "password1", "Root Admin");
        }
    }

    private String register(String username, String email, String password, String displayName) throws Exception {
        String body = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","email":"%s","password":"%s","displayName":"%s"}
                                """.formatted(username, email, password, displayName)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).get("token").asText();
    }

    private long createUser(String username, String email, String role) throws Exception {
        String body = mvc.perform(post("/api/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + adminToken)
                        .content("""
                                {"username":"%s","email":"%s","password":"password1",
                                 "displayName":"%s","role":"%s"}
                                """.formatted(username, email, username, role)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.enabled").value(true))
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).get("id").asLong();
    }

    @Test
    void nonAdminsCannotReachTheDashboard() throws Exception {
        long id = createUser("plain", "plain@example.com", "USER");
        assertThat(id).isPositive();

        String userToken = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usernameOrEmail\":\"plain\",\"password\":\"password1\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = mapper.readTree(userToken).get("token").asText();

        mvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createsEditsAndDisablesUsers() throws Exception {
        long id = createUser("carol", "carol@example.com", "USER");

        mvc.perform(put("/api/admin/users/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + adminToken)
                        .content("""
                                {"email":"carol.new@example.com","displayName":"Carol Danvers",
                                 "role":"ADMIN","enabled":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("carol.new@example.com"))
                .andExpect(jsonPath("$.displayName").value("Carol Danvers"))
                .andExpect(jsonPath("$.role").value("ADMIN"));

        mvc.perform(patch("/api/admin/users/" + id + "/enabled")
                        .param("enabled", "false")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));
    }

    @Test
    void disabledUserCannotLogInOrReuseAnExistingToken() throws Exception {
        long id = createUser("dave", "dave@example.com", "USER");

        String body = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usernameOrEmail\":\"dave\",\"password\":\"password1\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String daveToken = mapper.readTree(body).get("token").asText();

        mvc.perform(get("/api/projects").header("Authorization", "Bearer " + daveToken))
                .andExpect(status().isOk());

        mvc.perform(patch("/api/admin/users/" + id + "/enabled")
                        .param("enabled", "false")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        // The token is still cryptographically valid, so this only passes if the
        // filter re-checks the account on every request.
        mvc.perform(get("/api/projects").header("Authorization", "Bearer " + daveToken))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usernameOrEmail\":\"dave\",\"password\":\"password1\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refusesToLockTheAdministratorOut() throws Exception {
        String me = mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long myId = mapper.readTree(me).get("id").asLong();

        mvc.perform(patch("/api/admin/users/" + myId + "/enabled")
                        .param("enabled", "false")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict());

        mvc.perform(put("/api/admin/users/" + myId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + adminToken)
                        .content("""
                                {"email":"root@example.com","displayName":"Root Admin",
                                 "role":"USER","enabled":true}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsDuplicateUsernameAndEmail() throws Exception {
        createUser("erin", "erin@example.com", "USER");

        mvc.perform(post("/api/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + adminToken)
                        .content("""
                                {"username":"erin","email":"other@example.com","password":"password1",
                                 "displayName":"Erin Two","role":"USER"}
                                """))
                .andExpect(status().isConflict());

        mvc.perform(post("/api/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + adminToken)
                        .content("""
                                {"username":"erin2","email":"erin@example.com","password":"password1",
                                 "displayName":"Erin Two","role":"USER"}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void resetsAPassword() throws Exception {
        long id = createUser("frank", "frank@example.com", "USER");

        mvc.perform(put("/api/admin/users/" + id + "/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + adminToken)
                        .content("{\"password\":\"brand-new-pass\"}"))
                .andExpect(status().isNoContent());

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usernameOrEmail\":\"frank\",\"password\":\"password1\"}"))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usernameOrEmail\":\"frank\",\"password\":\"brand-new-pass\"}"))
                .andExpect(status().isOk());
    }
}

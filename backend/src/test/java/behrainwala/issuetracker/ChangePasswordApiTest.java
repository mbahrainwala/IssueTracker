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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Every signed-in user - not just admins - can change their own password. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties =
        "spring.datasource.url=jdbc:h2:mem:issuetracker-pwd;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
class ChangePasswordApiTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    private static boolean seeded;

    @BeforeEach
    void seed() throws Exception {
        if (seeded) {
            return;
        }
        // First registration is the admin; the second is an ordinary user.
        register("owner", "owner@example.com");
        register("member", "member@example.com");
        seeded = true;
    }

    private void register(String username, String email) throws Exception {
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","email":"%s","password":"password1","displayName":"%s"}
                                """.formatted(username, email, username)))
                .andExpect(status().isCreated());
    }

    private String login(String username, String password) throws Exception {
        String body = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usernameOrEmail\":\"%s\",\"password\":\"%s\"}".formatted(username, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).get("token").asText();
    }

    private String change(String token, String current, String next) throws Exception {
        return mvc.perform(post("/api/auth/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"currentPassword\":\"%s\",\"newPassword\":\"%s\"}".formatted(current, next)))
                .andReturn().getResponse().getStatus() + "";
    }

    @Test
    void ordinaryUserChangesOwnPassword() throws Exception {
        String token = login("member", "password1");

        mvc.perform(post("/api/auth/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"currentPassword\":\"password1\",\"newPassword\":\"a-better-secret\"}"))
                .andExpect(status().isNoContent());

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usernameOrEmail\":\"member\",\"password\":\"password1\"}"))
                .andExpect(status().isUnauthorized());

        login("member", "a-better-secret");
    }

    @Test
    void rejectsAWrongCurrentPassword() throws Exception {
        String token = login("owner", "password1");

        mvc.perform(post("/api/auth/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"currentPassword\":\"not-my-password\",\"newPassword\":\"whatever-123\"}"))
                .andExpect(status().isBadRequest());

        // Unchanged.
        login("owner", "password1");
    }

    @Test
    void rejectsShortAndUnchangedPasswords() throws Exception {
        String token = login("owner", "password1");

        mvc.perform(post("/api/auth/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"currentPassword\":\"password1\",\"newPassword\":\"short\"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/auth/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"currentPassword\":\"password1\",\"newPassword\":\"password1\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(post("/api/auth/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"password1\",\"newPassword\":\"whatever-123\"}"))
                .andExpect(status().isUnauthorized());

        // The tightened matcher means /api/auth/me is no longer anonymous either.
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }
}

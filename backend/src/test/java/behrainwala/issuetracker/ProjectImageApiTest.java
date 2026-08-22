package behrainwala.issuetracker;

import behrainwala.issuetracker.service.ProjectImageStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The optional project picture: who may set it, who may see it, and what may be uploaded.
 * Unlike the company logo this is project data, so reading it needs membership.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:issuetracker-projimg;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "app.projects.image-directory=${java.io.tmpdir}/issuetracker-projimg-test",
})
class ProjectImageApiTest {

    private static final byte[] PNG = {
            (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0x10, 0x20};

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private ProjectImageStore imageStore;

    /** First registered account is the global ADMIN and leads PIC. */
    private static String leadToken;
    private static String memberToken;
    private static String outsiderToken;
    private static long projectId;

    @BeforeEach
    void setUp() throws Exception {
        if (leadToken != null) {
            return;
        }
        leadToken = register("picowner", "Pia Owner");
        memberToken = register("picmember", "Mo Member");
        outsiderToken = register("picoutsider", "Ozzy Outsider");

        projectId = mapper.readTree(mvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + leadToken)
                        .content("{\"projectKey\":\"PIC\",\"name\":\"Pictures\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asLong();

        long memberId = userIdOf("picmember");
        mvc.perform(post("/api/projects/PIC/members")
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

    private long userIdOf(String username) throws Exception {
        var users = mapper.readTree(mvc.perform(get("/api/users")
                        .header("Authorization", "Bearer " + leadToken))
                .andReturn().getResponse().getContentAsString());
        for (var user : users) {
            if (username.equals(user.get("username").asText())) {
                return user.get("id").asLong();
            }
        }
        throw new IllegalStateException("no user " + username);
    }

    private ResultActions upload(String token, String filename, byte[] bytes) throws Exception {
        return mvc.perform(multipart("/api/projects/PIC/image")
                .file(new MockMultipartFile("file", filename, "image/png", bytes))
                .with(request -> {
                    request.setMethod("PUT");
                    return request;
                })
                .header("Authorization", "Bearer " + token));
    }

    @Test
    void aLeadSetsTheImageAndItAppearsOnTheProject() throws Exception {
        upload(leadToken, "team.png", PNG)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasImage").value(true))
                .andExpect(jsonPath("$.imageVersion").isNumber());

        // It rides along on the ordinary project payloads, so tiles need no extra call.
        mvc.perform(get("/api/projects/PIC").header("Authorization", "Bearer " + leadToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasImage").value(true));
        mvc.perform(get("/api/projects").header("Authorization", "Bearer " + leadToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.projectKey == 'PIC')].hasImage").value(true));

        mvc.perform(get("/api/projects/PIC/image").header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(content().bytes(PNG))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Security-Policy", "default-src 'none'; sandbox"));

        // Named after the project id, so there is exactly one file and no key to track.
        assertThat(imageStore.exists(projectId)).isTrue();
    }

    @Test
    void theImageIsProjectDataAndNotPublic() throws Exception {
        upload(leadToken, "team.png", PNG).andExpect(status().isOk());

        mvc.perform(get("/api/projects/PIC/image").header("Authorization", "Bearer " + outsiderToken))
                .andExpect(status().isForbidden());
        // Unlike the company logo, this one needs a token at all.
        mvc.perform(get("/api/projects/PIC/image")).andExpect(status().isUnauthorized());
    }

    @Test
    void onlyALeadOrAdminMaySetIt() throws Exception {
        upload(memberToken, "member.png", PNG).andExpect(status().isForbidden());
        upload(outsiderToken, "outsider.png", PNG).andExpect(status().isForbidden());
        mvc.perform(delete("/api/projects/PIC/image").header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void onlyImagesAreAccepted() throws Exception {
        upload(leadToken, "notes.pdf", "%PDF-1.4".getBytes(StandardCharsets.UTF_8))
                .andExpect(status().isBadRequest());
        // An executable wearing an image name is caught by its bytes.
        upload(leadToken, "team.png", new byte[]{'M', 'Z', (byte) 0x90, 0x00})
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(
                        "That file is a Windows executable - executables cannot be attached"));
    }

    @Test
    void replacingOverwritesAndRemovingClearsTheFile() throws Exception {
        byte[] second = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0x55, 0x66, 0x77};

        upload(leadToken, "first.png", PNG).andExpect(status().isOk());
        upload(leadToken, "second.png", second).andExpect(status().isOk());
        mvc.perform(get("/api/projects/PIC/image").header("Authorization", "Bearer " + leadToken))
                .andExpect(content().bytes(second));

        mvc.perform(delete("/api/projects/PIC/image").header("Authorization", "Bearer " + leadToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasImage").value(false));

        assertThat(imageStore.exists(projectId)).isFalse();
        mvc.perform(get("/api/projects/PIC/image").header("Authorization", "Bearer " + leadToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void deletingTheProjectTakesItsImageWithIt() throws Exception {
        long id = mapper.readTree(mvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + leadToken)
                        .content("{\"projectKey\":\"PICGONE\",\"name\":\"Doomed\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asLong();

        mvc.perform(multipart("/api/projects/PICGONE/image")
                        .file(new MockMultipartFile("file", "doomed.png", "image/png", PNG))
                        .with(request -> {
                            request.setMethod("PUT");
                            return request;
                        })
                        .header("Authorization", "Bearer " + leadToken))
                .andExpect(status().isOk());
        assertThat(imageStore.exists(id)).isTrue();

        mvc.perform(delete("/api/projects/PICGONE").header("Authorization", "Bearer " + leadToken))
                .andExpect(status().isNoContent());

        assertThat(imageStore.exists(id)).isFalse();
    }

    @Test
    void anArchivedProjectRefusesImageChanges() throws Exception {
        mvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + leadToken)
                        .content("{\"projectKey\":\"PICFROZE\",\"name\":\"Frozen\"}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/projects/PICFROZE/archive")
                .header("Authorization", "Bearer " + leadToken)).andExpect(status().isOk());

        mvc.perform(multipart("/api/projects/PICFROZE/image")
                        .file(new MockMultipartFile("file", "late.png", "image/png", PNG))
                        .with(request -> {
                            request.setMethod("PUT");
                            return request;
                        })
                        .header("Authorization", "Bearer " + leadToken))
                .andExpect(status().isConflict());
    }
}

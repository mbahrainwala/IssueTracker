package behrainwala.issuetracker;

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
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Branding: readable by anyone because the sign-in screen wears it, writable only by a global
 * administrator, and an image only.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:issuetracker-branding;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "app.branding.directory=${java.io.tmpdir}/issuetracker-branding-test",
})
class BrandingApiTest {

    private static final byte[] PNG = {
            (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x01};

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @org.springframework.beans.factory.annotation.Value("${app.branding.directory}")
    private String brandingDirectory;

    /** The first account registered is the global ADMIN; the second is an ordinary user. */
    private static String adminToken;
    private static String userToken;

    @BeforeEach
    void setUp() throws Exception {
        if (adminToken != null) {
            return;
        }
        adminToken = register("brandadmin", "Bea Admin");
        userToken = register("branduser", "Uri User");
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

    private ResultActions uploadLogo(String token, String filename, byte[] bytes) throws Exception {
        return mvc.perform(multipart("/api/branding/logo")
                .file(new MockMultipartFile("file", filename, "image/png", bytes))
                .with(request -> {
                    request.setMethod("PUT");
                    return request;
                })
                .header("Authorization", "Bearer " + token));
    }

    @Test
    void anAdministratorSetsTheNameAndEveryoneSeesIt() throws Exception {
        mvc.perform(put("/api/branding")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + adminToken)
                        .content("{\"companyName\":\"Northwind Ltd\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyName").value("Northwind Ltd"));

        // No token at all: the sign-in screen has to be able to read this.
        mvc.perform(get("/api/branding"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyName").value("Northwind Ltd"));

        // Blank puts the default title back.
        mvc.perform(put("/api/branding")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + adminToken)
                        .content("{\"companyName\":\"   \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyName").doesNotExist());
    }

    @Test
    void anOrdinaryUserCannotChangeBranding() throws Exception {
        mvc.perform(put("/api/branding")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + userToken)
                        .content("{\"companyName\":\"Not Mine To Set\"}"))
                .andExpect(status().isForbidden());

        uploadLogo(userToken, "logo.png", PNG).andExpect(status().isForbidden());

        mvc.perform(delete("/api/branding/logo").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());

        // ...and an anonymous caller certainly cannot.
        mvc.perform(put("/api/branding")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyName\":\"Anonymous Co\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void theLogoRoundTripsAndIsServedInertly() throws Exception {
        uploadLogo(adminToken, "logo.png", PNG)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasLogo").value(true))
                .andExpect(jsonPath("$.logoVersion").isNumber());

        // Public, so an <img> tag needs no token.
        mvc.perform(get("/api/branding/logo"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(content().bytes(PNG))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Security-Policy", "default-src 'none'; sandbox"));

        mvc.perform(delete("/api/branding/logo").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasLogo").value(false));

        mvc.perform(get("/api/branding/logo")).andExpect(status().isNotFound());
    }

    @Test
    void onlyImagesMayBeUsedAsALogo() throws Exception {
        uploadLogo(adminToken, "brochure.pdf", "%PDF-1.4".getBytes(StandardCharsets.UTF_8))
                .andExpect(status().isBadRequest());
        uploadLogo(adminToken, "logo.exe", "MZ".getBytes(StandardCharsets.UTF_8))
                .andExpect(status().isBadRequest());

        // An executable wearing an image name is caught by its bytes, not its extension.
        uploadLogo(adminToken, "logo.png", new byte[]{'M', 'Z', (byte) 0x90, 0x00})
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(
                        "That file is a Windows executable - executables cannot be attached"));
    }

    @Test
    void brandingIsEmptyUntilSomeoneSetsIt() throws Exception {
        // The migration seeds the row, so this answers rather than 404s on a fresh install.
        mvc.perform(get("/api/branding")).andExpect(status().isOk());
    }

    @Test
    void replacingTheLogoOverwritesTheStoredFile() throws Exception {
        byte[] first = concat(PNG, new byte[]{1, 1, 1});
        byte[] second = concat(PNG, new byte[]{2, 2, 2, 2, 2});

        uploadLogo(adminToken, "first.png", first).andExpect(status().isOk());
        mvc.perform(get("/api/branding/logo"))
                .andExpect(status().isOk())
                .andExpect(content().bytes(first));

        uploadLogo(adminToken, "second.png", second).andExpect(status().isOk());
        mvc.perform(get("/api/branding/logo"))
                .andExpect(status().isOk())
                .andExpect(content().bytes(second));

        // One logo means one file, so the replacement leaves nothing behind.
        assertThat(Path.of(brandingDirectory)).isDirectoryContaining("regex:.*logo$");
        try (var files = Files.list(Path.of(brandingDirectory))) {
            assertThat(files.toList()).hasSize(1);
        }
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }
}

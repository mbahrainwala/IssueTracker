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

/**
 * @-mentions: naming somebody flags the ticket for them until they open it and say they have
 * seen it, and saying so leaves a comment everybody can read.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties =
        "spring.datasource.url=jdbc:h2:mem:issuetracker-mentions;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
class MentionApiTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    /** Owns the project and does the mentioning. */
    private static String leadToken;
    /** A member of the project, and the one being mentioned. */
    private static String mateToken;
    /** Not a member of the project at all. */
    private static String outsiderToken;

    @BeforeEach
    void setUp() throws Exception {
        if (leadToken != null) {
            return;
        }
        leadToken = register("mentionlead", "Lia Lead");
        mateToken = register("mentionmate", "Moe Mate");
        outsiderToken = register("mentionout", "Ozzy Out");

        mvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + leadToken)
                        .content("{\"projectKey\":\"MEN\",\"name\":\"Mentions\"}"))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/projects/MEN/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + leadToken)
                        .content("{\"userId\":%d,\"projectRole\":\"MEMBER\"}".formatted(userIdOf("mentionmate"))))
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

    private String newTicket(String title, String description) throws Exception {
        String body = description == null
                ? "{\"title\":\"%s\"}".formatted(title)
                : "{\"title\":\"%s\",\"description\":\"%s\"}".formatted(title, description);
        return mapper.readTree(mvc.perform(post("/api/projects/MEN/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + leadToken)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("ticketKey").asText();
    }

    private void comment(String ticketKey, String token, String body) throws Exception {
        mvc.perform(post("/api/tickets/" + ticketKey + "/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"body\":\"%s\"}".formatted(body)))
                .andExpect(status().isCreated());
    }

    @Test
    void aMentionInACommentFlagsTheTicketUntilItIsAcknowledged() throws Exception {
        String ticket = newTicket("Needs a second opinion", null);
        comment(ticket, leadToken, "@mentionmate can you look at this before Friday?");

        // It is waiting for the person named...
        mvc.perform(get("/api/mentions").header("Authorization", "Bearer " + mateToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.ticketKey == '" + ticket + "')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.ticketKey == '" + ticket + "')].mentionedBy.displayName")
                        .value("Lia Lead"))
                .andExpect(jsonPath("$[?(@.ticketKey == '" + ticket + "')].excerpt")
                        .value("@mentionmate can you look at this before Friday?"));

        // ...and for nobody else, including the person who wrote it.
        mvc.perform(get("/api/mentions").header("Authorization", "Bearer " + leadToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.ticketKey == '" + ticket + "')]").isEmpty());

        mvc.perform(post("/api/tickets/" + ticket + "/mentions/acknowledge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + mateToken)
                        .content("{\"body\":\"Seen it - will review tomorrow.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body").value("Seen it - will review tomorrow."))
                .andExpect(jsonPath("$.author.displayName").value("Moe Mate"));

        // The flag is gone, and the acknowledgement is a comment on the ticket.
        mvc.perform(get("/api/mentions").header("Authorization", "Bearer " + mateToken))
                .andExpect(jsonPath("$[?(@.ticketKey == '" + ticket + "')]").isEmpty());
        mvc.perform(get("/api/tickets/" + ticket + "/comments")
                        .header("Authorization", "Bearer " + leadToken))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[1].body").value("Seen it - will review tomorrow."));
    }

    @Test
    void aMentionInTheDescriptionCountsToo() throws Exception {
        String ticket = newTicket("Described at you", "Handing this to @mentionmate to finish.");

        mvc.perform(get("/api/mentions").header("Authorization", "Bearer " + mateToken))
                .andExpect(jsonPath("$[?(@.ticketKey == '" + ticket + "')].excerpt")
                        .value("Handing this to @mentionmate to finish."));
    }

    @Test
    void acknowledgingRequiresAComment() throws Exception {
        String ticket = newTicket("Wants a reply", null);
        comment(ticket, leadToken, "@mentionmate thoughts?");

        for (String body : new String[]{"{\"body\":\"\"}", "{\"body\":\"   \"}", "{}"}) {
            mvc.perform(post("/api/tickets/" + ticket + "/mentions/acknowledge")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("Authorization", "Bearer " + mateToken)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }

        // Still outstanding: nothing was dismissed by trying.
        mvc.perform(get("/api/mentions").header("Authorization", "Bearer " + mateToken))
                .andExpect(jsonPath("$[?(@.ticketKey == '" + ticket + "')]").isNotEmpty());
    }

    @Test
    void thereIsNothingToAcknowledgeWithoutAMention() throws Exception {
        String ticket = newTicket("Nobody was named", null);
        comment(ticket, leadToken, "Just a note to myself.");

        mvc.perform(post("/api/tickets/" + ticket + "/mentions/acknowledge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + mateToken)
                        .content("{\"body\":\"Acknowledging nothing\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void namesThatAreNotReachableMembersAreIgnored() throws Exception {
        String ticket = newTicket("Loose talk", null);
        // An outsider, a name that is not a user, and an email address that looks like one.
        comment(ticket, leadToken,
                "@mentionout @nobodyatall please mail bob@example.com about this");

        mvc.perform(get("/api/mentions").header("Authorization", "Bearer " + outsiderToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.ticketKey == '" + ticket + "')]").isEmpty());

        // The comment itself was still accepted - a bad name is not an error.
        mvc.perform(get("/api/tickets/" + ticket + "/comments")
                        .header("Authorization", "Bearer " + leadToken))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void severalMentionsOnOneTicketClearTogether() throws Exception {
        String ticket = newTicket("Asked twice", null);
        comment(ticket, leadToken, "@mentionmate first ask");
        comment(ticket, leadToken, "@mentionmate second ask");

        mvc.perform(get("/api/mentions").header("Authorization", "Bearer " + mateToken))
                .andExpect(jsonPath("$[?(@.ticketKey == '" + ticket + "')]")
                        .value(org.hamcrest.Matchers.hasSize(2)));

        // What is acknowledged is the ticket, not one comment on it.
        mvc.perform(post("/api/tickets/" + ticket + "/mentions/acknowledge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + mateToken)
                        .content("{\"body\":\"Caught up on both.\"}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/mentions").header("Authorization", "Bearer " + mateToken))
                .andExpect(jsonPath("$[?(@.ticketKey == '" + ticket + "')]").isEmpty());
    }

    @Test
    void anAcknowledgementCanMentionSomebodyInTurn() throws Exception {
        String ticket = newTicket("Passing it on", null);
        comment(ticket, leadToken, "@mentionmate over to you");

        mvc.perform(post("/api/tickets/" + ticket + "/mentions/acknowledge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + mateToken)
                        .content("{\"body\":\"Done - @mentionlead please check\"}"))
                .andExpect(status().isOk());

        // Mine is cleared; theirs is now raised.
        mvc.perform(get("/api/mentions").header("Authorization", "Bearer " + mateToken))
                .andExpect(jsonPath("$[?(@.ticketKey == '" + ticket + "')]").isEmpty());
        mvc.perform(get("/api/mentions").header("Authorization", "Bearer " + leadToken))
                .andExpect(jsonPath("$[?(@.ticketKey == '" + ticket + "')]").isNotEmpty());
    }

    @Test
    void anArchivedTicketCannotBeAcknowledged() throws Exception {
        String ticket = newTicket("Frozen mid-conversation", null);
        comment(ticket, leadToken, "@mentionmate look before we close this");

        mvc.perform(patch("/api/tickets/" + ticket + "/status").param("status", "Done")
                .header("Authorization", "Bearer " + leadToken)).andExpect(status().isOk());
        mvc.perform(post("/api/tickets/" + ticket + "/archive")
                .header("Authorization", "Bearer " + leadToken)).andExpect(status().isOk());

        // Acknowledging writes a comment, and an archived ticket takes none.
        mvc.perform(post("/api/tickets/" + ticket + "/mentions/acknowledge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + mateToken)
                        .content("{\"body\":\"Too late\"}"))
                .andExpect(status().isConflict());

        // It stays outstanding rather than being silently dropped.
        mvc.perform(get("/api/mentions").header("Authorization", "Bearer " + mateToken))
                .andExpect(jsonPath("$[?(@.ticketKey == '" + ticket + "')]").isNotEmpty());
    }
}

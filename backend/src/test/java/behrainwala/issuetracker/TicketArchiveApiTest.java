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

/** Archiving finished work, and the extra rule that guards epics. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties =
        "spring.datasource.url=jdbc:h2:mem:issuetracker-archive;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
class TicketArchiveApiTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    private static String token;

    @BeforeEach
    void setUp() throws Exception {
        if (token != null) {
            return;
        }
        token = mapper.readTree(mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"archivist","email":"arch@example.com",
                                 "password":"password1","displayName":"Ann Archivist"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("token").asText();

        mvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"projectKey\":\"ARC\",\"name\":\"Archive\"}"))
                .andExpect(status().isCreated());
    }

    private String create(String title, String type, String epicKey) throws Exception {
        String epicPart = epicKey == null ? "" : ",\"epicKey\":\"%s\"".formatted(epicKey);
        return mapper.readTree(mvc.perform(post("/api/projects/ARC/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"title\":\"%s\",\"type\":\"%s\"%s}".formatted(title, type, epicPart)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("ticketKey").asText();
    }

    private void moveTo(String ticketKey, String status) throws Exception {
        mvc.perform(patch("/api/tickets/" + ticketKey + "/status")
                        .param("status", status)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    private void archive(String ticketKey, int expected) throws Exception {
        mvc.perform(post("/api/tickets/" + ticketKey + "/archive")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().is(expected));
    }

    @Test
    void onlyDoneTicketsCanBeArchived() throws Exception {
        String ticket = create("Still in progress", "TASK", null);

        archive(ticket, 409);
        moveTo(ticket, "In Progress");
        archive(ticket, 409);

        moveTo(ticket, "Done");
        archive(ticket, 200);

        mvc.perform(get("/api/tickets/" + ticket).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.archived").value(true))
                .andExpect(jsonPath("$.archivedAt").isNotEmpty())
                .andExpect(jsonPath("$.archivedBy.displayName").value("Ann Archivist"));
    }

    @Test
    void archivedTicketsLeaveTheActiveListAndAppearInTheArchivedOne() throws Exception {
        String live = create("Stays visible", "TASK", null);
        String gone = create("Gets archived", "TASK", null);
        moveTo(gone, "Done");
        archive(gone, 200);

        mvc.perform(get("/api/projects/ARC/tickets").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.ticketKey == '" + live + "')]").isNotEmpty())
                .andExpect(jsonPath("$.content[?(@.ticketKey == '" + gone + "')]").isEmpty());

        mvc.perform(get("/api/projects/ARC/tickets").param("archived", "true")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.ticketKey == '" + gone + "')]").isNotEmpty())
                .andExpect(jsonPath("$.content[?(@.ticketKey == '" + live + "')]").isEmpty());
    }

    @Test
    void anEpicCanOnlyBeArchivedOnceAllItsTicketsAre() throws Exception {
        String epic = create("Big push", "EPIC", null);
        String a = create("Child one", "TASK", epic);
        String b = create("Child two", "STORY", epic);
        moveTo(epic, "Done");

        // Two live children: refused, and the message says how many.
        mvc.perform(post("/api/tickets/" + epic + "/archive").header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("still has 2 tickets")));

        moveTo(a, "Done");
        archive(a, 200);

        // One left: still refused.
        mvc.perform(post("/api/tickets/" + epic + "/archive").header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("still has 1 ticket ")));

        moveTo(b, "Done");
        archive(b, 200);

        // All children archived: now the epic may go.
        archive(epic, 200);
    }

    @Test
    void restoringPutsATicketBackAndIsBlockedUnderAnArchivedEpic() throws Exception {
        String epic = create("Closed epic", "EPIC", null);
        String child = create("Child of closed epic", "TASK", epic);
        moveTo(child, "Done");
        archive(child, 200);
        moveTo(epic, "Done");
        archive(epic, 200);

        // The epic is archived, so its child must not come back on its own.
        mvc.perform(post("/api/tickets/" + child + "/restore").header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("restore the epic first")));

        // Restore the epic, then the child.
        mvc.perform(post("/api/tickets/" + epic + "/restore").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archived").value(false));
        mvc.perform(post("/api/tickets/" + child + "/restore").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archived").value(false))
                .andExpect(jsonPath("$.archivedAt").doesNotExist());

        mvc.perform(get("/api/projects/ARC/tickets").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.content[?(@.ticketKey == '" + child + "')]").isNotEmpty());
    }

    @Test
    void archivedTicketsAreFrozenUntilRestored() throws Exception {
        String ticket = create("Frozen once archived", "TASK", null);
        moveTo(ticket, "Done");
        archive(ticket, 200);

        mvc.perform(patch("/api/tickets/" + ticket + "/status")
                        .param("status", "To Do")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());

        mvc.perform(patch("/api/tickets/" + ticket)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"title\":\"Renamed while archived\"}"))
                .andExpect(status().isConflict());

        // Archiving twice is a conflict, not a silent no-op.
        archive(ticket, 409);
    }

    @Test
    void archivedTicketsAreNotOfferedAsEpicCandidates() throws Exception {
        String epic = create("Candidate epic", "EPIC", null);
        String done = create("Archived candidate kkq", "TASK", null);
        String open = create("Open candidate kkq", "TASK", null);
        moveTo(done, "Done");
        archive(done, 200);

        mvc.perform(get("/api/tickets/" + epic + "/candidates").param("q", "kkq")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.ticketKey == '" + open + "')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.ticketKey == '" + done + "')]").isEmpty());
    }

    @Test
    void projectTicketCountExcludesTheArchive() throws Exception {
        String before = mvc.perform(get("/api/projects/ARC").header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString();
        long countBefore = mapper.readTree(before).get("ticketCount").asLong();

        String ticket = create("Counted then archived", "TASK", null);
        mvc.perform(get("/api/projects/ARC").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.ticketCount").value(countBefore + 1));

        moveTo(ticket, "Done");
        archive(ticket, 200);

        mvc.perform(get("/api/projects/ARC").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.ticketCount").value(countBefore));
    }
}

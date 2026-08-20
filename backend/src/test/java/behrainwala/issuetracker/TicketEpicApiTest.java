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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A ticket belongs to at most one epic; an epic gathers many tickets. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties =
        "spring.datasource.url=jdbc:h2:mem:issuetracker-epics;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
class TicketEpicApiTest {

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
        String body = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"pmuser","email":"pmuser@example.com",
                                 "password":"password1","displayName":"PM"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        token = mapper.readTree(body).get("token").asText();

        for (String key : new String[]{"EPX", "OTH"}) {
            mvc.perform(post("/api/projects")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("Authorization", "Bearer " + token)
                            .content("{\"projectKey\":\"%s\",\"name\":\"%s\"}".formatted(key, key)))
                    .andExpect(status().isCreated());
        }
    }

    private String create(String project, String title, String type, String epicKey) throws Exception {
        String epicPart = epicKey == null ? "" : ",\"epicKey\":\"%s\"".formatted(epicKey);
        String body = mvc.perform(post("/api/projects/" + project + "/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"title\":\"%s\",\"type\":\"%s\"%s}".formatted(title, type, epicPart)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).get("ticketKey").asText();
    }

    private void patchEpic(String ticketKey, String json, int expectedStatus) throws Exception {
        mvc.perform(patch("/api/tickets/" + ticketKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(json))
                .andExpect(status().is(expectedStatus));
    }

    @Test
    void gathersManyTicketsUnderOneEpic() throws Exception {
        String epic = create("EPX", "Checkout revamp", "EPIC", null);
        String a = create("EPX", "Design the basket", "STORY", epic);
        String b = create("EPX", "Payment provider spike", "TASK", epic);
        String loose = create("EPX", "Unrelated bug", "BUG", null);

        mvc.perform(get("/api/tickets/" + epic + "/children").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.ticketKey == '" + a + "')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.ticketKey == '" + b + "')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.ticketKey == '" + loose + "')]").isEmpty());

        // Each child points back at its epic.
        mvc.perform(get("/api/tickets/" + a).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.epic.ticketKey").value(epic))
                .andExpect(jsonPath("$.epic.title").value("Checkout revamp"));

        mvc.perform(get("/api/tickets/" + loose).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.epic").doesNotExist());
    }

    @Test
    void aTicketBelongsToAtMostOneEpicAndMovingItReplacesTheOld() throws Exception {
        String first = create("EPX", "First epic", "EPIC", null);
        String second = create("EPX", "Second epic", "EPIC", null);
        String ticket = create("EPX", "Moves between epics", "TASK", first);

        patchEpic(ticket, "{\"epicKey\":\"%s\"}".formatted(second), 200);

        mvc.perform(get("/api/tickets/" + ticket).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.epic.ticketKey").value(second));
        mvc.perform(get("/api/tickets/" + first + "/children").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$[?(@.ticketKey == '" + ticket + "')]").isEmpty());
        mvc.perform(get("/api/tickets/" + second + "/children").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$[?(@.ticketKey == '" + ticket + "')]").isNotEmpty());

        // And it can be detached entirely.
        patchEpic(ticket, "{\"clearEpic\":true}", 200);
        mvc.perform(get("/api/tickets/" + ticket).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.epic").doesNotExist());
    }

    @Test
    void attachesAnExistingTicketToAnEpic() throws Exception {
        String epic = create("EPX", "Adopt me", "EPIC", null);
        String orphan = create("EPX", "Existing work", "TASK", null);

        patchEpic(orphan, "{\"epicKey\":\"%s\"}".formatted(epic), 200);

        mvc.perform(get("/api/tickets/" + epic + "/children").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].ticketKey").value(orphan));
    }

    @Test
    void rejectsInvalidEpics() throws Exception {
        String epic = create("EPX", "Valid epic", "EPIC", null);
        String plain = create("EPX", "Not an epic", "TASK", null);
        String otherProjectEpic = create("OTH", "Epic elsewhere", "EPIC", null);
        String target = create("EPX", "Needs a parent", "TASK", null);

        // A non-epic cannot act as a parent.
        patchEpic(target, "{\"epicKey\":\"%s\"}".formatted(plain), 409);
        // Epics cannot nest.
        patchEpic(epic, "{\"epicKey\":\"%s\"}".formatted(otherProjectEpic), 409);
        // The epic must live in the same project.
        patchEpic(target, "{\"epicKey\":\"%s\"}".formatted(otherProjectEpic), 409);
        // Unknown key.
        patchEpic(target, "{\"epicKey\":\"EPX-9999\"}", 404);
        // Still unattached after all of that.
        mvc.perform(get("/api/tickets/" + target).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.epic").doesNotExist());
    }

    @Test
    void listsEpicsOfAProjectForThePicker() throws Exception {
        String epic = create("EPX", "Pickable epic", "EPIC", null);
        create("EPX", "Not pickable", "STORY", null);

        mvc.perform(get("/api/projects/EPX/epics").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.ticketKey == '" + epic + "')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.title == 'Not pickable')]").isEmpty());
    }

    @Test
    void deletingAnEpicReleasesItsTicketsInsteadOfDeletingThem() throws Exception {
        String epic = create("EPX", "Doomed epic", "EPIC", null);
        String child = create("EPX", "Outlives its epic", "TASK", epic);

        mvc.perform(delete("/api/tickets/" + epic).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/tickets/" + child).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.epic").doesNotExist());
    }

    @Test
    void addsExistingTicketsToTheEpicFromTheEpic() throws Exception {
        String epic = create("EPX", "Gathers from its own page", "EPIC", null);
        String a = create("EPX", "First to adopt", "TASK", null);
        String b = create("EPX", "Second to adopt", "STORY", null);

        mvc.perform(post("/api/tickets/" + epic + "/children")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"ticketKeys\":[\"%s\",\"%s\"]}".formatted(a, b)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.ticketKey == '" + a + "')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.ticketKey == '" + b + "')]").isNotEmpty());

        mvc.perform(get("/api/tickets/" + a).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.epic.ticketKey").value(epic));
    }

    @Test
    void removesATicketFromTheEpicWithoutDeletingIt() throws Exception {
        String epic = create("EPX", "Lets one go", "EPIC", null);
        String child = create("EPX", "Leaves the epic", "TASK", epic);

        mvc.perform(delete("/api/tickets/" + epic + "/children/" + child)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        // The ticket still exists, just unparented.
        mvc.perform(get("/api/tickets/" + child).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.epic").doesNotExist());

        // Removing something that is not in this epic is a conflict, not a silent no-op.
        mvc.perform(delete("/api/tickets/" + epic + "/children/" + child)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());
    }

    @Test
    void addingFromTheEpicRefusesTicketsFromAnotherProject() throws Exception {
        String epic = create("EPX", "Same project only", "EPIC", null);
        String foreign = create("OTH", "Belongs elsewhere", "TASK", null);
        String otherEpic = create("EPX", "Another epic", "EPIC", null);

        mvc.perform(post("/api/tickets/" + epic + "/children")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"ticketKeys\":[\"%s\"]}".formatted(foreign)))
                .andExpect(status().isConflict());

        // An epic is never a child either.
        mvc.perform(post("/api/tickets/" + epic + "/children")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"ticketKeys\":[\"%s\"]}".formatted(otherEpic)))
                .andExpect(status().isConflict());

        mvc.perform(get("/api/tickets/" + foreign).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.epic").doesNotExist());
    }

    @Test
    void candidatePickerOffersOnlySameProjectNonEpicTickets() throws Exception {
        String epic = create("EPX", "Picker epic", "EPIC", null);
        String free = create("EPX", "Free agent zzq", "TASK", null);
        String alreadyMine = create("EPX", "Already mine zzq", "TASK", epic);
        String foreign = create("OTH", "Foreign zzq", "TASK", null);
        String anotherEpic = create("EPX", "Another epic zzq", "EPIC", null);

        mvc.perform(get("/api/tickets/" + epic + "/candidates")
                        .param("q", "zzq")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.ticketKey == '" + free + "')]").isNotEmpty())
                // already in this epic, an epic itself, or in another project - all excluded
                .andExpect(jsonPath("$[?(@.ticketKey == '" + alreadyMine + "')]").isEmpty())
                .andExpect(jsonPath("$[?(@.ticketKey == '" + anotherEpic + "')]").isEmpty())
                .andExpect(jsonPath("$[?(@.ticketKey == '" + foreign + "')]").isEmpty())
                .andExpect(jsonPath("$[?(@.projectKey == 'OTH')]").isEmpty());
    }

    @Test
    void candidatesIncludeTicketsHeldByADifferentEpicSoTheyCanBeMoved() throws Exception {
        String from = create("EPX", "Origin epic", "EPIC", null);
        String to = create("EPX", "Destination epic", "EPIC", null);
        String movable = create("EPX", "Movable wqx", "TASK", from);

        mvc.perform(get("/api/tickets/" + to + "/candidates")
                        .param("q", "wqx")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.ticketKey == '" + movable + "')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.ticketKey == '" + movable + "')].epic.ticketKey").value(from));

        mvc.perform(post("/api/tickets/" + to + "/children")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"ticketKeys\":[\"%s\"]}".formatted(movable)))
                .andExpect(status().isOk());

        mvc.perform(get("/api/tickets/" + from + "/children").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void childrenIsOnlyMeaningfulForEpics() throws Exception {
        String plain = create("EPX", "Just a task", "TASK", null);

        mvc.perform(get("/api/tickets/" + plain + "/children").header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());
    }
}

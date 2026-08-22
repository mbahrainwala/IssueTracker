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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Templates and per-project lanes: a board is data now, so a lawyer's project and an
 * engineer's project can have nothing in common but the software they run on.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties =
        "spring.datasource.url=jdbc:h2:mem:issuetracker-templates;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
class ProjectTemplateApiTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    /** First account registered is the global ADMIN. */
    private static String adminToken;
    private static String userToken;

    @BeforeEach
    void setUp() throws Exception {
        if (adminToken != null) {
            return;
        }
        adminToken = register("tplowner", "Tia Owner");
        userToken = register("tpluser", "Uma User");
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

    private long templateIdOf(String name) throws Exception {
        var templates = mapper.readTree(mvc.perform(get("/api/templates")
                        .header("Authorization", "Bearer " + adminToken))
                .andReturn().getResponse().getContentAsString());
        for (var t : templates) {
            if (name.equals(t.get("name").asText())) {
                return t.get("id").asLong();
            }
        }
        throw new IllegalStateException("no template " + name);
    }

    private void createProject(String key, Long templateId) throws Exception {
        String body = templateId == null
                ? "{\"projectKey\":\"%s\",\"name\":\"%s\"}".formatted(key, key)
                : "{\"projectKey\":\"%s\",\"name\":\"%s\",\"templateId\":%d}".formatted(key, key, templateId);
        mvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + adminToken)
                        .content(body))
                .andExpect(status().isCreated());
    }

    @Test
    void theBuiltInTemplatesCoverUnlikeKindsOfWork() throws Exception {
        mvc.perform(get("/api/templates").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'Legal Case')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.name == 'Trip Planning')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.name == 'Software Development')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.name == 'Legal Case')].builtIn").value(true));
    }

    @Test
    void aProjectGetsTheLanesOfTheTemplateItWasMadeFrom() throws Exception {
        createProject("LAW", templateIdOf("Legal Case"));

        mvc.perform(get("/api/projects/LAW").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.templateName").value("Legal Case"))
                .andExpect(jsonPath("$.lanes.length()").value(5))
                .andExpect(jsonPath("$.lanes[0].name").value("Intake"))
                .andExpect(jsonPath("$.lanes[0].initial").value(true))
                .andExpect(jsonPath("$.lanes[4].name").value("Closed"))
                .andExpect(jsonPath("$.lanes[4].done").value(true));

        // A ticket lands in the starting lane of *this* board, not in some global default.
        mvc.perform(post("/api/projects/LAW/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + adminToken)
                        .content("{\"title\":\"Smith v Jones\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("Intake"));
    }

    @Test
    void aTicketCannotBeMovedToALaneItsBoardDoesNotHave() throws Exception {
        createProject("TRIP", templateIdOf("Trip Planning"));
        String ticket = mapper.readTree(mvc.perform(post("/api/projects/TRIP/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + adminToken)
                        .content("{\"title\":\"Book the flights\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("ticketKey").asText();

        mvc.perform(patch("/api/tickets/" + ticket + "/status").param("status", "Booked")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("Booked"));

        // "In Review" belongs to the engineering board, not this one.
        mvc.perform(patch("/api/tickets/" + ticket + "/status").param("status", "In Review")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(
                        org.hamcrest.Matchers.containsString("TRIP has no lane called \"In Review\"")));
    }

    @Test
    void archivingFollowsTheBoardsOwnFinishedLane() throws Exception {
        createProject("CASE2", templateIdOf("Legal Case"));
        String ticket = mapper.readTree(mvc.perform(post("/api/projects/CASE2/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + adminToken)
                        .content("{\"title\":\"Closing the matter\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("ticketKey").asText();

        // There is no "Done" here; "Closed" is what finished means on this board.
        mvc.perform(post("/api/tickets/" + ticket + "/archive")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(
                        org.hamcrest.Matchers.containsString("Only tickets in Closed can be archived")));

        mvc.perform(patch("/api/tickets/" + ticket + "/status").param("status", "Closed")
                .header("Authorization", "Bearer " + adminToken)).andExpect(status().isOk());
        mvc.perform(post("/api/tickets/" + ticket + "/archive")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    void aTemplateSeedsItsStarterTicketsIntoTheNamedLanes() throws Exception {
        createProject("HOLIDAY", templateIdOf("Trip Planning"));

        mvc.perform(get("/api/projects/HOLIDAY/tickets").param("size", "50")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(5))
                // The example that started all this: somewhere to keep the booking confirmations.
                .andExpect(jsonPath("$.content[?(@.title == 'Travel documents')]").isNotEmpty())
                .andExpect(jsonPath("$.content[?(@.title == 'Travel documents')].status").value("Ideas"))
                .andExpect(jsonPath("$.content[?(@.title == 'Travel documents')].priority").value("HIGHEST"));

        // They are ordinary tickets: numbered in this project's sequence, reported by the
        // person who created it, and editable like anything else.
        mvc.perform(get("/api/tickets/HOLIDAY-1").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Travel documents"))
                .andExpect(jsonPath("$.reporter.displayName").value("Tia Owner"));

        // A legal matter starts with entirely different work, in its own lanes.
        createProject("MATTER", templateIdOf("Legal Case"));
        mvc.perform(get("/api/projects/MATTER/tickets").param("size", "50")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.page.totalElements").value(4))
                .andExpect(jsonPath("$.content[?(@.title == 'Run the conflict check')].status")
                        .value("Intake"));
    }

    @Test
    void aTemplateMayPrescribeNoStartingWork() throws Exception {
        createProject("EMPTY", templateIdOf("Kanban"));

        mvc.perform(get("/api/projects/EMPTY/tickets").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(0));
    }

    @Test
    void aStarterTicketCannotNameALaneTheTemplateLacks() throws Exception {
        mvc.perform(post("/api/templates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + adminToken)
                        .content("""
                                {"name":"Mismatched","lanes":[
                                    {"name":"Open","initial":true,"done":false},
                                    {"name":"Shut","initial":false,"done":true}],
                                 "starterTickets":[
                                    {"title":"Kick off","type":"TASK","priority":"HIGH","lane":"Backlog"}]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString(
                        "names lane \"Backlog\", which this template does not have")));
    }

    @Test
    void anAdminCanDefineATemplateWithItsOwnStartingWork() throws Exception {
        mvc.perform(post("/api/templates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + adminToken)
                        .content("""
                                {"name":"Client Onboarding","description":"Taking on a new client",
                                 "lanes":[{"name":"To Collect","initial":true,"done":false},
                                          {"name":"In Review","initial":false,"done":false},
                                          {"name":"Complete","initial":false,"done":true}],
                                 "starterTickets":[
                                    {"title":"Signed contract","description":"Attach the countersigned copy.",
                                     "type":"TASK","priority":"HIGHEST","lane":"To Collect"},
                                    {"title":"Identity documents","type":"TASK","priority":"HIGH","lane":"To Collect"},
                                    {"title":"Kick-off call","type":"STORY","priority":"MEDIUM","lane":"In Review"}]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.starterTickets.length()").value(3))
                .andExpect(jsonPath("$.starterTickets[0].title").value("Signed contract"));

        createProject("ACME", templateIdOf("Client Onboarding"));
        mvc.perform(get("/api/projects/ACME/tickets").param("size", "50")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.page.totalElements").value(3))
                .andExpect(jsonPath("$.content[?(@.title == 'Kick-off call')].status").value("In Review"))
                .andExpect(jsonPath("$.content[?(@.title == 'Kick-off call')].type").value("STORY"));
    }

    @Test
    void editingStarterTicketsDoesNotTouchProjectsAlreadyMade() throws Exception {
        long id = templateIdOf("Recruitment");
        createProject("HIRE1", id);

        mvc.perform(put("/api/templates/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + adminToken)
                        .content("""
                                {"name":"Recruitment","description":"Trimmed",
                                 "lanes":[{"name":"Applied","initial":true,"done":false},
                                          {"name":"Decided","initial":false,"done":true}],
                                 "starterTickets":[
                                    {"title":"Only one now","type":"TASK","priority":"LOW","lane":"Applied"}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.starterTickets.length()").value(1));

        // The project keeps the three tickets it was created with.
        mvc.perform(get("/api/projects/HIRE1/tickets").param("size", "50")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.page.totalElements").value(3));

        // A project made now gets the new set.
        createProject("HIRE2", id);
        mvc.perform(get("/api/projects/HIRE2/tickets").param("size", "50")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Only one now"));
    }

    @Test
    void onlyAnAdministratorMayDefineATemplate() throws Exception {
        String body = """
                {"name":"Bookkeeping","description":"Invoices",
                 "lanes":[{"name":"Received","initial":true,"done":false},
                          {"name":"Paid","initial":false,"done":true}]}
                """;

        mvc.perform(post("/api/templates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + userToken)
                        .content(body))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/templates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + adminToken)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.builtIn").value(false))
                .andExpect(jsonPath("$.lanes.length()").value(2));

        // ...and anyone may then start a project from it.
        createProject("BOOKS", templateIdOf("Bookkeeping"));
        mvc.perform(get("/api/projects/BOOKS").header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.lanes[0].name").value("Received"));
    }

    @Test
    void aBoardNeedsExactlyOneStartingAndOneFinishedLane() throws Exception {
        for (String lanes : new String[]{
                // none marked
                "[{\"name\":\"A\",\"initial\":false,\"done\":false}]",
                // two starting lanes
                "[{\"name\":\"A\",\"initial\":true,\"done\":false},"
                        + "{\"name\":\"B\",\"initial\":true,\"done\":true}]",
                // duplicate names
                "[{\"name\":\"A\",\"initial\":true,\"done\":false},"
                        + "{\"name\":\"a\",\"initial\":false,\"done\":true}]",
        }) {
            mvc.perform(post("/api/templates")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("Authorization", "Bearer " + adminToken)
                            .content("{\"name\":\"Broken %s\",\"lanes\":%s}".formatted(lanes.hashCode(), lanes)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void aBuiltInTemplateCanBeEditedButNotDeleted() throws Exception {
        long id = templateIdOf("Recruitment");

        mvc.perform(delete("/api/templates/" + id).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict());

        mvc.perform(put("/api/templates/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + adminToken)
                        .content("""
                                {"name":"Recruitment","description":"Now with a shortlist step",
                                 "lanes":[{"name":"Applied","initial":true,"done":false},
                                          {"name":"Shortlisted","initial":false,"done":false},
                                          {"name":"Decided","initial":false,"done":true}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lanes.length()").value(3))
                .andExpect(jsonPath("$.lanes[1].name").value("Shortlisted"));
    }

    @Test
    void editingATemplateLeavesExistingProjectsAlone() throws Exception {
        long id = templateIdOf("Kanban");
        createProject("BEFORE", id);

        mvc.perform(put("/api/templates/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + adminToken)
                        .content("""
                                {"name":"Kanban","description":"Rearranged",
                                 "lanes":[{"name":"Inbox","initial":true,"done":false},
                                          {"name":"Shipped","initial":false,"done":true}]}
                                """))
                .andExpect(status().isOk());

        // The project copied its lanes; the template moving on does not move it.
        mvc.perform(get("/api/projects/BEFORE").header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.lanes.length()").value(3))
                .andExpect(jsonPath("$.lanes[0].name").value("To Do"));
    }
}

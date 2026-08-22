package behrainwala.issuetracker.config;

import behrainwala.issuetracker.domain.Project;
import behrainwala.issuetracker.domain.ProjectMember;
import behrainwala.issuetracker.domain.ProjectRole;
import behrainwala.issuetracker.domain.Role;
import behrainwala.issuetracker.domain.Ticket;
import behrainwala.issuetracker.domain.TicketPriority;
import behrainwala.issuetracker.domain.TicketType;
import behrainwala.issuetracker.domain.User;
import behrainwala.issuetracker.repo.ProjectMemberRepository;
import behrainwala.issuetracker.repo.ProjectRepository;
import behrainwala.issuetracker.repo.TicketRepository;
import behrainwala.issuetracker.repo.UserRepository;
import behrainwala.issuetracker.service.ProjectTemplateService;
import behrainwala.issuetracker.service.WorkflowService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds a usable playground the first time the app starts against an empty database.
 * Passwords are hashed here rather than in a migration so no hash is checked into SQL.
 * Disable with app.seed-demo-data=false.
 */
@Component
@ConditionalOnProperty(name = "app.seed-demo-data", havingValue = "true", matchIfMissing = true)
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final UserRepository users;
    private final ProjectRepository projects;
    private final ProjectMemberRepository members;
    private final TicketRepository tickets;
    private final PasswordEncoder encoder;
    private final WorkflowService workflow;
    private final ProjectTemplateService templates;

    public DemoDataSeeder(UserRepository users,
                          ProjectRepository projects,
                          ProjectMemberRepository members,
                          TicketRepository tickets,
                          PasswordEncoder encoder,
                          WorkflowService workflow,
                          ProjectTemplateService templates) {
        this.users = users;
        this.projects = projects;
        this.members = members;
        this.tickets = tickets;
        this.encoder = encoder;
        this.workflow = workflow;
        this.templates = templates;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.count() > 0) {
            return;
        }

        User admin = users.saveAndFlush(new User("admin", "admin@example.com",
                encoder.encode("admin123"), "Admin User", Role.ADMIN));
        User alice = users.saveAndFlush(new User("alice", "alice@example.com",
                encoder.encode("password"), "Alice Nguyen", Role.USER));
        User bob = users.saveAndFlush(new User("bob", "bob@example.com",
                encoder.encode("password"), "Bob Carter", Role.USER));

        // The seeded projects go through a template like any other, so the demo shows the
        // board coming from somewhere rather than existing by magic.
        var engineering = templates.require(templates.defaultTemplate().getId());
        var software = templates.byName("Software Development").orElse(engineering);

        Project platform = projects.saveAndFlush(
                new Project("PROJ1", "Platform", "Core platform services and APIs"));
        platform.setTemplate(software);
        workflow.applyTemplate(platform, software);

        Project web = projects.saveAndFlush(
                new Project("PROJ2", "Web App", "Customer-facing web experience"));
        web.setTemplate(software);
        workflow.applyTemplate(web, software);

        // PROJ1 deliberately has two leads, and Bob leads PROJ2 as well as co-leading nothing
        // else - showing both "many leads per project" and "many projects per lead".
        members.save(new ProjectMember(platform, alice, ProjectRole.LEAD));
        members.save(new ProjectMember(platform, admin, ProjectRole.LEAD));
        members.save(new ProjectMember(platform, bob, ProjectRole.MEMBER));
        members.save(new ProjectMember(web, bob, ProjectRole.LEAD));
        members.save(new ProjectMember(web, alice, ProjectRole.MEMBER));

        newTicket(platform, alice, "Set up CI pipeline", TicketType.TASK,
                "Done", TicketPriority.HIGH, bob);
        newTicket(platform, alice, "Rate limit the public API", TicketType.STORY,
                "In Progress", TicketPriority.HIGH, alice);
        newTicket(platform, bob, "Token refresh returns 500 on expiry", TicketType.BUG,
                "To Do", TicketPriority.HIGHEST, null);
        newTicket(web, bob, "Redesign the ticket board", TicketType.EPIC,
                "Backlog", TicketPriority.MEDIUM, null);
        newTicket(web, bob, "Keyboard shortcuts for triage", TicketType.STORY,
                "To Do", TicketPriority.LOW, alice);

        log.info("Seeded demo data. Logins: admin/admin123, alice/password, bob/password");
    }

    private void newTicket(Project project, User reporter, String title, TicketType type,
                           String status, TicketPriority priority, User assignee) {
        Ticket ticket = new Ticket(project, project.nextTicketNumber(), title, reporter);
        ticket.setType(type);
        ticket.setStatus(status);
        ticket.setPriority(priority);
        ticket.setAssignee(assignee);
        tickets.save(ticket);
    }
}

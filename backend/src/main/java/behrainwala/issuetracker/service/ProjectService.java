package behrainwala.issuetracker.service;

import behrainwala.issuetracker.config.AppProperties;
import behrainwala.issuetracker.domain.Project;
import behrainwala.issuetracker.domain.ProjectMember;
import behrainwala.issuetracker.domain.ProjectRole;
import behrainwala.issuetracker.domain.ProjectTemplate;
import behrainwala.issuetracker.domain.TemplateTicket;
import behrainwala.issuetracker.domain.Ticket;
import behrainwala.issuetracker.domain.User;
import behrainwala.issuetracker.dto.ProjectDtos.AddMemberRequest;
import behrainwala.issuetracker.dto.ProjectDtos.CreateProjectRequest;
import behrainwala.issuetracker.dto.ProjectDtos.MemberDto;
import behrainwala.issuetracker.dto.ProjectDtos.ProjectDto;
import behrainwala.issuetracker.dto.ProjectDtos.UpdateProjectRequest;
import behrainwala.issuetracker.dto.WorkflowDtos.LaneDto;
import behrainwala.issuetracker.dto.WorkflowDtos.LanesRequest;
import behrainwala.issuetracker.repo.ProjectMemberRepository;
import behrainwala.issuetracker.repo.ProjectRepository;
import behrainwala.issuetracker.repo.TicketRepository;
import behrainwala.issuetracker.web.ConflictException;
import behrainwala.issuetracker.web.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository memberRepository;
    private final TicketRepository ticketRepository;
    private final ProjectImageStore imageStore;
    private final WorkflowService workflow;
    private final ProjectTemplateService templateService;
    private final ImagePolicy imagePolicy;
    private final AppProperties.Projects projectSettings;
    private final UserService userService;
    private final AccessGuard accessGuard;

    public ProjectService(ProjectRepository projectRepository,
                          ProjectMemberRepository memberRepository,
                          TicketRepository ticketRepository,
                          ProjectImageStore imageStore,
                          WorkflowService workflow,
                          ProjectTemplateService templateService,
                          ImagePolicy imagePolicy,
                          AppProperties properties,
                          UserService userService,
                          AccessGuard accessGuard) {
        this.projectRepository = projectRepository;
        this.memberRepository = memberRepository;
        this.ticketRepository = ticketRepository;
        this.imageStore = imageStore;
        this.workflow = workflow;
        this.templateService = templateService;
        this.imagePolicy = imagePolicy;
        this.projectSettings = properties.getProjects();
        this.userService = userService;
        this.accessGuard = accessGuard;
    }

    public List<ProjectDto> listVisible(User current, boolean archived) {
        List<Project> projects = accessGuard.isAdmin(current)
                ? projectRepository.findAllWithMembers(archived)
                : projectRepository.findForUser(current.getId(), archived);
        return projects.stream().map(this::toDto).toList();
    }

    /** A project card always shows its live ticket count, never the archived ones. */
    private ProjectDto toDto(Project project) {
        return ProjectDto.from(project,
                ticketRepository.countByProjectIdAndArchivedAtIsNull(project.getId()),
                workflow.laneDtos(project));
    }

    /**
     * Archiving hides a project from the active list and freezes its contents. Unlike deleting
     * it, this is reversible - which is the point.
     */
    @Transactional
    public ProjectDto archive(String projectKey, User current) {
        Project project = requireByKey(projectKey);
        accessGuard.requireAdminister(project, current);
        if (project.isArchived()) {
            throw new ConflictException(project.getProjectKey() + " is already archived");
        }
        project.archive(current);
        return toDto(project);
    }

    @Transactional
    public ProjectDto restore(String projectKey, User current) {
        Project project = requireByKey(projectKey);
        accessGuard.requireAdminister(project, current);
        if (!project.isArchived()) {
            throw new ConflictException(project.getProjectKey() + " is not archived");
        }
        project.restore();
        return toDto(project);
    }

    public Project requireByKey(String projectKey) {
        return projectRepository.findByProjectKeyIgnoreCase(projectKey)
                .orElseThrow(() -> new NotFoundException("Project " + projectKey + " not found"));
    }

    public ProjectDto get(String projectKey, User current) {
        Project project = requireByKey(projectKey);
        accessGuard.requireView(project, current);
        return toDto(project);
    }

    @Transactional
    public ProjectDto create(CreateProjectRequest request, User current) {
        String key = request.projectKey().toUpperCase();
        if (projectRepository.existsByProjectKeyIgnoreCase(key)) {
            throw new ConflictException("Project key already in use: " + key);
        }
        Project project = new Project(key, request.name(), request.description());
        // A board is not optional: without lanes there is nowhere for a ticket to live. An
        // unspecified template gets the default rather than an empty board.
        ProjectTemplate template = request.templateId() == null
                ? templateService.defaultTemplate()
                : templateService.require(request.templateId());
        project.setTemplate(template);
        projectRepository.saveAndFlush(project);
        workflow.applyTemplate(project, template);
        seedStarterTickets(project, template, current);

        // The creator leads what they create; co-leads can be named up front or added later.
        addMember(project, current, ProjectRole.LEAD);
        if (request.additionalLeadIds() != null) {
            request.additionalLeadIds().stream()
                    .filter(id -> !id.equals(current.getId()))
                    .distinct()
                    .forEach(id -> addMember(project, userService.requireById(id), ProjectRole.LEAD));
        }
        memberRepository.flush();
        return toDto(project);
    }

    /**
     * Creates the template's starter tickets - the work this kind of project always begins
     * with. They are ordinary tickets from the moment they exist: renamed, moved, deleted or
     * archived like any other, with the creator as their reporter.
     * <p>
     * A starter ticket naming a lane the project does not have falls back to the starting
     * lane rather than failing the whole creation: a half-made project would be worse than a
     * ticket one column to the left.
     */
    private void seedStarterTickets(Project project, ProjectTemplate template, User creator) {
        String startingLane = workflow.initialLane(project).getName();

        for (TemplateTicket starter : template.getStarterTickets()) {
            Ticket ticket = new Ticket(project, project.nextTicketNumber(), starter.getTitle(), creator);
            ticket.setDescription(starter.getDescription());
            ticket.setType(starter.getType());
            ticket.setPriority(starter.getPriority());
            ticket.setStatus(starter.getLaneName() == null
                    ? startingLane
                    : workflow.laneNameOrDefault(project, starter.getLaneName(), startingLane));
            ticketRepository.save(ticket);
        }
    }

    private void addMember(Project project, User user, ProjectRole role) {
        ProjectMember member = memberRepository.save(new ProjectMember(project, user, role));
        project.getMembers().add(member);
    }

    @Transactional
    public ProjectDto update(String projectKey, UpdateProjectRequest request, User current) {
        Project project = requireByKey(projectKey);
        accessGuard.requireAdmin(project, current);
        project.setName(request.name());
        project.setDescription(request.description());
        return toDto(project);
    }

    /** The project's board, for anyone who can see it. */
    public List<LaneDto> lanes(String projectKey, User current) {
        Project project = requireByKey(projectKey);
        accessGuard.requireView(project, current);
        return workflow.laneDtos(project);
    }

    /**
     * Reshapes the board. Same rights as renaming the project, since the lanes are part of how
     * the project is set up rather than of the work in it.
     */
    @Transactional
    public List<LaneDto> setLanes(String projectKey, LanesRequest request, User current) {
        Project project = requireByKey(projectKey);
        accessGuard.requireAdmin(project, current);
        return workflow.replaceLanes(project, request.lanes());
    }

    /**
     * The project picture, for anyone who can see the project. Unlike the company logo this
     * is project data, so it is never public - it is fetched with a token like any other read.
     */
    public ProjectImage image(String projectKey, User current) {
        Project project = requireByKey(projectKey);
        accessGuard.requireView(project, current);

        byte[] bytes = project.hasImage() ? imageStore.read(project.getId()).orElse(null) : null;
        if (bytes == null) {
            throw new NotFoundException(project.getProjectKey() + " has no image");
        }
        return new ProjectImage(bytes, project.getImageContentType());
    }

    /** A stored project picture, ready to be streamed back. */
    public record ProjectImage(byte[] bytes, String contentType) {
    }

    /**
     * Sets or replaces the picture. Same rights as renaming the project - a lead or an
     * administrator - and an archived project refuses it like any other change.
     */
    @Transactional
    public ProjectDto setImage(String projectKey, MultipartFile file, User current) {
        Project project = requireByKey(projectKey);
        accessGuard.requireAdmin(project, current);

        ImagePolicy.Image image =
                imagePolicy.validate(file, projectSettings.getMaxImageBytes(), "A project image");

        // File first: if the metadata update then fails, the row still says there is no image
        // and the stale file is simply overwritten by the next upload. The reverse order would
        // advertise an image that is not there.
        imageStore.write(project.getId(), image.bytes());
        project.setImageMetadata(image.contentType(), image.filename());
        return toDto(project);
    }

    @Transactional
    public ProjectDto clearImage(String projectKey, User current) {
        Project project = requireByKey(projectKey);
        accessGuard.requireAdmin(project, current);

        project.clearImageMetadata();
        imageStore.delete(project.getId());
        return toDto(project);
    }

    @Transactional
    public void delete(String projectKey, User current) {
        Project project = requireByKey(projectKey);
        // Deliberately not requireAdmin: an archived project must still be deletable.
        accessGuard.requireAdminister(project, current);

        // An empty project is a bookkeeping mistake; one with tickets is a body of work, and
        // deleting it would take those tickets - and their comments, attachments and history -
        // with it in a single click. Emptying it first makes that deliberate.
        long tickets = ticketRepository.countByProjectId(project.getId());
        if (tickets > 0) {
            throw new ConflictException(
                    "%s still has %d ticket%s, archived included - delete them before deleting the project"
                            .formatted(project.getProjectKey(), tickets, tickets == 1 ? "" : "s"));
        }
        projectRepository.delete(project);
        // The row is going; its picture should not outlive it on disk.
        imageStore.delete(project.getId());
    }

    public List<MemberDto> listMembers(String projectKey, User current) {
        Project project = requireByKey(projectKey);
        accessGuard.requireView(project, current);
        return memberRepository.findByProjectId(project.getId()).stream()
                .map(MemberDto::from)
                .toList();
    }

    /**
     * Adds a user to the project or changes their role. Any lead of the project may do this,
     * which is what lets leads staff their own project without an administrator.
     */
    @Transactional
    public MemberDto addOrUpdateMember(String projectKey, AddMemberRequest request, User current) {
        Project project = requireByKey(projectKey);
        accessGuard.requireAdmin(project, current);
        User user = userService.requireById(request.userId());

        ProjectMember existing = memberRepository
                .findByProjectIdAndUserId(project.getId(), user.getId())
                .orElse(null);

        if (existing == null) {
            return MemberDto.from(memberRepository.save(
                    new ProjectMember(project, user, request.projectRole())));
        }
        if (existing.getProjectRole() == ProjectRole.LEAD && request.projectRole() != ProjectRole.LEAD) {
            requireAnotherLeadRemains(project, "demote");
        }
        existing.setProjectRole(request.projectRole());
        return MemberDto.from(memberRepository.save(existing));
    }

    @Transactional
    public void removeMember(String projectKey, Long userId, User current) {
        Project project = requireByKey(projectKey);
        accessGuard.requireAdmin(project, current);

        memberRepository.findByProjectIdAndUserId(project.getId(), userId)
                .ifPresent(member -> {
                    if (member.getProjectRole() == ProjectRole.LEAD) {
                        requireAnotherLeadRemains(project, "remove");
                    }
                    memberRepository.delete(member);
                });
    }

    /** A project with no lead could only ever be administered by a system admin again. */
    private void requireAnotherLeadRemains(Project project, String verb) {
        if (memberRepository.countByProjectIdAndProjectRole(project.getId(), ProjectRole.LEAD) <= 1) {
            throw new ConflictException(
                    "%s is the only lead of %s - promote another lead before you %s them"
                            .formatted("This user", project.getProjectKey(), verb));
        }
    }
}

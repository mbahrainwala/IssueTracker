package behrainwala.issuetracker.service;

import behrainwala.issuetracker.domain.Project;
import behrainwala.issuetracker.domain.ProjectMember;
import behrainwala.issuetracker.domain.ProjectRole;
import behrainwala.issuetracker.domain.User;
import behrainwala.issuetracker.dto.ProjectDtos.AddMemberRequest;
import behrainwala.issuetracker.dto.ProjectDtos.CreateProjectRequest;
import behrainwala.issuetracker.dto.ProjectDtos.MemberDto;
import behrainwala.issuetracker.dto.ProjectDtos.ProjectDto;
import behrainwala.issuetracker.dto.ProjectDtos.UpdateProjectRequest;
import behrainwala.issuetracker.repo.ProjectMemberRepository;
import behrainwala.issuetracker.repo.ProjectRepository;
import behrainwala.issuetracker.repo.TicketRepository;
import behrainwala.issuetracker.web.ConflictException;
import behrainwala.issuetracker.web.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository memberRepository;
    private final TicketRepository ticketRepository;
    private final UserService userService;
    private final AccessGuard accessGuard;

    public ProjectService(ProjectRepository projectRepository,
                          ProjectMemberRepository memberRepository,
                          TicketRepository ticketRepository,
                          UserService userService,
                          AccessGuard accessGuard) {
        this.projectRepository = projectRepository;
        this.memberRepository = memberRepository;
        this.ticketRepository = ticketRepository;
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
        return ProjectDto.from(project, ticketRepository.countByProjectIdAndArchivedAtIsNull(project.getId()));
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
        projectRepository.saveAndFlush(project);

        // The creator leads what they create; co-leads can be named up front or added later.
        addMember(project, current, ProjectRole.LEAD);
        if (request.additionalLeadIds() != null) {
            request.additionalLeadIds().stream()
                    .filter(id -> !id.equals(current.getId()))
                    .distinct()
                    .forEach(id -> addMember(project, userService.requireById(id), ProjectRole.LEAD));
        }
        memberRepository.flush();
        return ProjectDto.from(project, 0);
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

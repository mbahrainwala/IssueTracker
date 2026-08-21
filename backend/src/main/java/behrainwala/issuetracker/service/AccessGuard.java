package behrainwala.issuetracker.service;

import behrainwala.issuetracker.domain.Project;
import behrainwala.issuetracker.domain.ProjectMember;
import behrainwala.issuetracker.domain.ProjectRole;
import behrainwala.issuetracker.domain.Role;
import behrainwala.issuetracker.domain.Ticket;
import behrainwala.issuetracker.domain.User;
import behrainwala.issuetracker.repo.ProjectMemberRepository;
import behrainwala.issuetracker.web.ConflictException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Central place for "may this user touch this project?" decisions. */
@Component
public class AccessGuard {

    private final ProjectMemberRepository memberRepository;

    public AccessGuard(ProjectMemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    public boolean isAdmin(User user) {
        return user.getRole() == Role.ADMIN;
    }

    /** Leadership is just a membership row, so one lookup answers every access question. */
    public Optional<ProjectRole> projectRoleOf(Project project, User user) {
        return memberRepository.findByProjectIdAndUserId(project.getId(), user.getId())
                .map(ProjectMember::getProjectRole);
    }

    public boolean canView(Project project, User user) {
        return isAdmin(user) || projectRoleOf(project, user).isPresent();
    }

    public boolean canWrite(Project project, User user) {
        if (isAdmin(user)) {
            return true;
        }
        return projectRoleOf(project, user)
                .map(role -> role == ProjectRole.LEAD || role == ProjectRole.MEMBER)
                .orElse(false);
    }

    public boolean canAdminister(Project project, User user) {
        return isAdmin(user) || projectRoleOf(project, user).filter(r -> r == ProjectRole.LEAD).isPresent();
    }

    public void requireView(Project project, User user) {
        if (!canView(project, user)) {
            throw new AccessDeniedException("Not a member of project " + project.getProjectKey());
        }
    }

    /**
     * An archived project is frozen. Enforcing it here means every write path already goes
     * through the check; archiving, restoring and deleting a project deliberately use
     * {@link #canAdminister} directly so they still work on an archived one.
     */
    public void requireActive(Project project) {
        if (project.isArchived()) {
            throw new ConflictException(
                    "Project %s is archived - restore it before making changes"
                            .formatted(project.getProjectKey()));
        }
    }

    /**
     * Read-only means read-only: an archived ticket, or any ticket in an archived project,
     * refuses every content change - comments and links included, not just its own fields.
     */
    public void requireActive(Ticket ticket) {
        requireActive(ticket.getProject());
        if (ticket.isArchived()) {
            throw new ConflictException(
                    ticket.getTicketKey() + " is archived - restore it before making changes");
        }
    }

    public void requireWrite(Project project, User user) {
        if (!canWrite(project, user)) {
            throw new AccessDeniedException("Write access required on project " + project.getProjectKey());
        }
        requireActive(project);
    }

    public void requireAdmin(Project project, User user) {
        if (!canAdminister(project, user)) {
            throw new AccessDeniedException("Project lead or admin required on " + project.getProjectKey());
        }
        requireActive(project);
    }

    public boolean isProjectLead(Project project, User user) {
        return projectRoleOf(project, user).filter(r -> r == ProjectRole.LEAD).isPresent();
    }
}

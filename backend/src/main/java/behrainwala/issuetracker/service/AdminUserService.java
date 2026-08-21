package behrainwala.issuetracker.service;

import behrainwala.issuetracker.domain.Project;
import behrainwala.issuetracker.domain.ProjectMember;
import behrainwala.issuetracker.domain.ProjectRole;
import behrainwala.issuetracker.domain.Role;
import behrainwala.issuetracker.domain.User;
import behrainwala.issuetracker.dto.AdminUserDtos.AssignProjectsRequest;
import behrainwala.issuetracker.dto.AdminUserDtos.AssignProjectsRequest.Assignment;
import behrainwala.issuetracker.dto.AdminUserDtos.CreateUserRequest;
import behrainwala.issuetracker.dto.AdminUserDtos.ProjectAssignmentDto;
import behrainwala.issuetracker.dto.AdminUserDtos.ResetPasswordRequest;
import behrainwala.issuetracker.dto.AdminUserDtos.UpdateUserRequest;
import behrainwala.issuetracker.dto.UserDto;
import behrainwala.issuetracker.repo.ProjectMemberRepository;
import behrainwala.issuetracker.repo.ProjectRepository;
import behrainwala.issuetracker.repo.UserRepository;
import behrainwala.issuetracker.web.ConflictException;
import behrainwala.issuetracker.web.NotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * User administration. Accounts are disabled rather than deleted so the tickets and
 * comments they authored keep pointing at a real person.
 */
@Service
@Transactional(readOnly = true)
public class AdminUserService {

    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository memberRepository;
    private final AccessGuard accessGuard;
    private final PasswordEncoder passwordEncoder;

    public AdminUserService(UserRepository userRepository,
                            ProjectRepository projectRepository,
                            ProjectMemberRepository memberRepository,
                            AccessGuard accessGuard,
                            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.projectRepository = projectRepository;
        this.memberRepository = memberRepository;
        this.accessGuard = accessGuard;
        this.passwordEncoder = passwordEncoder;
    }

    public List<UserDto> list() {
        return userRepository.findAll().stream()
                .sorted(Comparator.comparing(User::getDisplayName, String.CASE_INSENSITIVE_ORDER))
                .map(UserDto::from)
                .toList();
    }

    @Transactional
    public UserDto create(CreateUserRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new ConflictException("Username already taken: " + request.username());
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new ConflictException("Email already registered: " + request.email());
        }
        User user = new User(
                request.username(),
                request.email(),
                passwordEncoder.encode(request.password()),
                request.displayName(),
                request.role());
        return UserDto.from(userRepository.save(user));
    }

    @Transactional
    public UserDto update(Long id, UpdateUserRequest request, User current) {
        User user = require(id);

        userRepository.findByEmail(request.email())
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw new ConflictException("Email already registered: " + request.email());
                });

        boolean losingAdmin = user.getRole() == Role.ADMIN
                && (request.role() != Role.ADMIN || !request.enabled());
        if (losingAdmin) {
            guardSelf(user, current);
            guardLastAdmin();
        }
        if (!request.enabled()) {
            guardSelf(user, current);
        }

        user.setEmail(request.email());
        user.setDisplayName(request.displayName());
        user.setRole(request.role());
        user.setEnabled(request.enabled());
        return UserDto.from(user);
    }

    @Transactional
    public UserDto setEnabled(Long id, boolean enabled, User current) {
        User user = require(id);
        if (!enabled) {
            guardSelf(user, current);
            if (user.getRole() == Role.ADMIN) {
                guardLastAdmin();
            }
        }
        user.setEnabled(enabled);
        return UserDto.from(user);
    }

    @Transactional
    public void resetPassword(Long id, ResetPasswordRequest request) {
        require(id).setPasswordHash(passwordEncoder.encode(request.password()));
    }

    /** Every project this user is assigned to, with the role they hold there. */
    public List<ProjectAssignmentDto> assignments(Long userId) {
        require(userId);
        return memberRepository.findByUserId(userId).stream()
                .map(member -> {
                    Project project = member.getProject();
                    return new ProjectAssignmentDto(project.getId(), project.getProjectKey(),
                            project.getName(), member.getProjectRole());
                })
                .sorted(Comparator.comparing(ProjectAssignmentDto::projectKey))
                .toList();
    }

    /** Replaces the user's assignments with exactly the requested set. */
    @Transactional
    public List<ProjectAssignmentDto> setAssignments(Long userId, AssignProjectsRequest request) {
        User user = require(userId);

        Map<String, ProjectRole> wanted = new LinkedHashMap<>();
        for (Assignment assignment : request.assignments()) {
            Project project = projectRepository.findByProjectKeyIgnoreCase(assignment.projectKey())
                    .orElseThrow(() -> new NotFoundException("Project " + assignment.projectKey() + " not found"));
            // This path is admin-only and bypasses AccessGuard, so the archive rule is restated.
            accessGuard.requireActive(project);
            wanted.put(project.getProjectKey(), assignment.projectRole());
        }

        for (ProjectMember existing : memberRepository.findByUserId(userId)) {
            Project project = existing.getProject();
            // Membership of an archived project stays as it is until the project comes back.
            if (project.isArchived()) {
                continue;
            }
            ProjectRole role = wanted.remove(project.getProjectKey());
            boolean losingLead = existing.getProjectRole() == ProjectRole.LEAD && role != ProjectRole.LEAD;
            if (losingLead) {
                requireAnotherLeadRemains(project);
            }
            if (role == null) {
                memberRepository.delete(existing);
            } else if (role != existing.getProjectRole()) {
                existing.setProjectRole(role);
            }
        }
        // Whatever is left in `wanted` is a project the user was not a member of yet.
        for (Map.Entry<String, ProjectRole> entry : wanted.entrySet()) {
            Project project = projectRepository.findByProjectKeyIgnoreCase(entry.getKey()).orElseThrow();
            memberRepository.save(new ProjectMember(project, user, entry.getValue()));
        }
        memberRepository.flush();
        return assignments(userId);
    }

    private void requireAnotherLeadRemains(Project project) {
        if (memberRepository.countByProjectIdAndProjectRole(project.getId(), ProjectRole.LEAD) <= 1) {
            throw new ConflictException(
                    "This user is the only lead of " + project.getProjectKey()
                            + " - promote another lead first");
        }
    }

    private User require(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("User " + id + " not found"));
    }

    /** Stops an admin locking themselves out with a stray click. */
    private void guardSelf(User target, User current) {
        if (target.getId().equals(current.getId())) {
            throw new ConflictException("You cannot disable or demote your own account");
        }
    }

    private void guardLastAdmin() {
        if (userRepository.countByRoleAndEnabledTrue(Role.ADMIN) <= 1) {
            throw new ConflictException("This is the last enabled administrator - promote another first");
        }
    }
}

package behrainwala.issuetracker.web;

import behrainwala.issuetracker.dto.ProjectDtos.AddMemberRequest;
import behrainwala.issuetracker.dto.ProjectDtos.CreateProjectRequest;
import behrainwala.issuetracker.dto.ProjectDtos.MemberDto;
import behrainwala.issuetracker.dto.ProjectDtos.ProjectDto;
import behrainwala.issuetracker.dto.ProjectDtos.UpdateProjectRequest;
import behrainwala.issuetracker.service.ProjectService;
import behrainwala.issuetracker.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;
    private final UserService userService;

    public ProjectController(ProjectService projectService, UserService userService) {
        this.projectService = projectService;
        this.userService = userService;
    }

    @GetMapping
    public List<ProjectDto> list(Authentication auth) {
        return projectService.listVisible(userService.currentUser(auth));
    }

    @GetMapping("/{projectKey}")
    public ProjectDto get(@PathVariable String projectKey, Authentication auth) {
        return projectService.get(projectKey, userService.currentUser(auth));
    }

    @PostMapping
    public ResponseEntity<ProjectDto> create(@Valid @RequestBody CreateProjectRequest request, Authentication auth) {
        ProjectDto created = projectService.create(request, userService.currentUser(auth));
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{projectKey}")
    public ProjectDto update(@PathVariable String projectKey,
                             @Valid @RequestBody UpdateProjectRequest request,
                             Authentication auth) {
        return projectService.update(projectKey, request, userService.currentUser(auth));
    }

    @DeleteMapping("/{projectKey}")
    public ResponseEntity<Void> delete(@PathVariable String projectKey, Authentication auth) {
        projectService.delete(projectKey, userService.currentUser(auth));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{projectKey}/members")
    public List<MemberDto> members(@PathVariable String projectKey, Authentication auth) {
        return projectService.listMembers(projectKey, userService.currentUser(auth));
    }

    @PostMapping("/{projectKey}/members")
    public MemberDto addMember(@PathVariable String projectKey,
                               @Valid @RequestBody AddMemberRequest request,
                               Authentication auth) {
        return projectService.addOrUpdateMember(projectKey, request, userService.currentUser(auth));
    }

    @DeleteMapping("/{projectKey}/members/{userId}")
    public ResponseEntity<Void> removeMember(@PathVariable String projectKey,
                                             @PathVariable Long userId,
                                             Authentication auth) {
        projectService.removeMember(projectKey, userId, userService.currentUser(auth));
        return ResponseEntity.noContent().build();
    }
}

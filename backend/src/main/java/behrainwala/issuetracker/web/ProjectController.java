package behrainwala.issuetracker.web;

import behrainwala.issuetracker.dto.ProjectDtos.AddMemberRequest;
import behrainwala.issuetracker.dto.ProjectDtos.CreateProjectRequest;
import behrainwala.issuetracker.dto.ProjectDtos.MemberDto;
import behrainwala.issuetracker.dto.ProjectDtos.ProjectDto;
import behrainwala.issuetracker.dto.ProjectDtos.UpdateProjectRequest;
import behrainwala.issuetracker.dto.WorkflowDtos.LaneDto;
import behrainwala.issuetracker.dto.WorkflowDtos.LanesRequest;
import behrainwala.issuetracker.service.ProjectService;
import behrainwala.issuetracker.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

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

    /** Active projects by default; pass archived=true for the archived tab. */
    @GetMapping
    public List<ProjectDto> list(@RequestParam(defaultValue = "false") boolean archived,
                                 Authentication auth) {
        return projectService.listVisible(userService.currentUser(auth), archived);
    }

    @PostMapping("/{projectKey}/archive")
    public ProjectDto archive(@PathVariable String projectKey, Authentication auth) {
        return projectService.archive(projectKey, userService.currentUser(auth));
    }

    @PostMapping("/{projectKey}/restore")
    public ProjectDto restore(@PathVariable String projectKey, Authentication auth) {
        return projectService.restore(projectKey, userService.currentUser(auth));
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

    /** The project's swim lanes, left to right. */
    @GetMapping("/{projectKey}/lanes")
    public List<LaneDto> lanes(@PathVariable String projectKey, Authentication auth) {
        return projectService.lanes(projectKey, userService.currentUser(auth));
    }

    /**
     * Replaces the whole board. Sent whole rather than as add/rename/reorder calls because a
     * board is edited as a shape: the submitted order is the new order.
     */
    @PutMapping("/{projectKey}/lanes")
    public List<LaneDto> setLanes(@PathVariable String projectKey,
                                  @Valid @RequestBody LanesRequest request,
                                  Authentication auth) {
        return projectService.setLanes(projectKey, request, userService.currentUser(auth));
    }

    /**
     * The project picture. Requires read access to the project, so unlike the company logo
     * this cannot be a plain {@code <img src>} - the client fetches it with its token.
     * <p>
     * Served with its real type because it has to render, but with sniffing off and a CSP
     * that denies everything and sandboxes the document, so an uploaded SVG opened directly
     * is a picture rather than a scripting context.
     */
    @GetMapping("/{projectKey}/image")
    public ResponseEntity<byte[]> image(@PathVariable String projectKey, Authentication auth) {
        ProjectService.ProjectImage image =
                projectService.image(projectKey, userService.currentUser(auth));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.contentType()))
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "default-src 'none'; sandbox")
                // Per-user authorised, so a shared cache must not hold it; the client
                // cache-busts with the version from the project payload.
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=3600")
                .body(image.bytes());
    }

    @PutMapping("/{projectKey}/image")
    public ProjectDto setImage(@PathVariable String projectKey,
                               @RequestParam("file") MultipartFile file,
                               Authentication auth) {
        return projectService.setImage(projectKey, file, userService.currentUser(auth));
    }

    @DeleteMapping("/{projectKey}/image")
    public ProjectDto clearImage(@PathVariable String projectKey, Authentication auth) {
        return projectService.clearImage(projectKey, userService.currentUser(auth));
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

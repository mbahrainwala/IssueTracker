package behrainwala.issuetracker.web;

import behrainwala.issuetracker.dto.AdminUserDtos.AssignProjectsRequest;
import behrainwala.issuetracker.dto.AdminUserDtos.CreateUserRequest;
import behrainwala.issuetracker.dto.AdminUserDtos.ProjectAssignmentDto;
import behrainwala.issuetracker.dto.AdminUserDtos.ResetPasswordRequest;
import behrainwala.issuetracker.dto.AdminUserDtos.UpdateUserRequest;
import behrainwala.issuetracker.dto.UserDto;
import behrainwala.issuetracker.service.AdminUserService;
import behrainwala.issuetracker.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final AdminUserService adminUserService;
    private final UserService userService;

    public AdminUserController(AdminUserService adminUserService, UserService userService) {
        this.adminUserService = adminUserService;
        this.userService = userService;
    }

    @GetMapping
    public List<UserDto> list() {
        return adminUserService.list();
    }

    @PostMapping
    public ResponseEntity<UserDto> create(@Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminUserService.create(request));
    }

    @PutMapping("/{id}")
    public UserDto update(@PathVariable Long id,
                          @Valid @RequestBody UpdateUserRequest request,
                          Authentication auth) {
        return adminUserService.update(id, request, userService.currentUser(auth));
    }

    @PatchMapping("/{id}/enabled")
    public UserDto setEnabled(@PathVariable Long id,
                              @RequestParam boolean enabled,
                              Authentication auth) {
        return adminUserService.setEnabled(id, enabled, userService.currentUser(auth));
    }

    @PutMapping("/{id}/password")
    public ResponseEntity<Void> resetPassword(@PathVariable Long id,
                                              @Valid @RequestBody ResetPasswordRequest request) {
        adminUserService.resetPassword(id, request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/projects")
    public List<ProjectAssignmentDto> assignments(@PathVariable Long id) {
        return adminUserService.assignments(id);
    }

    /** Replaces the user's project assignments with exactly the supplied set. */
    @PutMapping("/{id}/projects")
    public List<ProjectAssignmentDto> setAssignments(@PathVariable Long id,
                                                     @Valid @RequestBody AssignProjectsRequest request) {
        return adminUserService.setAssignments(id, request);
    }
}

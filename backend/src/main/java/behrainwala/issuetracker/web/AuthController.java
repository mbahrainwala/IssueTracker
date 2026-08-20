package behrainwala.issuetracker.web;

import behrainwala.issuetracker.dto.AuthDtos.AuthResponse;
import behrainwala.issuetracker.dto.AuthDtos.ChangePasswordRequest;
import behrainwala.issuetracker.dto.AuthDtos.LoginRequest;
import behrainwala.issuetracker.dto.AuthDtos.RegisterRequest;
import behrainwala.issuetracker.dto.AdminUserDtos.ProjectAssignmentDto;
import behrainwala.issuetracker.dto.UserDto;
import behrainwala.issuetracker.service.AdminUserService;
import behrainwala.issuetracker.service.AuthService;
import behrainwala.issuetracker.service.UserService;

import java.util.List;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final UserService userService;
    private final AdminUserService adminUserService;

    public AuthController(AuthService authService,
                          UserService userService,
                          AdminUserService adminUserService) {
        this.authService = authService;
        this.userService = userService;
        this.adminUserService = adminUserService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    /** Requires a valid token; used by the SPA to rehydrate the session on refresh. */
    @GetMapping("/me")
    public UserDto me(Authentication authentication) {
        return UserDto.from(userService.currentUser(authentication));
    }

    /** The caller's own project assignments, for the profile panel. */
    @GetMapping("/me/projects")
    public List<ProjectAssignmentDto> myProjects(Authentication authentication) {
        return adminUserService.assignments(userService.currentUser(authentication).getId());
    }

    /** Available to every signed-in user, for their own account only. */
    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request,
                                               Authentication authentication) {
        authService.changePassword(userService.currentUser(authentication), request);
        return ResponseEntity.noContent().build();
    }
}

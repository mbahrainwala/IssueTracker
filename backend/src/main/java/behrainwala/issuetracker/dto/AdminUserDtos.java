package behrainwala.issuetracker.dto;

import behrainwala.issuetracker.domain.ProjectRole;
import behrainwala.issuetracker.domain.Role;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class AdminUserDtos {

    private AdminUserDtos() {
    }

    public record CreateUserRequest(
            @NotBlank @Size(min = 3, max = 60) String username,
            @NotBlank @Email @Size(max = 190) String email,
            @NotBlank @Size(min = 8, max = 100) String password,
            @NotBlank @Size(max = 120) String displayName,
            @NotNull Role role) {
    }

    /** Username is deliberately absent: it is the JWT subject, so it stays immutable. */
    public record UpdateUserRequest(
            @NotBlank @Email @Size(max = 190) String email,
            @NotBlank @Size(max = 120) String displayName,
            @NotNull Role role,
            @NotNull Boolean enabled) {
    }

    public record ResetPasswordRequest(
            @NotBlank @Size(min = 8, max = 100) String password) {
    }

    /** One project a user is assigned to, and the role they hold there. */
    public record ProjectAssignmentDto(
            Long projectId,
            String projectKey,
            String projectName,
            ProjectRole projectRole) {
    }

    public record AssignProjectsRequest(
            @NotNull @Valid List<Assignment> assignments) {

        public record Assignment(
                @NotBlank String projectKey,
                @NotNull ProjectRole projectRole) {
        }
    }
}

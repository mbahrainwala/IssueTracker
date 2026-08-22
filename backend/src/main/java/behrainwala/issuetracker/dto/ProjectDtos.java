package behrainwala.issuetracker.dto;

import behrainwala.issuetracker.domain.Project;
import behrainwala.issuetracker.domain.ProjectMember;
import behrainwala.issuetracker.domain.ProjectRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class ProjectDtos {

    private ProjectDtos() {
    }

    /**
     * The creator always becomes a lead. Extra leads can be named up front via
     * {@code additionalLeadIds}, or added later through the members endpoints.
     */
    public record CreateProjectRequest(
            @NotBlank
            @Pattern(regexp = "^[A-Za-z][A-Za-z0-9]{1,9}$",
                    message = "key must be 2-10 alphanumeric characters starting with a letter")
            String projectKey,
            @NotBlank @Size(max = 150) String name,
            @Size(max = 4000) String description,
            List<Long> additionalLeadIds) {
    }

    /** Leadership is a membership role, so it is managed through the members endpoints. */
    public record UpdateProjectRequest(
            @NotBlank @Size(max = 150) String name,
            @Size(max = 4000) String description) {
    }

    public record AddMemberRequest(
            @NotNull Long userId,
            @NotNull ProjectRole projectRole) {
    }

    public record MemberDto(UserDto user, ProjectRole projectRole) {

        public static MemberDto from(ProjectMember member) {
            return new MemberDto(UserDto.from(member.getUser()), member.getProjectRole());
        }
    }

    public record ProjectDto(
            Long id,
            String projectKey,
            String name,
            String description,
            List<UserDto> leads,
            long ticketCount,
            boolean archived,
            Instant archivedAt,
            UserDto archivedBy,
            boolean hasImage,
            /** Last-updated stamp, appended to the image URL so a replacement is not cached. */
            Long imageVersion,
            Instant createdAt) {

        public static ProjectDto from(Project project, long ticketCount) {
            return new ProjectDto(
                    project.getId(),
                    project.getProjectKey(),
                    project.getName(),
                    project.getDescription(),
                    project.getLeads().stream().map(UserDto::from).toList(),
                    ticketCount,
                    project.isArchived(),
                    project.getArchivedAt(),
                    UserDto.from(project.getArchivedBy()),
                    project.hasImage(),
                    project.getImageUpdatedAt() == null ? null : project.getImageUpdatedAt().toEpochMilli(),
                    project.getCreatedAt());
        }
    }
}

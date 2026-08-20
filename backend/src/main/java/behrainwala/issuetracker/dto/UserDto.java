package behrainwala.issuetracker.dto;

import behrainwala.issuetracker.domain.Role;
import behrainwala.issuetracker.domain.User;

public record UserDto(Long id, String username, String email, String displayName, Role role, boolean enabled) {

    public static UserDto from(User user) {
        if (user == null) {
            return null;
        }
        return new UserDto(user.getId(), user.getUsername(), user.getEmail(), user.getDisplayName(),
                user.getRole(), user.isEnabled());
    }
}

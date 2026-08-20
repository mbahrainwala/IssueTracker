package behrainwala.issuetracker.service;

import behrainwala.issuetracker.domain.User;
import behrainwala.issuetracker.dto.UserDto;
import behrainwala.issuetracker.repo.UserRepository;
import behrainwala.issuetracker.security.AppUserPrincipal;
import behrainwala.issuetracker.web.NotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User requireById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("User " + id + " not found"));
    }

    public User currentUser(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AppUserPrincipal principal)) {
            throw new NotFoundException("No authenticated user");
        }
        return requireById(principal.getId());
    }

    public List<UserDto> listAll() {
        return userRepository.findAll().stream()
                .sorted(Comparator.comparing(User::getDisplayName, String.CASE_INSENSITIVE_ORDER))
                .map(UserDto::from)
                .toList();
    }
}

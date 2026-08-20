package behrainwala.issuetracker.service;

import behrainwala.issuetracker.domain.Role;
import behrainwala.issuetracker.domain.User;
import behrainwala.issuetracker.dto.AuthDtos.AuthResponse;
import behrainwala.issuetracker.dto.AuthDtos.ChangePasswordRequest;
import behrainwala.issuetracker.dto.AuthDtos.LoginRequest;
import behrainwala.issuetracker.dto.AuthDtos.RegisterRequest;
import behrainwala.issuetracker.dto.UserDto;
import behrainwala.issuetracker.repo.UserRepository;
import behrainwala.issuetracker.security.AppUserPrincipal;
import behrainwala.issuetracker.security.JwtService;
import behrainwala.issuetracker.web.ConflictException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager,
                       JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new ConflictException("Username already taken: " + request.username());
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new ConflictException("Email already registered: " + request.email());
        }
        // The very first account bootstraps the system administrator.
        Role role = userRepository.count() == 0 ? Role.ADMIN : Role.USER;
        User user = new User(
                request.username(),
                request.email(),
                passwordEncoder.encode(request.password()),
                request.displayName(),
                role);
        userRepository.save(user);
        return tokenFor(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.usernameOrEmail(), request.password()));
        AppUserPrincipal principal = (AppUserPrincipal) authentication.getPrincipal();
        User user = userRepository.findById(principal.getId()).orElseThrow();
        return tokenFor(user);
    }

    /**
     * Any signed-in user can change their own password. Re-checking the current password
     * means a walk-up on an unlocked screen cannot silently take the account over.
     */
    @Transactional
    public void changePassword(User user, ChangePasswordRequest request) {
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("The new password must differ from the current one");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
    }

    private AuthResponse tokenFor(User user) {
        String token = jwtService.generateToken(user.getUsername(), Map.of(
                "uid", user.getId(),
                "role", user.getRole().name(),
                "name", user.getDisplayName()));
        return new AuthResponse(token, jwtService.getTtlSeconds(), UserDto.from(user));
    }
}

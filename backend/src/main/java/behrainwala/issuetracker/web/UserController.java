package behrainwala.issuetracker.web;

import behrainwala.issuetracker.dto.UserDto;
import behrainwala.issuetracker.service.UserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /** Directory used for assignee and member pickers. */
    @GetMapping
    public List<UserDto> list() {
        return userService.listAll();
    }
}

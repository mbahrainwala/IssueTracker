package behrainwala.issuetracker.web;

import behrainwala.issuetracker.dto.WorkflowDtos.TemplateDto;
import behrainwala.issuetracker.dto.WorkflowDtos.TemplateRequest;
import behrainwala.issuetracker.service.ProjectTemplateService;
import behrainwala.issuetracker.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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

/**
 * Reading templates is open to any signed-in user - the create-project dialog offers them.
 * Defining them is administrator-only: a template shapes how everybody else's projects begin.
 */
@RestController
@RequestMapping("/api/templates")
public class ProjectTemplateController {

    private final ProjectTemplateService templateService;
    private final UserService userService;

    public ProjectTemplateController(ProjectTemplateService templateService, UserService userService) {
        this.templateService = templateService;
        this.userService = userService;
    }

    @GetMapping
    public List<TemplateDto> list() {
        return templateService.list();
    }

    @GetMapping("/{id}")
    public TemplateDto get(@PathVariable Long id) {
        return templateService.get(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TemplateDto> create(@Valid @RequestBody TemplateRequest request,
                                              Authentication auth) {
        TemplateDto created = templateService.create(request, userService.currentUser(auth));
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public TemplateDto update(@PathVariable Long id,
                              @Valid @RequestBody TemplateRequest request,
                              Authentication auth) {
        return templateService.update(id, request, userService.currentUser(auth));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        templateService.delete(id);
        return ResponseEntity.noContent().build();
    }
}

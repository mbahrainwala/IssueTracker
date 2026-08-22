package behrainwala.issuetracker.service;

import behrainwala.issuetracker.domain.ProjectTemplate;
import behrainwala.issuetracker.domain.TemplateLane;
import behrainwala.issuetracker.domain.TemplateTicket;
import behrainwala.issuetracker.domain.TicketPriority;
import behrainwala.issuetracker.domain.TicketType;
import behrainwala.issuetracker.domain.User;
import behrainwala.issuetracker.dto.WorkflowDtos.LaneRequest;
import behrainwala.issuetracker.dto.WorkflowDtos.StarterTicketRequest;
import behrainwala.issuetracker.dto.WorkflowDtos.TemplateDto;
import behrainwala.issuetracker.dto.WorkflowDtos.TemplateRequest;
import behrainwala.issuetracker.repo.ProjectRepository;
import behrainwala.issuetracker.repo.ProjectTemplateRepository;
import behrainwala.issuetracker.web.ConflictException;
import behrainwala.issuetracker.web.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Project templates: reusable board blueprints.
 * <p>
 * Reading them is open to any signed-in user, because the create-project dialog offers them.
 * Defining them is an administrator's job - a template shapes how everyone else's projects
 * start, so it is an installation-wide decision rather than a per-project one.
 */
@Service
@Transactional(readOnly = true)
public class ProjectTemplateService {

    private final ProjectTemplateRepository templateRepository;
    private final ProjectRepository projectRepository;

    public ProjectTemplateService(ProjectTemplateRepository templateRepository,
                                  ProjectRepository projectRepository) {
        this.templateRepository = templateRepository;
        this.projectRepository = projectRepository;
    }

    public List<TemplateDto> list() {
        return templateRepository.findAllWithLanes().stream().map(TemplateDto::from).toList();
    }

    public TemplateDto get(Long id) {
        return TemplateDto.from(require(id));
    }

    public ProjectTemplate require(Long id) {
        return templateRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Template " + id + " not found"));
    }

    /**
     * The template a project gets when the caller names none. Kanban ships with the app and
     * is the least opinionated board there is; if it has been renamed away, anything will do
     * rather than refusing to create a project.
     */
    public java.util.Optional<ProjectTemplate> byName(String name) {
        return templateRepository.findByNameIgnoreCase(name);
    }

    public ProjectTemplate defaultTemplate() {
        return templateRepository.findByNameIgnoreCase("Kanban")
                .or(() -> templateRepository.findAll().stream().findFirst())
                .orElseThrow(() -> new IllegalStateException(
                        "No project templates exist - the V12 migration seeds them"));
    }

    @Transactional
    public TemplateDto create(TemplateRequest request, User current) {
        String name = request.name().trim();
        if (templateRepository.existsByNameIgnoreCase(name)) {
            throw new ConflictException("A template called \"" + name + "\" already exists");
        }
        WorkflowService.validate(request.lanes());

        ProjectTemplate template = new ProjectTemplate(name, blankToNull(request.description()), current);
        applyLanes(template, request.lanes());
        applyStarterTickets(template, request);
        return TemplateDto.from(templateRepository.save(template));
    }

    /**
     * Replaces a template's name, description and lanes. Projects already created from it are
     * untouched: their lanes were copied, not linked.
     */
    @Transactional
    public TemplateDto update(Long id, TemplateRequest request, User current) {
        ProjectTemplate template = require(id);
        String name = request.name().trim();
        if (!template.getName().equalsIgnoreCase(name) && templateRepository.existsByNameIgnoreCase(name)) {
            throw new ConflictException("A template called \"" + name + "\" already exists");
        }
        WorkflowService.validate(request.lanes());

        template.setName(name);
        template.setDescription(blankToNull(request.description()));

        // Flushed between the clear and the re-add on purpose. In one flush Hibernate orders
        // inserts before orphan deletes, so re-using a lane name that is still on the old row
        // trips the (template_id, name) unique index - which is exactly what editing a
        // template usually does, since most lanes keep their names.
        template.getLanes().clear();
        template.getStarterTickets().clear();
        templateRepository.saveAndFlush(template);

        applyLanes(template, request.lanes());
        applyStarterTickets(template, request);
        return TemplateDto.from(templateRepository.save(template));
    }

    @Transactional
    public void delete(Long id) {
        ProjectTemplate template = require(id);
        if (template.isBuiltIn()) {
            throw new ConflictException(
                    "\"%s\" ships with the app and cannot be deleted - edit it instead"
                            .formatted(template.getName()));
        }
        // Projects keep a reference for display only; clear it rather than block the delete.
        projectRepository.clearTemplate(template.getId());
        templateRepository.delete(template);
    }

    /**
     * Starter tickets, each pinned to a lane by name. A lane the template does not have is
     * refused rather than quietly relocated: it almost always means a lane was renamed and
     * this ticket was left pointing at the old name.
     */
    private void applyStarterTickets(ProjectTemplate template, TemplateRequest request) {
        List<StarterTicketRequest> tickets =
                request.starterTickets() == null ? List.of() : request.starterTickets();

        Set<String> laneNames = request.lanes().stream()
                .map(lane -> lane.name().trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());

        int order = 0;
        for (StarterTicketRequest ticket : tickets) {
            String lane = blankToNull(ticket.lane());
            if (lane != null && !laneNames.contains(lane.toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException(
                        "Starter ticket \"%s\" names lane \"%s\", which this template does not have"
                                .formatted(ticket.title().trim(), lane));
            }
            template.getStarterTickets().add(new TemplateTicket(
                    template,
                    ticket.title().trim(),
                    blankToNull(ticket.description()),
                    ticket.type() == null ? TicketType.TASK : ticket.type(),
                    ticket.priority() == null ? TicketPriority.MEDIUM : ticket.priority(),
                    lane,
                    order++));
        }
    }

    private void applyLanes(ProjectTemplate template, List<LaneRequest> lanes) {
        int order = 0;
        for (LaneRequest lane : lanes) {
            template.getLanes().add(new TemplateLane(
                    template, lane.name().trim(), order++, lane.initial(), lane.done()));
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

package behrainwala.issuetracker.service;

import behrainwala.issuetracker.domain.Project;
import behrainwala.issuetracker.domain.Ticket;
import behrainwala.issuetracker.domain.TicketPriority;
import behrainwala.issuetracker.domain.TicketStatus;
import behrainwala.issuetracker.domain.TicketType;
import behrainwala.issuetracker.domain.TicketStatusChange;
import behrainwala.issuetracker.domain.User;
import behrainwala.issuetracker.dto.TicketDtos.CreateTicketRequest;
import behrainwala.issuetracker.dto.TicketDtos.EpicRefDto;
import behrainwala.issuetracker.dto.TicketDtos.TicketDto;
import behrainwala.issuetracker.dto.TicketDtos.UpdateTicketRequest;
import behrainwala.issuetracker.dto.TicketHistoryDtos.StatusChangeDto;
import behrainwala.issuetracker.dto.TicketLinkDtos.LinkedTicketDto;
import behrainwala.issuetracker.repo.ProjectRepository;
import behrainwala.issuetracker.repo.TicketRepository;
import behrainwala.issuetracker.repo.TicketStatusChangeRepository;
import behrainwala.issuetracker.web.ConflictException;
import behrainwala.issuetracker.web.NotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class TicketService {

    private final TicketRepository ticketRepository;
    private final ProjectRepository projectRepository;
    private final TicketStatusChangeRepository statusChangeRepository;
    private final ProjectService projectService;
    private final UserService userService;
    private final AccessGuard accessGuard;

    public TicketService(TicketRepository ticketRepository,
                         ProjectRepository projectRepository,
                         TicketStatusChangeRepository statusChangeRepository,
                         ProjectService projectService,
                         UserService userService,
                         AccessGuard accessGuard) {
        this.ticketRepository = ticketRepository;
        this.projectRepository = projectRepository;
        this.statusChangeRepository = statusChangeRepository;
        this.projectService = projectService;
        this.userService = userService;
        this.accessGuard = accessGuard;
    }

    public Page<TicketDto> list(String projectKey,
                                TicketStatus status,
                                Long assigneeId,
                                String q,
                                boolean archived,
                                Pageable pageable,
                                User current) {
        Project project = projectService.requireByKey(projectKey);
        accessGuard.requireView(project, current);
        // The archived tab has no status columns to filter by, so it never narrows on status.
        String statusName = archived || status == null ? null : status.name();
        return ticketRepository
                .search(project.getId(), archived, statusName, assigneeId, blankToNull(q), pageable)
                .map(TicketDto::from);
    }

    /**
     * Archives a finished ticket. Only DONE work can be archived, and an epic additionally
     * needs every one of its tickets archived first - otherwise archiving the epic would
     * hide live work behind it.
     */
    @Transactional
    public TicketDto archive(String ticketKey, User current) {
        Ticket ticket = requireByKey(ticketKey);
        accessGuard.requireWrite(ticket.getProject(), current);

        if (ticket.isArchived()) {
            throw new ConflictException(ticket.getTicketKey() + " is already archived");
        }
        if (ticket.getStatus() != TicketStatus.DONE) {
            throw new ConflictException(
                    "Only tickets in %s can be archived - %s is in %s"
                            .formatted(TicketStatus.DONE.getLabel(), ticket.getTicketKey(),
                                    ticket.getStatus().getLabel()));
        }
        if (ticket.getType() == TicketType.EPIC) {
            long live = ticketRepository.countByEpicIdAndArchivedAtIsNull(ticket.getId());
            if (live > 0) {
                throw new ConflictException(
                        "%s still has %d ticket%s that %s not archived - archive them first"
                                .formatted(ticket.getTicketKey(), live, live == 1 ? "" : "s",
                                        live == 1 ? "is" : "are"));
            }
        }
        ticket.archive(current);
        return TicketDto.from(ticket);
    }

    /** Brings a ticket back into the active views. */
    @Transactional
    public TicketDto restore(String ticketKey, User current) {
        Ticket ticket = requireByKey(ticketKey);
        accessGuard.requireWrite(ticket.getProject(), current);

        if (!ticket.isArchived()) {
            throw new ConflictException(ticket.getTicketKey() + " is not archived");
        }
        // Restoring a child under an archived epic would leave live work hidden behind it.
        if (ticket.getEpic() != null && ticket.getEpic().isArchived()) {
            throw new ConflictException(
                    "Its epic %s is archived - restore the epic first"
                            .formatted(ticket.getEpic().getTicketKey()));
        }
        ticket.restore();
        return TicketDto.from(ticket);
    }

    /**
     * Ticket lookup across every project the caller can see. Used by the link picker,
     * so results are filtered by project visibility rather than by project key.
     */
    public List<LinkedTicketDto> searchVisible(String q, String excludeTicketKey, User current) {
        if (q == null || q.isBlank()) {
            return List.of();
        }
        return ticketRepository.searchAll(q.trim(), PageRequest.of(0, 50)).stream()
                .filter(t -> !t.getTicketKey().equalsIgnoreCase(excludeTicketKey))
                .filter(t -> accessGuard.canView(t.getProject(), current))
                .limit(15)
                .map(LinkedTicketDto::from)
                .toList();
    }

    /** Epics of a project, for the "which epic?" picker. */
    public List<EpicRefDto> listEpics(String projectKey, User current) {
        Project project = projectService.requireByKey(projectKey);
        accessGuard.requireView(project, current);
        return ticketRepository
                .findByProjectIdAndTypeOrderByTicketNumberDesc(project.getId(), TicketType.EPIC).stream()
                .map(EpicRefDto::from)
                .toList();
    }

    public Ticket requireByKey(String ticketKey) {
        return ticketRepository.findByTicketKeyIgnoreCase(ticketKey)
                .orElseThrow(() -> new NotFoundException("Ticket " + ticketKey + " not found"));
    }

    public TicketDto get(String ticketKey, User current) {
        Ticket ticket = requireByKey(ticketKey);
        accessGuard.requireView(ticket.getProject(), current);
        return TicketDto.from(ticket);
    }

    @Transactional
    public TicketDto create(String projectKey, CreateTicketRequest request, User current) {
        Project project = projectService.requireByKey(projectKey);
        accessGuard.requireWrite(project, current);

        // Re-read under a row lock so two concurrent creates cannot claim the same number.
        Project locked = projectRepository.findByIdForUpdate(project.getId())
                .orElseThrow(() -> new NotFoundException("Project " + projectKey + " not found"));

        Ticket ticket = new Ticket(locked, locked.nextTicketNumber(), request.title(), current);
        ticket.setDescription(request.description());
        ticket.setType(request.type() == null ? TicketType.TASK : request.type());
        ticket.setStatus(request.status() == null ? TicketStatus.BACKLOG : request.status());
        ticket.setPriority(request.priority() == null ? TicketPriority.MEDIUM : request.priority());
        ticket.setStoryPoints(request.storyPoints());
        ticket.setDueDate(request.dueDate());
        if (request.assigneeId() != null) {
            ticket.setAssignee(userService.requireById(request.assigneeId()));
        }
        if (request.epicKey() != null && !request.epicKey().isBlank()) {
            ticket.setEpic(resolveEpic(request.epicKey(), ticket));
        }
        return TicketDto.from(ticketRepository.save(ticket));
    }

    /**
     * An epic must be an EPIC-typed ticket in the same project, and an epic cannot itself
     * sit inside one - that keeps the hierarchy exactly one level deep and cycle-free.
     */
    private Ticket resolveEpic(String epicKey, Ticket ticket) {
        if (ticket.getType() == TicketType.EPIC) {
            throw new ConflictException("An epic cannot be placed inside another epic");
        }
        Ticket epic = requireByKey(epicKey);
        if (epic.getType() != TicketType.EPIC) {
            throw new ConflictException(epic.getTicketKey() + " is not an epic");
        }
        // Filing live work under an archived epic would hide it, same reasoning as restore.
        if (epic.isArchived()) {
            throw new ConflictException(
                    "Epic " + epic.getTicketKey() + " is archived - restore it first");
        }
        if (!epic.getProject().getId().equals(ticket.getProject().getId())) {
            throw new ConflictException("An epic must belong to the same project as its tickets");
        }
        if (ticket.getId() != null && epic.getId().equals(ticket.getId())) {
            throw new ConflictException("A ticket cannot be its own epic");
        }
        return epic;
    }

    /** The tickets gathered under an epic, newest first. */
    public List<TicketDto> children(String epicKey, User current) {
        Ticket epic = requireEpic(epicKey);
        accessGuard.requireView(epic.getProject(), current);
        return childrenOf(epic);
    }

    private List<TicketDto> childrenOf(Ticket epic) {
        return ticketRepository.findByEpicIdOrderByTicketNumberDesc(epic.getId()).stream()
                .map(TicketDto::from)
                .toList();
    }

    private Ticket requireEpic(String epicKey) {
        Ticket epic = requireByKey(epicKey);
        if (epic.getType() != TicketType.EPIC) {
            throw new ConflictException(epic.getTicketKey() + " is not an epic");
        }
        return epic;
    }

    /**
     * Tickets that may be filed under this epic: same project only, epics excluded, and
     * anything already in this epic left out.
     */
    public List<TicketDto> epicCandidates(String epicKey, String q, User current) {
        Ticket epic = requireEpic(epicKey);
        accessGuard.requireView(epic.getProject(), current);
        String needle = q == null || q.isBlank() ? null : q.trim();
        return ticketRepository
                .findEpicCandidates(epic.getProject().getId(), epic.getId(), needle, PageRequest.of(0, 25))
                .stream()
                .map(TicketDto::from)
                .toList();
    }

    /** Adds existing tickets to an epic from the epic's own page. */
    @Transactional
    public List<TicketDto> addChildren(String epicKey, List<String> ticketKeys, User current) {
        Ticket epic = requireEpic(epicKey);
        accessGuard.requireWrite(epic, current);

        for (String key : ticketKeys) {
            Ticket ticket = requireByKey(key);
            accessGuard.requireActive(ticket);
            // resolveEpic re-checks same-project, not-an-epic and no-self-parent for each one.
            ticket.setEpic(resolveEpic(epic.getTicketKey(), ticket));
        }
        ticketRepository.flush();
        return childrenOf(epic);
    }

    /** Removes one ticket from an epic without deleting the ticket. */
    @Transactional
    public List<TicketDto> removeChild(String epicKey, String ticketKey, User current) {
        Ticket epic = requireEpic(epicKey);
        accessGuard.requireWrite(epic, current);

        Ticket ticket = requireByKey(ticketKey);
        accessGuard.requireActive(ticket);
        if (ticket.getEpic() == null || !ticket.getEpic().getId().equals(epic.getId())) {
            throw new ConflictException(ticket.getTicketKey() + " is not in " + epic.getTicketKey());
        }
        ticket.setEpic(null);
        ticketRepository.flush();
        return childrenOf(epic);
    }

    @Transactional
    public TicketDto update(String ticketKey, UpdateTicketRequest request, User current) {
        Ticket ticket = requireByKey(ticketKey);
        accessGuard.requireWrite(ticket, current);

        if (request.title() != null) {
            ticket.setTitle(request.title());
        }
        if (request.description() != null) {
            ticket.setDescription(request.description());
        }
        if (request.type() != null) {
            // Turning a ticket into an epic drops its own parent, since epics cannot nest.
            if (request.type() == TicketType.EPIC && ticket.getEpic() != null) {
                ticket.setEpic(null);
            }
            ticket.setType(request.type());
        }
        if (request.status() != null) {
            recordMove(ticket, request.status(), current);
        }
        if (request.priority() != null) {
            ticket.setPriority(request.priority());
        }
        if (request.clearAssignee()) {
            ticket.setAssignee(null);
        } else if (request.assigneeId() != null) {
            ticket.setAssignee(userService.requireById(request.assigneeId()));
        }
        if (request.clearEpic()) {
            ticket.setEpic(null);
        } else if (request.epicKey() != null && !request.epicKey().isBlank()) {
            ticket.setEpic(resolveEpic(request.epicKey(), ticket));
        }
        if (request.storyPoints() != null) {
            ticket.setStoryPoints(request.storyPoints());
        }
        if (request.dueDate() != null) {
            ticket.setDueDate(request.dueDate());
        }
        return TicketDto.from(ticket);
    }

    @Transactional
    public TicketDto transition(String ticketKey, TicketStatus status, User current) {
        Ticket ticket = requireByKey(ticketKey);
        accessGuard.requireWrite(ticket, current);
        recordMove(ticket, status, current);
        return TicketDto.from(ticket);
    }

    /**
     * Moves the ticket and logs who did it. A "move" to the status it already has is not a
     * move, so it leaves no entry - otherwise re-saving a form would pollute the history.
     */
    private void recordMove(Ticket ticket, TicketStatus target, User current) {
        TicketStatus previous = ticket.getStatus();
        if (previous == target) {
            return;
        }
        ticket.setStatus(target);
        statusChangeRepository.save(new TicketStatusChange(ticket, previous, target, current));
    }

    /** Every recorded move of this ticket, oldest first. */
    public List<StatusChangeDto> history(String ticketKey, User current) {
        Ticket ticket = requireByKey(ticketKey);
        accessGuard.requireView(ticket.getProject(), current);
        return statusChangeRepository.findByTicketIdOrderByIdAsc(ticket.getId()).stream()
                .map(StatusChangeDto::from)
                .toList();
    }

    @Transactional
    public void delete(String ticketKey, User current) {
        Ticket ticket = requireByKey(ticketKey);
        // Administrators only: not a project lead, who can archive instead. Deleting takes
        // the ticket's comments, attachments, links and history with it and cannot be undone.
        accessGuard.requireGlobalAdmin(current, "Only an administrator can delete a ticket");
        accessGuard.requireActive(ticket.getProject());
        ticketRepository.delete(ticket);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}

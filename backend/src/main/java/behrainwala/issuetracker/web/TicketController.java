package behrainwala.issuetracker.web;

import behrainwala.issuetracker.domain.TicketStatus;
import behrainwala.issuetracker.dto.TicketDtos.AddChildrenRequest;
import behrainwala.issuetracker.dto.TicketDtos.CreateTicketRequest;
import behrainwala.issuetracker.dto.TicketDtos.EpicRefDto;
import behrainwala.issuetracker.dto.TicketDtos.TicketDto;
import behrainwala.issuetracker.dto.TicketDtos.UpdateTicketRequest;
import behrainwala.issuetracker.dto.TicketHistoryDtos.StatusChangeDto;
import behrainwala.issuetracker.dto.TicketLinkDtos.CreateLinkRequest;
import behrainwala.issuetracker.dto.TicketLinkDtos.LinkedTicketDto;
import behrainwala.issuetracker.dto.TicketLinkDtos.TicketLinkDto;
import behrainwala.issuetracker.service.TicketLinkService;
import behrainwala.issuetracker.service.TicketService;
import behrainwala.issuetracker.service.UserService;
import jakarta.validation.Valid;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class TicketController {

    private final TicketService ticketService;
    private final TicketLinkService ticketLinkService;
    private final UserService userService;

    public TicketController(TicketService ticketService,
                            TicketLinkService ticketLinkService,
                            UserService userService) {
        this.ticketService = ticketService;
        this.ticketLinkService = ticketLinkService;
        this.userService = userService;
    }

    @GetMapping("/projects/{projectKey}/tickets")
    public Page<TicketDto> list(@PathVariable String projectKey,
                                @RequestParam(required = false) TicketStatus status,
                                @RequestParam(required = false) Long assigneeId,
                                @RequestParam(required = false) String q,
                                @RequestParam(defaultValue = "0") int page,
                                @RequestParam(defaultValue = "50") int size,
                                Authentication auth) {
        var pageable = PageRequest.of(page, Math.min(size, 200), Sort.by(Sort.Direction.DESC, "ticketNumber"));
        return ticketService.list(projectKey, status, assigneeId, q, pageable, userService.currentUser(auth));
    }

    @PostMapping("/projects/{projectKey}/tickets")
    public ResponseEntity<TicketDto> create(@PathVariable String projectKey,
                                            @Valid @RequestBody CreateTicketRequest request,
                                            Authentication auth) {
        TicketDto created = ticketService.create(projectKey, request, userService.currentUser(auth));
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /** Epics of a project, for the epic picker. */
    @GetMapping("/projects/{projectKey}/epics")
    public List<EpicRefDto> epics(@PathVariable String projectKey, Authentication auth) {
        return ticketService.listEpics(projectKey, userService.currentUser(auth));
    }

    /** The tickets gathered under an epic. */
    @GetMapping("/tickets/{ticketKey}/children")
    public List<TicketDto> children(@PathVariable String ticketKey, Authentication auth) {
        return ticketService.children(ticketKey, userService.currentUser(auth));
    }

    /** Tickets that could be added to this epic - same project only. */
    @GetMapping("/tickets/{ticketKey}/candidates")
    public List<TicketDto> epicCandidates(@PathVariable String ticketKey,
                                          @RequestParam(required = false) String q,
                                          Authentication auth) {
        return ticketService.epicCandidates(ticketKey, q, userService.currentUser(auth));
    }

    /** Adds existing tickets to this epic. */
    @PostMapping("/tickets/{ticketKey}/children")
    public List<TicketDto> addChildren(@PathVariable String ticketKey,
                                       @Valid @RequestBody AddChildrenRequest request,
                                       Authentication auth) {
        return ticketService.addChildren(ticketKey, request.ticketKeys(), userService.currentUser(auth));
    }

    /** Removes a ticket from this epic; the ticket itself is untouched. */
    @DeleteMapping("/tickets/{ticketKey}/children/{childKey}")
    public List<TicketDto> removeChild(@PathVariable String ticketKey,
                                       @PathVariable String childKey,
                                       Authentication auth) {
        return ticketService.removeChild(ticketKey, childKey, userService.currentUser(auth));
    }

    /** Who moved this ticket between buckets, and when. */
    @GetMapping("/tickets/{ticketKey}/history")
    public List<StatusChangeDto> history(@PathVariable String ticketKey, Authentication auth) {
        return ticketService.history(ticketKey, userService.currentUser(auth));
    }

    /** Cross-project ticket lookup for the link picker. */
    @GetMapping("/tickets/search")
    public List<LinkedTicketDto> search(@RequestParam String q,
                                        @RequestParam(required = false) String exclude,
                                        Authentication auth) {
        return ticketService.searchVisible(q, exclude, userService.currentUser(auth));
    }

    @GetMapping("/tickets/{ticketKey}")
    public TicketDto get(@PathVariable String ticketKey, Authentication auth) {
        return ticketService.get(ticketKey, userService.currentUser(auth));
    }

    @GetMapping("/tickets/{ticketKey}/links")
    public List<TicketLinkDto> links(@PathVariable String ticketKey, Authentication auth) {
        return ticketLinkService.list(ticketKey, userService.currentUser(auth));
    }

    @PostMapping("/tickets/{ticketKey}/links")
    public ResponseEntity<List<TicketLinkDto>> addLink(@PathVariable String ticketKey,
                                                       @Valid @RequestBody CreateLinkRequest request,
                                                       Authentication auth) {
        List<TicketLinkDto> links = ticketLinkService.create(ticketKey, request, userService.currentUser(auth));
        return ResponseEntity.status(HttpStatus.CREATED).body(links);
    }

    @DeleteMapping("/links/{linkId}")
    public ResponseEntity<Void> removeLink(@PathVariable Long linkId, Authentication auth) {
        ticketLinkService.delete(linkId, userService.currentUser(auth));
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/tickets/{ticketKey}")
    public TicketDto update(@PathVariable String ticketKey,
                            @Valid @RequestBody UpdateTicketRequest request,
                            Authentication auth) {
        return ticketService.update(ticketKey, request, userService.currentUser(auth));
    }

    /** Board drag-and-drop uses this narrow endpoint instead of a full update. */
    @PatchMapping("/tickets/{ticketKey}/status")
    public TicketDto transition(@PathVariable String ticketKey,
                                @RequestParam TicketStatus status,
                                Authentication auth) {
        return ticketService.transition(ticketKey, status, userService.currentUser(auth));
    }

    @DeleteMapping("/tickets/{ticketKey}")
    public ResponseEntity<Void> delete(@PathVariable String ticketKey, Authentication auth) {
        ticketService.delete(ticketKey, userService.currentUser(auth));
        return ResponseEntity.noContent().build();
    }
}

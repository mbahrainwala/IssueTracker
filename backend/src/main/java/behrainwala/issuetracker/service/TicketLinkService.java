package behrainwala.issuetracker.service;

import behrainwala.issuetracker.domain.LinkType;
import behrainwala.issuetracker.domain.Ticket;
import behrainwala.issuetracker.domain.TicketLink;
import behrainwala.issuetracker.domain.User;
import behrainwala.issuetracker.dto.TicketLinkDtos.CreateLinkRequest;
import behrainwala.issuetracker.dto.TicketLinkDtos.LinkedTicketDto;
import behrainwala.issuetracker.dto.TicketLinkDtos.TicketLinkDto;
import behrainwala.issuetracker.repo.TicketLinkRepository;
import behrainwala.issuetracker.web.ConflictException;
import behrainwala.issuetracker.web.NotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class TicketLinkService {

    private final TicketLinkRepository linkRepository;
    private final TicketService ticketService;
    private final AccessGuard accessGuard;

    public TicketLinkService(TicketLinkRepository linkRepository,
                             TicketService ticketService,
                             AccessGuard accessGuard) {
        this.linkRepository = linkRepository;
        this.ticketService = ticketService;
        this.accessGuard = accessGuard;
    }

    public List<TicketLinkDto> list(String ticketKey, User current) {
        Ticket ticket = ticketService.requireByKey(ticketKey);
        accessGuard.requireView(ticket.getProject(), current);
        return linksFor(ticket, current);
    }

    /**
     * Links may cross projects, so each row is filtered against what the caller may see -
     * a link must never leak the title of a ticket in a project they have no access to.
     */
    private List<TicketLinkDto> linksFor(Ticket ticket, User current) {
        List<TicketLinkDto> result = new ArrayList<>();

        for (TicketLink link : linkRepository.findBySourceId(ticket.getId())) {
            if (accessGuard.canView(link.getTarget().getProject(), current)) {
                result.add(new TicketLinkDto(link.getId(), link.getLinkType(),
                        link.getLinkType().getLabel(), LinkedTicketDto.from(link.getTarget())));
            }
        }
        for (TicketLink link : linkRepository.findByTargetId(ticket.getId())) {
            if (accessGuard.canView(link.getSource().getProject(), current)) {
                LinkType inverse = link.getLinkType().inverse();
                result.add(new TicketLinkDto(link.getId(), inverse,
                        inverse.getLabel(), LinkedTicketDto.from(link.getSource())));
            }
        }
        result.sort(Comparator.comparing((TicketLinkDto l) -> l.linkType().ordinal())
                .thenComparing(l -> l.ticket().ticketKey()));
        return result;
    }

    @Transactional
    public List<TicketLinkDto> create(String ticketKey, CreateLinkRequest request, User current) {
        Ticket source = ticketService.requireByKey(ticketKey);
        accessGuard.requireWrite(source, current);

        Ticket target = ticketService.requireByKey(request.targetTicketKey());
        // Seeing the other ticket is required; editing it is not.
        accessGuard.requireView(target.getProject(), current);
        // ...but a link would appear on the target too, so an archived one stays untouched.
        accessGuard.requireActive(target);

        if (source.getId().equals(target.getId())) {
            throw new ConflictException("A ticket cannot be linked to itself");
        }
        if (linkRepository.findBySourceIdAndTargetIdAndLinkType(
                source.getId(), target.getId(), request.linkType()).isPresent()) {
            throw new ConflictException(
                    "%s already %s %s".formatted(source.getTicketKey(),
                            request.linkType().getLabel(), target.getTicketKey()));
        }
        // The same relationship stored the other way round is the same link.
        if (linkRepository.findBySourceIdAndTargetIdAndLinkType(
                target.getId(), source.getId(), request.linkType().inverse()).isPresent()) {
            throw new ConflictException(
                    "%s already %s %s".formatted(source.getTicketKey(),
                            request.linkType().getLabel(), target.getTicketKey()));
        }

        linkRepository.save(new TicketLink(source, target, request.linkType(), current));
        return linksFor(source, current);
    }

    /** Either end of the link may remove it, provided the caller can write there. */
    @Transactional
    public void delete(Long linkId, User current) {
        TicketLink link = linkRepository.findById(linkId)
                .orElseThrow(() -> new NotFoundException("Link " + linkId + " not found"));

        boolean canEditEitherEnd = accessGuard.canWrite(link.getSource().getProject(), current)
                || accessGuard.canWrite(link.getTarget().getProject(), current);
        if (!canEditEitherEnd) {
            throw new AccessDeniedException("Write access required on one of the linked projects");
        }
        // canWrite is a plain permission check and skips the archive rule, so state it here:
        // removing the link would change what either end shows.
        accessGuard.requireActive(link.getSource());
        accessGuard.requireActive(link.getTarget());
        linkRepository.delete(link);
    }
}

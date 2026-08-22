package behrainwala.issuetracker.service;

import behrainwala.issuetracker.domain.Comment;
import behrainwala.issuetracker.domain.Ticket;
import behrainwala.issuetracker.domain.TicketMention;
import behrainwala.issuetracker.domain.User;
import behrainwala.issuetracker.dto.MentionDtos.MentionDto;
import behrainwala.issuetracker.repo.TicketMentionRepository;
import behrainwala.issuetracker.repo.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@code @username} in a comment or a ticket description.
 * <p>
 * A mention flags the ticket for that person until they open it and say they have seen it. The
 * acknowledgement is a comment rather than a dismiss button, so the record of "yes, I have read
 * this" lives on the ticket where the person who asked can see it.
 */
@Service
@Transactional(readOnly = true)
public class MentionService {

    /**
     * Usernames are 3-60 characters at registration. The trailing boundary stops an email
     * address in the text ("mail bob@example.com") from reading as a mention of "bob" -
     * a mention must not be preceded by a word character.
     */
    private static final Pattern MENTION = Pattern.compile("(?<![\\w@])@([A-Za-z0-9._-]{3,60})");

    private final TicketMentionRepository mentionRepository;
    private final UserRepository userRepository;
    private final AccessGuard accessGuard;

    public MentionService(TicketMentionRepository mentionRepository,
                          UserRepository userRepository,
                          AccessGuard accessGuard) {
        this.mentionRepository = mentionRepository;
        this.userRepository = userRepository;
        this.accessGuard = accessGuard;
    }

    /** The distinct usernames named in a piece of text, in the order they appear. */
    public static Set<String> parse(String text) {
        Set<String> names = new LinkedHashSet<>();
        if (text == null || text.isBlank()) {
            return names;
        }
        Matcher matcher = MENTION.matcher(text);
        while (matcher.find()) {
            names.add(matcher.group(1).toLowerCase(Locale.ROOT));
        }
        return names;
    }

    /**
     * Records a mention for every real user named in the text.
     * <p>
     * Silently skips three cases rather than failing the comment: a name that is not a user at
     * all (people write "@here" and email addresses), someone who cannot see the project - the
     * flag would point at a ticket they would be refused - and the author naming themselves,
     * which needs no acknowledgement from anyone.
     *
     * @param source the comment the mention came from, or null for the ticket description
     */
    @Transactional
    public void record(Ticket ticket, String text, User author, Comment source) {
        for (String username : parse(text)) {
            User mentioned = userRepository.findByUsername(username).orElse(null);
            if (mentioned == null
                    || !mentioned.isEnabled()
                    || mentioned.getId().equals(author.getId())
                    || !accessGuard.canView(ticket.getProject(), mentioned)) {
                continue;
            }
            Long sourceId = source == null ? null : source.getId();
            if (mentionRepository.alreadyOutstanding(ticket.getId(), mentioned.getId(), sourceId)) {
                continue;
            }
            mentionRepository.save(new TicketMention(ticket, mentioned, author, source));
        }
    }

    /**
     * Everything still waiting for this person, across every project they can see.
     * <p>
     * Mapped here rather than in the controller: the DTO reads through to the ticket's project
     * and the comment the mention came from, and open-in-view is off, so that has to happen
     * while the transaction is still open.
     */
    public List<MentionDto> outstandingFor(User user) {
        return mentionRepository.findOutstandingFor(user.getId()).stream()
                .map(MentionDto::from)
                .toList();
    }

    public List<TicketMention> outstandingOn(Ticket ticket, User user) {
        return mentionRepository.findOutstandingOn(ticket.getId(), user.getId());
    }

    /**
     * Marks everything outstanding on this ticket as seen, pointing each at the comment the
     * acknowledger wrote. All of them at once: what is being acknowledged is the ticket, not
     * one comment on it.
     */
    @Transactional
    public int acknowledge(Ticket ticket, User user, Comment acknowledgement) {
        List<TicketMention> outstanding = outstandingOn(ticket, user);
        outstanding.forEach(mention -> mention.acknowledge(acknowledgement));
        return outstanding.size();
    }
}

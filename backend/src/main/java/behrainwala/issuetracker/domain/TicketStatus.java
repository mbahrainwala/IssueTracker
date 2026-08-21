package behrainwala.issuetracker.domain;

/**
 * The board's status buckets. Each carries its display label: deriving one from the enum name
 * gets TODO wrong ("Todo" rather than "To Do"), so the wording is stated, not computed.
 * Keep in step with STATUS_LABELS in the frontend.
 */
public enum TicketStatus {

    BACKLOG("Backlog"),
    TODO("To Do"),
    IN_PROGRESS("In Progress"),
    IN_REVIEW("In Review"),
    DONE("Done");

    private final String label;

    TicketStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
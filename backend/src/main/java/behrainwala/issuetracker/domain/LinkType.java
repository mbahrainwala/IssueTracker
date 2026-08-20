package behrainwala.issuetracker.domain;

/**
 * Relationship between two tickets, stored once as a directed row. Viewing the link from
 * the other ticket shows {@link #inverse()}, so "A blocks B" reads as "B is blocked by A"
 * without a second row to keep in step.
 */
public enum LinkType {

    RELATES_TO("relates to"),
    BLOCKS("blocks"),
    IS_BLOCKED_BY("is blocked by"),
    DUPLICATES("duplicates"),
    IS_DUPLICATED_BY("is duplicated by"),
    CAUSES("causes"),
    IS_CAUSED_BY("is caused by");

    private final String label;

    LinkType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public LinkType inverse() {
        return switch (this) {
            case RELATES_TO -> RELATES_TO;
            case BLOCKS -> IS_BLOCKED_BY;
            case IS_BLOCKED_BY -> BLOCKS;
            case DUPLICATES -> IS_DUPLICATED_BY;
            case IS_DUPLICATED_BY -> DUPLICATES;
            case CAUSES -> IS_CAUSED_BY;
            case IS_CAUSED_BY -> CAUSES;
        };
    }
}

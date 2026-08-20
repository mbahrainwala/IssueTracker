package behrainwala.issuetracker.domain;

/** Global (system-wide) role. Per-project permissions live in {@link ProjectRole}. */
public enum Role {
    ADMIN,
    USER
}

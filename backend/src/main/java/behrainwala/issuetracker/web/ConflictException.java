package behrainwala.issuetracker.web;

public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}

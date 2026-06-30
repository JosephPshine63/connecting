package dev.pioruocco.wacchat.user;

public class SessionConflictException extends RuntimeException {

    public SessionConflictException(String userId) {
        super("Another session is already active for user " + userId);
    }
}

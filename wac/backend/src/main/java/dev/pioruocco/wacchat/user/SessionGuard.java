package dev.pioruocco.wacchat.user;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class SessionGuard {

    private final long staleAfterSeconds;
    private final int maxActiveSessions;

    public SessionGuard(@Value("${application.session.stale-after-seconds}") long staleAfterSeconds,
                         @Value("${application.session.max-active-sessions}") int maxActiveSessions) {
        this.staleAfterSeconds = staleAfterSeconds;
        this.maxActiveSessions = maxActiveSessions;
    }

    /**
     * True when {@code sid} doesn't belong to any of the user's currently fresh sessions
     * and the fresh-session count is already at the configured max — i.e. accepting this
     * tab would exceed the concurrent-session limit.
     */
    public boolean isConflicting(User user, String sid) {
        List<ActiveSession> fresh = freshSessions(user);
        boolean alreadyTracked = fresh.stream().anyMatch(session -> sid.equals(session.getTabId()));
        if (alreadyTracked) {
            return false;
        }
        return fresh.size() >= maxActiveSessions;
    }

    /**
     * Records/refreshes {@code sid}'s last-seen timestamp among the user's active sessions,
     * dropping any that have gone stale. Must only be called after {@link #isConflicting}
     * has returned false for the same {@code sid}.
     */
    public void recordSession(User user, String sid) {
        LocalDateTime now = LocalDateTime.now();
        List<ActiveSession> sessions = user.getActiveSessions();
        sessions.removeIf(session -> !isFresh(session, now));
        sessions.stream()
                .filter(session -> sid.equals(session.getTabId()))
                .findFirst()
                .ifPresentOrElse(
                        session -> session.setLastSeen(now),
                        () -> sessions.add(new ActiveSession(sid, now)));
    }

    private List<ActiveSession> freshSessions(User user) {
        LocalDateTime now = LocalDateTime.now();
        return user.getActiveSessions().stream()
                .filter(session -> isFresh(session, now))
                .toList();
    }

    private boolean isFresh(ActiveSession session, LocalDateTime now) {
        return session.getLastSeen() != null
                && session.getLastSeen().isAfter(now.minusSeconds(staleAfterSeconds));
    }
}

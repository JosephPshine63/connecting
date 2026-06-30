package dev.pioruocco.wacchat.user;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class SessionGuard {

    private final long staleAfterSeconds;

    public SessionGuard(@Value("${application.session.stale-after-seconds}") long staleAfterSeconds) {
        this.staleAfterSeconds = staleAfterSeconds;
    }

    /**
     * True when {@code sid} belongs to a different session than the user's recorded
     * active session, and that active session is still recent enough to be considered alive.
     */
    public boolean isConflicting(User user, String sid) {
        String activeSessionId = user.getActiveSessionId();
        if (activeSessionId == null || activeSessionId.equals(sid)) {
            return false;
        }
        LocalDateTime lastSeen = user.getLastSeen();
        if (lastSeen == null) {
            return false;
        }
        return lastSeen.isAfter(LocalDateTime.now().minusSeconds(staleAfterSeconds));
    }
}

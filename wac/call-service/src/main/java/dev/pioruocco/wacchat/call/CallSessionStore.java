package dev.pioruocco.wacchat.call;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class CallSessionStore {

    private final Map<String, CallSession> sessions = new ConcurrentHashMap<>();

    public CallSession create(String chatId, String callerId, String calleeId, String callType) {
        CallSession session = new CallSession(chatId, callerId, calleeId, callType);
        sessions.put(chatId, session);
        return session;
    }

    public Optional<CallSession> get(String chatId) {
        return Optional.ofNullable(sessions.get(chatId));
    }

    public void remove(String chatId) {
        sessions.remove(chatId);
    }

    public Collection<CallSession> allSessions() {
        return sessions.values();
    }

    // Atomically transitions RINGING -> IN_CALL, guarding against a concurrent
    // sweepRingTimeouts() removing/timing-out the same session mid-answer.
    public boolean markAnsweredIfRinging(String chatId, Instant answeredAt) {
        AtomicBoolean transitioned = new AtomicBoolean(false);
        sessions.computeIfPresent(chatId, (id, session) -> {
            if (session.getState() == CallSessionState.RINGING) {
                session.setState(CallSessionState.IN_CALL);
                session.setAnsweredAt(answeredAt);
                transitioned.set(true);
            }
            return session;
        });
        return transitioned.get();
    }

    // Atomically checks-and-removes a still-RINGING, timed-out session, guarding
    // against a concurrent answer() that already transitioned it to IN_CALL.
    public Optional<CallSession> removeIfStillRingingPast(String chatId, Instant cutoff) {
        AtomicReference<CallSession> removed = new AtomicReference<>();
        sessions.computeIfPresent(chatId, (id, session) -> {
            if (session.getState() == CallSessionState.RINGING && session.getRingingSince().isBefore(cutoff)) {
                removed.set(session);
                return null;
            }
            return session;
        });
        return Optional.ofNullable(removed.get());
    }
}

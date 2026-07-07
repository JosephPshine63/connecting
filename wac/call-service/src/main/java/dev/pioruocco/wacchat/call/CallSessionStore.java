package dev.pioruocco.wacchat.call;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class CallSessionStore {

    private final Map<String, CallSession> sessions = new ConcurrentHashMap<>();

    // Empty if a call is already active on this chatId — callers must not silently
    // overwrite an in-flight session (this is the 409-on-duplicate-invite guard).
    public Optional<CallSession> createIfAbsent(String chatId, String callerId, String callType, List<String> inviteeIds) {
        CallSession session = new CallSession(chatId, callerId, callType, inviteeIds);
        CallSession existing = sessions.putIfAbsent(chatId, session);
        return existing == null ? Optional.of(session) : Optional.empty();
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
}

package dev.pioruocco.wacchat.call;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

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
}

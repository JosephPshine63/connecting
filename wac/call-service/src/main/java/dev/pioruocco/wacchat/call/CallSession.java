package dev.pioruocco.wacchat.call;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Ephemeral, in-memory only — call-service owns no database. If the process restarts,
 * in-flight calls are lost (acceptable for v1, see the roadmap plan).
 */
@Getter
public class CallSession {

    private final String chatId;
    private final String callerId;
    private final String calleeId;
    private final String callType;
    private final Instant ringingSince;
    @Setter
    private CallSessionState state;
    @Setter
    private Instant answeredAt;

    public CallSession(String chatId, String callerId, String calleeId, String callType) {
        this.chatId = chatId;
        this.callerId = callerId;
        this.calleeId = calleeId;
        this.callType = callType;
        this.state = CallSessionState.RINGING;
        this.ringingSince = Instant.now();
    }

    public String peerOf(String userId) {
        return callerId.equals(userId) ? calleeId : callerId;
    }
}

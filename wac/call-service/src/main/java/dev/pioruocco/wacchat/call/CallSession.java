package dev.pioruocco.wacchat.call;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Ephemeral, in-memory only — call-service owns no database. If the process restarts,
 * in-flight calls are lost (acceptable for v1, see the roadmap plan).
 *
 * <p>Mesh call session: every participant (caller included) is tracked individually so a
 * group call survives one participant leaving/declining/timing out without affecting the
 * rest. A 1:1 call is simply the degenerate case of a session with exactly 2 participants.
 */
public class CallSession {

    @Getter
    private final String chatId;
    @Getter
    private final String callerId;
    @Getter
    private final String callType;
    private final Map<String, Participant> participants = new ConcurrentHashMap<>();

    public CallSession(String chatId, String callerId, String callType, List<String> inviteeIds) {
        this.chatId = chatId;
        this.callerId = callerId;
        this.callType = callType;
        Instant now = Instant.now();
        participants.put(callerId, new Participant(callerId, ParticipantState.JOINED, now, now));
        for (String inviteeId : inviteeIds) {
            participants.put(inviteeId, new Participant(inviteeId, ParticipantState.RINGING, now, null));
        }
    }

    public boolean isGroupCall() {
        return participants.size() > 2;
    }

    public boolean isParticipant(String userId) {
        return participants.containsKey(userId);
    }

    public boolean isActiveParticipant(String userId) {
        ParticipantState state = stateOf(userId);
        return state == ParticipantState.JOINED || state == ParticipantState.RINGING;
    }

    public ParticipantState stateOf(String userId) {
        Participant participant = participants.get(userId);
        return participant == null ? null : participant.getState();
    }

    public Instant joinedAtOf(String userId) {
        Participant participant = participants.get(userId);
        return participant == null ? null : participant.getJoinedAt();
    }

    public Set<String> ringingParticipantIds() {
        return idsInState(ParticipantState.RINGING);
    }

    public Set<String> joinedParticipantIds() {
        return idsInState(ParticipantState.JOINED);
    }

    public Set<String> activeParticipantIds() {
        return participants.values().stream()
                .filter(p -> p.getState() == ParticipantState.JOINED || p.getState() == ParticipantState.RINGING)
                .map(Participant::getUserId)
                .collect(Collectors.toSet());
    }

    public Set<String> allParticipantIds() {
        return Set.copyOf(participants.keySet());
    }

    // Null if no invitee ever joined (the call never actually connected) — used to
    // distinguish a "missed" summary message from a "duration mm:ss" one.
    public Instant earliestInviteeJoinAt() {
        return participants.entrySet().stream()
                .filter(e -> !e.getKey().equals(callerId))
                .map(e -> e.getValue().getJoinedAt())
                .filter(Objects::nonNull)
                .min(Instant::compareTo)
                .orElse(null);
    }

    public int everJoinedCount() {
        return (int) participants.values().stream().filter(p -> p.getJoinedAt() != null).count();
    }

    private Set<String> idsInState(ParticipantState state) {
        return participants.values().stream()
                .filter(p -> p.getState() == state)
                .map(Participant::getUserId)
                .collect(Collectors.toSet());
    }

    // Atomically transitions RINGING -> JOINED, guarding against a concurrent
    // sweepRingTimeouts() marking the same participant MISSED mid-answer.
    public boolean markJoinedIfRinging(String userId, Instant joinedAt) {
        AtomicBoolean transitioned = new AtomicBoolean(false);
        participants.computeIfPresent(userId, (id, participant) -> {
            if (participant.getState() == ParticipantState.RINGING) {
                participant.setState(ParticipantState.JOINED);
                participant.setJoinedAt(joinedAt);
                transitioned.set(true);
            }
            return participant;
        });
        return transitioned.get();
    }

    // Atomically checks-and-marks a still-RINGING, timed-out participant, guarding
    // against a concurrent answer() that already transitioned them to JOINED.
    public boolean markMissedIfRingingPast(String userId, Instant cutoff) {
        AtomicBoolean transitioned = new AtomicBoolean(false);
        participants.computeIfPresent(userId, (id, participant) -> {
            if (participant.getState() == ParticipantState.RINGING && participant.getInvitedAt().isBefore(cutoff)) {
                participant.setState(ParticipantState.MISSED);
                transitioned.set(true);
            }
            return participant;
        });
        return transitioned.get();
    }

    public void leave(String userId, ParticipantState endState) {
        participants.computeIfPresent(userId, (id, participant) -> {
            participant.setState(endState);
            return participant;
        });
    }

    @Getter
    @Setter
    private static final class Participant {
        private final String userId;
        private ParticipantState state;
        private final Instant invitedAt;
        private Instant joinedAt;

        Participant(String userId, ParticipantState state, Instant invitedAt, Instant joinedAt) {
            this.userId = userId;
            this.state = state;
            this.invitedAt = invitedAt;
            this.joinedAt = joinedAt;
        }
    }
}

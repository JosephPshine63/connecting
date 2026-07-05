package dev.pioruocco.wacchat.call;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CallService {

    private final CallSessionStore sessionStore;
    private final ChatValidationClient chatValidationClient;
    private final InternalMessageClient internalMessageClient;
    private final RabbitTemplate rabbitTemplate;

    @Value("${application.call.exchange}")
    private String exchangeName;

    @Value("${application.call.routing-key}")
    private String routingKey;

    @Value("${application.call.ring-timeout-seconds}")
    private long ringTimeoutSeconds;

    public void invite(String chatId, String callerId, String calleeId, String callType, String sdpOffer) {
        if (!chatValidationClient.isAccepted(callerId, calleeId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No accepted chat between these users");
        }
        sessionStore.create(chatId, callerId, calleeId, callType);
        publish(calleeId, new CallSignal(chatId, callerId, CallSignalType.INVITE, callType, sdpOffer, null, null, null));
    }

    public void answer(String chatId, String calleeId, String sdpAnswer) {
        CallSession session = requireSession(chatId);
        session.setState(CallSessionState.IN_CALL);
        session.setAnsweredAt(Instant.now());
        publish(session.peerOf(calleeId), new CallSignal(chatId, calleeId, CallSignalType.ANSWER, session.getCallType(), sdpAnswer, null, null, null));
    }

    public void iceCandidate(String chatId, String fromUserId, String candidate, String sdpMid, Integer sdpMLineIndex) {
        CallSession session = requireSession(chatId);
        publish(session.peerOf(fromUserId),
                new CallSignal(chatId, fromUserId, CallSignalType.ICE_CANDIDATE, session.getCallType(), null, candidate, sdpMid, sdpMLineIndex));
    }

    public void end(String chatId, String userId, String reason) {
        CallSession session = sessionStore.get(chatId).orElse(null);
        if (session == null) {
            return;
        }
        sessionStore.remove(chatId);
        CallSignalType signalType = "REJECT".equals(reason) ? CallSignalType.REJECT : CallSignalType.END;
        publish(session.peerOf(userId), new CallSignal(chatId, userId, signalType, session.getCallType(), null, null, null, null));
        leaveSystemMessage(session);
    }

    @Scheduled(fixedDelay = 5000)
    void sweepRingTimeouts() {
        Instant cutoff = Instant.now().minus(Duration.ofSeconds(ringTimeoutSeconds));
        for (CallSession session : List.copyOf(sessionStore.allSessions())) {
            if (session.getState() == CallSessionState.RINGING && session.getRingingSince().isBefore(cutoff)) {
                sessionStore.remove(session.getChatId());
                log.info("Ring timeout for chat {}, marking MISSED", session.getChatId());
                CallSignal missed = new CallSignal(session.getChatId(), session.getCallerId(), CallSignalType.MISSED,
                        session.getCallType(), null, null, null, null);
                publish(session.getCallerId(), missed);
                publish(session.getCalleeId(), missed);
                leaveSystemMessage(session);
            }
        }
    }

    private void leaveSystemMessage(CallSession session) {
        String content;
        if (session.getState() == CallSessionState.IN_CALL && session.getAnsweredAt() != null) {
            Duration duration = Duration.between(session.getAnsweredAt(), Instant.now());
            content = "📞 Chiamata terminata - durata " + formatDuration(duration);
        } else {
            content = "📞 Chiamata persa";
        }
        internalMessageClient.sendSystemMessage(session.getChatId(), session.getCallerId(), session.getCalleeId(), content);
    }

    private static String formatDuration(Duration duration) {
        long totalSeconds = Math.max(0, duration.getSeconds());
        return String.format("%02d:%02d", totalSeconds / 60, totalSeconds % 60);
    }

    private CallSession requireSession(String chatId) {
        return sessionStore.get(chatId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No active call for this chat"));
    }

    private void publish(String toUserId, CallSignal signal) {
        rabbitTemplate.convertAndSend(exchangeName, routingKey, new CallSignalEvent(toUserId, signal));
    }
}

package dev.pioruocco.wacchat.call;

import java.io.Serializable;

/**
 * Payload delivered to the frontend over /user/queue/call. INVITE carries the SDP offer
 * (no separate OFFER type — a 1:1 call has no "ringing without an offer" step).
 *
 * fromUserName is caller-self-asserted (call-service is deliberately DB-free and never
 * looks up display names — see CallController's own doc comment on identity always coming
 * from the JWT), supplied by the frontend on /invite and only populated on INVITE signals.
 * It exists so notification-service can put the caller's name in a push notification
 * title; worst case of spoofing it is a misleading display string, not a security issue.
 */
public record CallSignal(
        String chatId,
        String fromUserId,
        CallSignalType type,
        String callType,
        String sdp,
        String candidate,
        String candidateSdpMid,
        Integer candidateSdpMLineIndex,
        String fromUserName
) implements Serializable {
}

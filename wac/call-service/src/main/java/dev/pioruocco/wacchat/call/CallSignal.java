package dev.pioruocco.wacchat.call;

import java.io.Serializable;

/**
 * Payload delivered to the frontend over /user/queue/call. INVITE carries the SDP offer
 * (no separate OFFER type — a 1:1 call has no "ringing without an offer" step).
 */
public record CallSignal(
        String chatId,
        String fromUserId,
        CallSignalType type,
        String callType,
        String sdp,
        String candidate,
        String candidateSdpMid,
        Integer candidateSdpMLineIndex
) implements Serializable {
}

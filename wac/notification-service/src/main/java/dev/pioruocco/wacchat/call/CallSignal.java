package dev.pioruocco.wacchat.call;

import java.io.Serializable;

/**
 * Payload delivered to the frontend over /user/queue/call. Duplicated verbatim (same
 * FQCN) from call-service — see CallSignalEvent for why.
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

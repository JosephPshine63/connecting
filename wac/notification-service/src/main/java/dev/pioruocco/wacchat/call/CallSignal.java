package dev.pioruocco.wacchat.call;

import java.io.Serializable;

/**
 * Payload delivered to the frontend over /user/queue/call. Duplicated verbatim (same
 * FQCN) from call-service — see CallSignalEvent for why.
 *
 * fromUserName is caller-self-asserted (see call-service's copy of this record for why)
 * and only populated on INVITE signals; CallSignalListener uses it to personalize the
 * push notification title for an incoming call.
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

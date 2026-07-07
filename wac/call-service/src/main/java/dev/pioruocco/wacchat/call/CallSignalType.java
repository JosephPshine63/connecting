package dev.pioruocco.wacchat.call;

public enum CallSignalType {

    INVITE,
    ANSWER,
    ICE_CANDIDATE,
    END,
    REJECT,
    BUSY,
    MISSED,
    PARTICIPANT_JOINED,
    PEER_OFFER,
    PEER_ANSWER,
}

package dev.pioruocco.wacchat.call;

import java.io.Serializable;

/**
 * One invitee's individual SDP offer within an invite request. The caller opens a direct
 * RTCPeerConnection to each invitee up front (mesh topology), so each gets its own offer —
 * a 1:1 call is simply the size==1 case of the invitee list.
 */
public record InviteeOffer(String peerId, String sdpOffer) implements Serializable {
}

package dev.pioruocco.wacchat.call;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST signaling intake for WebRTC calls (mesh topology). Identity (caller/participant)
 * always comes from the JWT (Authentication), never from the request body — the body only
 * carries peer id(s) and the SDP/ICE payload. A 1:1 call is the degenerate case of a
 * single-invitee /invite; peer-offer/peer-answer only come into play once a third
 * participant joins an already-running call (mesh bootstrap).
 */
@RestController
@RequestMapping("/api/v1/calls")
@RequiredArgsConstructor
public class CallController {

    private final CallService callService;

    @PostMapping("/{chatId}/invite")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void invite(@PathVariable String chatId, @RequestBody InviteRequest request, Authentication authentication) {
        callService.invite(chatId, authentication.getName(), request.fromUserName(), request.invitees(), request.callType());
    }

    @PostMapping("/{chatId}/answer")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void answer(@PathVariable String chatId, @RequestBody AnswerRequest request, Authentication authentication) {
        callService.answer(chatId, authentication.getName(), request.sdpAnswer());
    }

    @PostMapping("/{chatId}/ice-candidate")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void iceCandidate(@PathVariable String chatId, @RequestBody IceCandidateRequest request, Authentication authentication) {
        callService.iceCandidate(chatId, authentication.getName(), request.peerId(), request.candidate(), request.sdpMid(), request.sdpMLineIndex());
    }

    @PostMapping("/{chatId}/peer-offer")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void peerOffer(@PathVariable String chatId, @RequestBody PeerOfferRequest request, Authentication authentication) {
        callService.peerOffer(chatId, authentication.getName(), request.peerId(), request.sdpOffer());
    }

    @PostMapping("/{chatId}/peer-answer")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void peerAnswer(@PathVariable String chatId, @RequestBody PeerAnswerRequest request, Authentication authentication) {
        callService.peerAnswer(chatId, authentication.getName(), request.peerId(), request.sdpAnswer());
    }

    @PostMapping("/{chatId}/end")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void end(@PathVariable String chatId, @RequestBody EndRequest request, Authentication authentication) {
        callService.end(chatId, authentication.getName(), request.reason());
    }

    public record InviteRequest(List<InviteeOffer> invitees, String callType, String fromUserName) {
    }

    public record AnswerRequest(String sdpAnswer) {
    }

    public record IceCandidateRequest(String peerId, String candidate, String sdpMid, Integer sdpMLineIndex) {
    }

    public record PeerOfferRequest(String peerId, String sdpOffer) {
    }

    public record PeerAnswerRequest(String peerId, String sdpAnswer) {
    }

    public record EndRequest(String reason) {
    }
}

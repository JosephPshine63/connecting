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

/**
 * REST signaling intake for WebRTC calls. Identity (caller/callee) always comes from the
 * JWT (Authentication), never from the request body — the body only carries the peer id
 * on /invite (the frontend already knows it from the open chat) and the SDP/ICE payload.
 */
@RestController
@RequestMapping("/api/v1/calls")
@RequiredArgsConstructor
public class CallController {

    private final CallService callService;

    @PostMapping("/{chatId}/invite")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void invite(@PathVariable String chatId, @RequestBody InviteRequest request, Authentication authentication) {
        callService.invite(chatId, authentication.getName(), request.peerId(), request.callType(), request.sdpOffer());
    }

    @PostMapping("/{chatId}/answer")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void answer(@PathVariable String chatId, @RequestBody AnswerRequest request, Authentication authentication) {
        callService.answer(chatId, authentication.getName(), request.sdpAnswer());
    }

    @PostMapping("/{chatId}/ice-candidate")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void iceCandidate(@PathVariable String chatId, @RequestBody IceCandidateRequest request, Authentication authentication) {
        callService.iceCandidate(chatId, authentication.getName(), request.candidate(), request.sdpMid(), request.sdpMLineIndex());
    }

    @PostMapping("/{chatId}/end")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void end(@PathVariable String chatId, @RequestBody EndRequest request, Authentication authentication) {
        callService.end(chatId, authentication.getName(), request.reason());
    }

    public record InviteRequest(String peerId, String callType, String sdpOffer) {
    }

    public record AnswerRequest(String sdpAnswer) {
    }

    public record IceCandidateRequest(String candidate, String sdpMid, Integer sdpMLineIndex) {
    }

    public record EndRequest(String reason) {
    }
}

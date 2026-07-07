package dev.pioruocco.wacchat.chat;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Called by call-service before allowing a call invite/answer, to check that the two
 * users share an ACCEPTED DIRECT chat. Unlike SessionValidationController's fail-open
 * session lock (a UX nicety), this is a real security boundary — the caller
 * (ChatValidationClient in call-service) must fail closed (deny) if this endpoint is
 * unreachable. Guarded by InternalAuthFilter, not JWT.
 *
 * Validates by chatId (not just the (userId, peerId) pair) so a GROUP chat — or any other
 * ACCEPTED 1:1 chat the same two users happen to share — can never be mistaken for the
 * chat the call is actually being placed on. GROUP chats always fail closed on this
 * endpoint: group calls are validated separately by {@link #validateGroupCall}.
 */
@RestController
@RequestMapping("/api/v1/internal/chats")
@RequiredArgsConstructor
public class ChatValidationController {

    private final ChatRepository chatRepository;
    private final ChatMemberRepository chatMemberRepository;

    @PostMapping("/validate")
    public ChatValidationResponse validate(@RequestBody ChatValidationRequest request) {
        boolean accepted = chatRepository.findById(request.chatId())
                .filter(chat -> chat.getType() == ChatType.DIRECT)
                .filter(chat -> Set.of(chat.getSender().getId(), chat.getRecipient().getId())
                        .equals(Set.of(request.userId(), request.peerId())))
                .map(chat -> chat.getStatus() == ChatStatus.ACCEPTED)
                .orElse(false);
        return new ChatValidationResponse(accepted);
    }

    /**
     * Validates that the caller and every invitee are current members of a GROUP chat.
     * Group calls have no ACCEPTED/PENDING notion (groups are always ACCEPTED) or blocking
     * check (blocking doesn't apply to groups, per the group-chat feature) — membership is
     * the only boundary that matters here.
     */
    @PostMapping("/validate-group")
    public GroupCallValidationResponse validateGroupCall(@RequestBody GroupCallValidationRequest request) {
        boolean accepted = chatRepository.findById(request.chatId())
                .filter(chat -> chat.getType() == ChatType.GROUP)
                .map(chat -> {
                    Set<String> memberIds = chatMemberRepository.findByChatId(chat.getId()).stream()
                            .map(ChatMember::getUserId)
                            .collect(Collectors.toSet());
                    return memberIds.contains(request.callerId()) && memberIds.containsAll(request.inviteeIds());
                })
                .orElse(false);
        return new GroupCallValidationResponse(accepted);
    }

    public record ChatValidationRequest(String chatId, String userId, String peerId) {
    }

    public record ChatValidationResponse(boolean accepted) {
    }

    public record GroupCallValidationRequest(String chatId, String callerId, List<String> inviteeIds) {
    }

    public record GroupCallValidationResponse(boolean accepted) {
    }
}

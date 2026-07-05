package dev.pioruocco.wacchat.moderation;

import dev.pioruocco.wacchat.chat.ChatRepository;
import dev.pioruocco.wacchat.chat.ChatStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ModerationService {

    private final BlockedUserRepository blockedUserRepository;
    private final UserReportRepository userReportRepository;
    private final ChatRepository chatRepository;

    @Transactional
    public void blockUser(String blockerId, String blockedId) {
        if (!blockedUserRepository.existsByBlockerIdAndBlockedId(blockerId, blockedId)) {
            BlockedUser blockedUser = new BlockedUser();
            blockedUser.setBlockerId(blockerId);
            blockedUser.setBlockedId(blockedId);
            blockedUserRepository.save(blockedUser);
        }
        autoRejectPendingChat(blockerId, blockedId);
    }

    @Transactional
    public void unblockUser(String blockerId, String blockedId) {
        blockedUserRepository.deleteByBlockerIdAndBlockedId(blockerId, blockedId);
        // Unblocking only lifts the send-message guard — it must not silently restore an
        // ACCEPTED chat. Demoting it to REJECTED forces the pair through a fresh
        // request/accept cycle (via ChatService.getOrCreateChat's revival path) before they
        // can talk again.
        chatRepository.findChatByReceiverAndSender(blockerId, blockedId)
                .filter(chat -> chat.getStatus() == ChatStatus.ACCEPTED)
                .ifPresent(chat -> {
                    chat.setStatus(ChatStatus.REJECTED);
                    chatRepository.save(chat);
                });
    }

    public List<BlockedUser> getBlockedUsers(String blockerId) {
        return blockedUserRepository.findByBlockerId(blockerId);
    }

    public boolean isBlocked(String userIdA, String userIdB) {
        return blockedUserRepository.existsBlockBetween(userIdA, userIdB);
    }

    public boolean isBlockedByMe(String blockerId, String blockedId) {
        return blockedUserRepository.existsByBlockerIdAndBlockedId(blockerId, blockedId);
    }

    public List<String> getBlockedIds(String blockerId) {
        return blockedUserRepository.findBlockedIdsByBlockerId(blockerId);
    }

    public void reportUser(String reporterId, String reportedId, ReportReason reason, String details) {
        UserReport report = new UserReport();
        report.setReporterId(reporterId);
        report.setReportedId(reportedId);
        report.setReason(reason);
        report.setDetails(details);
        report.setStatus(ReportStatus.OPEN);
        userReportRepository.save(report);
    }

    // Auto-rejecting here (rather than through ChatService.rejectChat) is deliberate: that
    // path also sends a CHAT_REQUEST_REJECTED notification to the requester, which would leak
    // the block to the blocked user — blocking is meant to be silent (WhatsApp-style).
    private void autoRejectPendingChat(String blockerId, String blockedId) {
        chatRepository.findChatByReceiverAndSender(blockerId, blockedId)
                .filter(chat -> chat.getStatus() == ChatStatus.PENDING)
                .ifPresent(chat -> {
                    chat.setStatus(ChatStatus.REJECTED);
                    chatRepository.save(chat);
                });
    }
}

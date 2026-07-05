package dev.pioruocco.wacchat.moderation;

import dev.pioruocco.wacchat.chat.Chat;
import dev.pioruocco.wacchat.chat.ChatRepository;
import dev.pioruocco.wacchat.chat.ChatStatus;
import dev.pioruocco.wacchat.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ModerationServiceTest {

    private static final String BLOCKER_ID = "blocker-1";
    private static final String BLOCKED_ID = "blocked-1";

    @Mock
    private BlockedUserRepository blockedUserRepository;
    @Mock
    private UserReportRepository userReportRepository;
    @Mock
    private ChatRepository chatRepository;

    private ModerationService moderationService;

    @BeforeEach
    void setUp() {
        moderationService = new ModerationService(blockedUserRepository, userReportRepository, chatRepository);
    }

    @Test
    void blockUser_newPair_savesBlockedUser() {
        when(blockedUserRepository.existsByBlockerIdAndBlockedId(BLOCKER_ID, BLOCKED_ID)).thenReturn(false);
        when(chatRepository.findChatByReceiverAndSender(BLOCKER_ID, BLOCKED_ID)).thenReturn(Optional.empty());

        moderationService.blockUser(BLOCKER_ID, BLOCKED_ID);

        verify(blockedUserRepository).save(any(BlockedUser.class));
    }

    @Test
    void blockUser_alreadyBlocked_isIdempotentAndDoesNotSaveAgain() {
        when(blockedUserRepository.existsByBlockerIdAndBlockedId(BLOCKER_ID, BLOCKED_ID)).thenReturn(true);
        when(chatRepository.findChatByReceiverAndSender(BLOCKER_ID, BLOCKED_ID)).thenReturn(Optional.empty());

        moderationService.blockUser(BLOCKER_ID, BLOCKED_ID);

        verify(blockedUserRepository, never()).save(any());
    }

    @Test
    void blockUser_withPendingChat_autoRejectsIt() {
        when(blockedUserRepository.existsByBlockerIdAndBlockedId(BLOCKER_ID, BLOCKED_ID)).thenReturn(false);
        Chat chat = chat(ChatStatus.PENDING);
        when(chatRepository.findChatByReceiverAndSender(BLOCKER_ID, BLOCKED_ID)).thenReturn(Optional.of(chat));

        moderationService.blockUser(BLOCKER_ID, BLOCKED_ID);

        assertThat(chat.getStatus()).isEqualTo(ChatStatus.REJECTED);
        verify(chatRepository).save(chat);
    }

    @Test
    void blockUser_withAcceptedChat_doesNotTouchChatStatus() {
        when(blockedUserRepository.existsByBlockerIdAndBlockedId(BLOCKER_ID, BLOCKED_ID)).thenReturn(false);
        Chat chat = chat(ChatStatus.ACCEPTED);
        when(chatRepository.findChatByReceiverAndSender(BLOCKER_ID, BLOCKED_ID)).thenReturn(Optional.of(chat));

        moderationService.blockUser(BLOCKER_ID, BLOCKED_ID);

        assertThat(chat.getStatus()).isEqualTo(ChatStatus.ACCEPTED);
        verify(chatRepository, never()).save(any());
    }

    @Test
    void unblockUser_withAcceptedChat_demotesToRejectedForcingReRequest() {
        Chat chat = chat(ChatStatus.ACCEPTED);
        when(chatRepository.findChatByReceiverAndSender(BLOCKER_ID, BLOCKED_ID)).thenReturn(Optional.of(chat));

        moderationService.unblockUser(BLOCKER_ID, BLOCKED_ID);

        verify(blockedUserRepository).deleteByBlockerIdAndBlockedId(BLOCKER_ID, BLOCKED_ID);
        assertThat(chat.getStatus()).isEqualTo(ChatStatus.REJECTED);
        verify(chatRepository).save(chat);
    }

    @Test
    void unblockUser_withAlreadyRejectedChat_leavesItUntouched() {
        Chat chat = chat(ChatStatus.REJECTED);
        when(chatRepository.findChatByReceiverAndSender(BLOCKER_ID, BLOCKED_ID)).thenReturn(Optional.of(chat));

        moderationService.unblockUser(BLOCKER_ID, BLOCKED_ID);

        verify(chatRepository, never()).save(any());
    }

    @Test
    void unblockUser_noExistingChat_onlyDeletesBlockRow() {
        when(chatRepository.findChatByReceiverAndSender(BLOCKER_ID, BLOCKED_ID)).thenReturn(Optional.empty());

        moderationService.unblockUser(BLOCKER_ID, BLOCKED_ID);

        verify(blockedUserRepository).deleteByBlockerIdAndBlockedId(BLOCKER_ID, BLOCKED_ID);
        verify(chatRepository, never()).save(any());
    }

    @Test
    void isBlocked_delegatesToBidirectionalCheck() {
        when(blockedUserRepository.existsBlockBetween(BLOCKER_ID, BLOCKED_ID)).thenReturn(true);

        assertThat(moderationService.isBlocked(BLOCKER_ID, BLOCKED_ID)).isTrue();
    }

    @Test
    void isBlockedByMe_delegatesToDirectionalExistsCheck() {
        when(blockedUserRepository.existsByBlockerIdAndBlockedId(BLOCKER_ID, BLOCKED_ID)).thenReturn(true);

        assertThat(moderationService.isBlockedByMe(BLOCKER_ID, BLOCKED_ID)).isTrue();
    }

    @Test
    void isBlockedByMe_returnsFalseWhenOnlyReverseDirectionIsBlocked() {
        when(blockedUserRepository.existsByBlockerIdAndBlockedId(BLOCKER_ID, BLOCKED_ID)).thenReturn(false);

        assertThat(moderationService.isBlockedByMe(BLOCKER_ID, BLOCKED_ID)).isFalse();
    }

    private static Chat chat(ChatStatus status) {
        Chat chat = new Chat();
        chat.setId("chat-1");
        chat.setSender(user(BLOCKER_ID));
        chat.setRecipient(user(BLOCKED_ID));
        chat.setStatus(status);
        return chat;
    }

    private static User user(String id) {
        User user = new User();
        user.setId(id);
        return user;
    }
}

package dev.pioruocco.wacchat.chat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatValidationControllerTest {

    private static final String CHAT_ID = "chat-1";
    private static final String USER_ID = "user-1";
    private static final String PEER_ID = "peer-1";

    @Mock
    private ChatRepository chatRepository;

    @Mock
    private ChatMemberRepository chatMemberRepository;

    private ChatValidationController controller;

    @BeforeEach
    void setUp() {
        controller = new ChatValidationController(chatRepository, chatMemberRepository);
    }

    @Test
    void validate_acceptedDirectChat_returnsAccepted() {
        when(chatRepository.findById(CHAT_ID)).thenReturn(Optional.of(directChat(ChatStatus.ACCEPTED)));

        ChatValidationController.ChatValidationResponse response =
                controller.validate(new ChatValidationController.ChatValidationRequest(CHAT_ID, USER_ID, PEER_ID));

        assertThat(response.accepted()).isTrue();
    }

    @Test
    void validate_pendingDirectChat_returnsNotAccepted() {
        when(chatRepository.findById(CHAT_ID)).thenReturn(Optional.of(directChat(ChatStatus.PENDING)));

        ChatValidationController.ChatValidationResponse response =
                controller.validate(new ChatValidationController.ChatValidationRequest(CHAT_ID, USER_ID, PEER_ID));

        assertThat(response.accepted()).isFalse();
    }

    @Test
    void validate_noChatExists_returnsNotAccepted() {
        when(chatRepository.findById(CHAT_ID)).thenReturn(Optional.empty());

        ChatValidationController.ChatValidationResponse response =
                controller.validate(new ChatValidationController.ChatValidationRequest(CHAT_ID, USER_ID, PEER_ID));

        assertThat(response.accepted()).isFalse();
    }

    @Test
    void validate_groupChatBetweenSameTwoUsers_returnsNotAccepted() {
        Chat group = new Chat();
        group.setId(CHAT_ID);
        group.setType(ChatType.GROUP);
        group.setStatus(ChatStatus.ACCEPTED);
        when(chatRepository.findById(CHAT_ID)).thenReturn(Optional.of(group));

        ChatValidationController.ChatValidationResponse response =
                controller.validate(new ChatValidationController.ChatValidationRequest(CHAT_ID, USER_ID, PEER_ID));

        assertThat(response.accepted()).isFalse();
    }

    @Test
    void validate_chatIdBelongsToDifferentPair_returnsNotAccepted() {
        when(chatRepository.findById(CHAT_ID)).thenReturn(Optional.of(directChat(ChatStatus.ACCEPTED)));

        ChatValidationController.ChatValidationResponse response =
                controller.validate(new ChatValidationController.ChatValidationRequest(CHAT_ID, USER_ID, "someone-else"));

        assertThat(response.accepted()).isFalse();
    }

    @Test
    void validateGroupCall_callerAndAllInviteesAreMembers_returnsAccepted() {
        when(chatRepository.findById(CHAT_ID)).thenReturn(Optional.of(groupChat()));
        when(chatMemberRepository.findByChatId(CHAT_ID)).thenReturn(List.of(
                member(USER_ID), member(PEER_ID), member("third-user")));

        ChatValidationController.GroupCallValidationResponse response = controller.validateGroupCall(
                new ChatValidationController.GroupCallValidationRequest(CHAT_ID, USER_ID, List.of(PEER_ID, "third-user")));

        assertThat(response.accepted()).isTrue();
    }

    @Test
    void validateGroupCall_callerNotAMember_returnsNotAccepted() {
        when(chatRepository.findById(CHAT_ID)).thenReturn(Optional.of(groupChat()));
        when(chatMemberRepository.findByChatId(CHAT_ID)).thenReturn(List.of(member(PEER_ID)));

        ChatValidationController.GroupCallValidationResponse response = controller.validateGroupCall(
                new ChatValidationController.GroupCallValidationRequest(CHAT_ID, USER_ID, List.of(PEER_ID)));

        assertThat(response.accepted()).isFalse();
    }

    @Test
    void validateGroupCall_inviteeNotAMember_returnsNotAccepted() {
        when(chatRepository.findById(CHAT_ID)).thenReturn(Optional.of(groupChat()));
        when(chatMemberRepository.findByChatId(CHAT_ID)).thenReturn(List.of(member(USER_ID)));

        ChatValidationController.GroupCallValidationResponse response = controller.validateGroupCall(
                new ChatValidationController.GroupCallValidationRequest(CHAT_ID, USER_ID, List.of(PEER_ID)));

        assertThat(response.accepted()).isFalse();
    }

    @Test
    void validateGroupCall_directChat_returnsNotAccepted() {
        when(chatRepository.findById(CHAT_ID)).thenReturn(Optional.of(directChat(ChatStatus.ACCEPTED)));

        ChatValidationController.GroupCallValidationResponse response = controller.validateGroupCall(
                new ChatValidationController.GroupCallValidationRequest(CHAT_ID, USER_ID, List.of(PEER_ID)));

        assertThat(response.accepted()).isFalse();
    }

    @Test
    void validateGroupCall_noChatExists_returnsNotAccepted() {
        when(chatRepository.findById(CHAT_ID)).thenReturn(Optional.empty());

        ChatValidationController.GroupCallValidationResponse response = controller.validateGroupCall(
                new ChatValidationController.GroupCallValidationRequest(CHAT_ID, USER_ID, List.of(PEER_ID)));

        assertThat(response.accepted()).isFalse();
    }

    private static Chat groupChat() {
        Chat chat = new Chat();
        chat.setId(CHAT_ID);
        chat.setType(ChatType.GROUP);
        chat.setStatus(ChatStatus.ACCEPTED);
        return chat;
    }

    private static ChatMember member(String userId) {
        ChatMember member = new ChatMember();
        member.setChatId(CHAT_ID);
        member.setUserId(userId);
        member.setRole(GroupMemberRole.MEMBER);
        return member;
    }

    private static Chat directChat(ChatStatus status) {
        Chat chat = new Chat();
        chat.setId(CHAT_ID);
        chat.setType(ChatType.DIRECT);
        chat.setSender(user(USER_ID));
        chat.setRecipient(user(PEER_ID));
        chat.setStatus(status);
        return chat;
    }

    private static dev.pioruocco.wacchat.user.User user(String id) {
        dev.pioruocco.wacchat.user.User user = new dev.pioruocco.wacchat.user.User();
        user.setId(id);
        return user;
    }
}

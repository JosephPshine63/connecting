package dev.pioruocco.wacchat.chat;

import dev.pioruocco.wacchat.user.User;
import dev.pioruocco.wacchat.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChatMapperTest {

    private static final String SENDER_ID = "sender-1";
    private static final String RECEIVER_ID = "receiver-1";
    private static final String ADMIN_ID = "admin-1";

    private ChatMapper mapperWithAdminUserId(String adminUserId) {
        ChatMapper mapper = new ChatMapper(mock(ChatMemberRepository.class), mock(UserRepository.class));
        ReflectionTestUtils.setField(mapper, "adminUserId", adminUserId);
        return mapper;
    }

    @Test
    void toChatResponse_otherParticipantIsAdmin_flagsIsAdminChatTrue() {
        ChatMapper mapper = mapperWithAdminUserId(ADMIN_ID);
        Chat chat = chat(SENDER_ID, ADMIN_ID);

        ChatResponse response = mapper.toChatResponse(chat, SENDER_ID);

        assertThat(response.isAdminChat()).isTrue();
    }

    @Test
    void toChatResponse_noOtherParticipantIsAdmin_flagsIsAdminChatFalse() {
        ChatMapper mapper = mapperWithAdminUserId(ADMIN_ID);
        Chat chat = chat(SENDER_ID, RECEIVER_ID);

        ChatResponse response = mapper.toChatResponse(chat, SENDER_ID);

        assertThat(response.isAdminChat()).isFalse();
    }

    @Test
    void toChatResponse_adminUserIdUnset_flagsIsAdminChatFalse() {
        ChatMapper mapper = mapperWithAdminUserId("");
        Chat chat = chat(SENDER_ID, RECEIVER_ID);

        ChatResponse response = mapper.toChatResponse(chat, SENDER_ID);

        assertThat(response.isAdminChat()).isFalse();
    }

    @Test
    void toChatResponse_groupChat_usesChatMemberStateNotSenderRecipient() {
        ChatMemberRepository chatMemberRepository = mock(ChatMemberRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        ChatMapper mapper = new ChatMapper(chatMemberRepository, userRepository);
        ReflectionTestUtils.setField(mapper, "adminUserId", "");

        Chat group = new Chat();
        group.setId("group-1");
        group.setType(ChatType.GROUP);
        group.setName("Trip");
        group.setStatus(ChatStatus.ACCEPTED);
        group.setMessages(List.of());

        ChatMember viewerMembership = new ChatMember();
        viewerMembership.setUserId(SENDER_ID);
        viewerMembership.setRole(GroupMemberRole.OWNER);
        viewerMembership.setFavorite(true);
        when(chatMemberRepository.findByChatId("group-1")).thenReturn(List.of(viewerMembership));
        when(userRepository.findByPublicId(SENDER_ID)).thenReturn(Optional.of(user(SENDER_ID)));

        ChatResponse response = mapper.toChatResponse(group, SENDER_ID);

        assertThat(response.getType()).isEqualTo(ChatType.GROUP);
        assertThat(response.getName()).isEqualTo("Trip");
        assertThat(response.isFavorite()).isTrue();
        assertThat(response.getMembers()).hasSize(1);
        assertThat(response.getMembers().get(0).getRole()).isEqualTo(GroupMemberRole.OWNER);
    }

    private static User user(String id) {
        User user = new User();
        user.setId(id);
        user.setFirstName("First-" + id);
        user.setLastName("Last-" + id);
        return user;
    }

    private static Chat chat(String senderId, String receiverId) {
        Chat chat = new Chat();
        chat.setId("chat-" + senderId + "-" + receiverId);
        chat.setSender(user(senderId));
        chat.setRecipient(user(receiverId));
        chat.setStatus(ChatStatus.ACCEPTED);
        chat.setPendingMessageCount(0);
        chat.setMessages(List.of());
        return chat;
    }
}

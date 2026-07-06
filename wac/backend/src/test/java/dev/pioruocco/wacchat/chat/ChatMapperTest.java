package dev.pioruocco.wacchat.chat;

import dev.pioruocco.wacchat.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChatMapperTest {

    private static final String SENDER_ID = "sender-1";
    private static final String RECEIVER_ID = "receiver-1";
    private static final String ADMIN_ID = "admin-1";

    private ChatMapper mapperWithAdminUserId(String adminUserId) {
        ChatMapper mapper = new ChatMapper();
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

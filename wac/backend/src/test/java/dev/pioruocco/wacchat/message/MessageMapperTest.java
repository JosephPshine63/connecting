package dev.pioruocco.wacchat.message;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MessageMapperTest {

    private final MessageMapper mapper = new MessageMapper();

    @Test
    void toMessageResponse_freshlyCreatedMessage_editedAtIsNullEvenThoughAuditingSetLastModifiedDate() {
        // Spring Data JPA auditing sets lastModifiedDate == createdDate in memory on creation,
        // even though the column is insertable=false and stays NULL in the DB row — the mapper
        // must not mistake that for a real edit.
        LocalDateTime now = LocalDateTime.now();
        Message message = new Message();
        message.setId(1L);
        message.setContent("hello");
        message.setType(MessageType.TEXT);
        message.setState(MessageState.SENT);
        message.setSenderId("sender-1");
        message.setReceiverId("receiver-1");
        message.setCreatedDate(now);
        message.setLastModifiedDate(now);

        MessageResponse response = mapper.toMessageResponse(message, "sender-1", List.of());

        assertThat(response.getEditedAt()).isNull();
    }

    @Test
    void toMessageResponse_trulyEditedMessage_setsEditedAt() {
        LocalDateTime created = LocalDateTime.now().minusMinutes(5);
        LocalDateTime edited = LocalDateTime.now();
        Message message = new Message();
        message.setId(1L);
        message.setContent("edited content");
        message.setType(MessageType.TEXT);
        message.setState(MessageState.SENT);
        message.setSenderId("sender-1");
        message.setReceiverId("receiver-1");
        message.setCreatedDate(created);
        message.setLastModifiedDate(edited);

        MessageResponse response = mapper.toMessageResponse(message, "sender-1", List.of());

        assertThat(response.getEditedAt()).isEqualTo(edited);
    }

    @Test
    void toMessageResponse_deletedMessage_returnsPlaceholderContentAndMediaWithoutClearingTheEntity() {
        Message message = new Message();
        message.setId(1L);
        message.setContent("secret content");
        message.setMediaFilePath("https://cdn.example.com/messages/x.jpg");
        message.setType(MessageType.IMAGE);
        message.setState(MessageState.SENT);
        message.setSenderId("sender-1");
        message.setReceiverId("receiver-1");
        message.setCreatedDate(LocalDateTime.now());
        message.setDeleted(true);

        MessageResponse response = mapper.toMessageResponse(message, "sender-1", List.of());

        assertThat(response.isDeleted()).isTrue();
        assertThat(response.getContent()).isNull();
        assertThat(response.getMedia()).isNull();
        // the entity itself must be untouched — deletion is a display-time flag, not data erasure
        assertThat(message.getContent()).isEqualTo("secret content");
        assertThat(message.getMediaFilePath()).isEqualTo("https://cdn.example.com/messages/x.jpg");
    }

    @Test
    void toMessageResponse_lastModifiedDateNull_editedAtIsNull() {
        Message message = new Message();
        message.setId(1L);
        message.setContent("hello");
        message.setType(MessageType.TEXT);
        message.setState(MessageState.SENT);
        message.setSenderId("sender-1");
        message.setReceiverId("receiver-1");
        message.setCreatedDate(LocalDateTime.now());
        message.setLastModifiedDate(null);

        MessageResponse response = mapper.toMessageResponse(message, "sender-1", List.of());

        assertThat(response.getEditedAt()).isNull();
    }

    @Test
    void toMessageResponse_noReactions_returnsEmptyList() {
        Message message = existingMessage();

        MessageResponse response = mapper.toMessageResponse(message, "viewer-1", List.of());

        assertThat(response.getReactions()).isEmpty();
    }

    @Test
    void toMessageResponse_aggregatesReactionsPerEmojiAndSetsReactedByMe() {
        Message message = existingMessage();
        List<MessageReaction> reactions = List.of(
                reaction("viewer-1", "👍"),
                reaction("other-user", "👍"),
                reaction("third-user", "❤️")
        );

        MessageResponse response = mapper.toMessageResponse(message, "viewer-1", reactions);

        assertThat(response.getReactions()).hasSize(2);
        ReactionSummaryResponse thumbsUp = response.getReactions().stream()
                .filter(r -> r.getEmoji().equals("👍")).findFirst().orElseThrow();
        assertThat(thumbsUp.getCount()).isEqualTo(2);
        assertThat(thumbsUp.isReactedByMe()).isTrue();

        ReactionSummaryResponse heart = response.getReactions().stream()
                .filter(r -> r.getEmoji().equals("❤️")).findFirst().orElseThrow();
        assertThat(heart.getCount()).isEqualTo(1);
        assertThat(heart.isReactedByMe()).isFalse();
    }

    private static Message existingMessage() {
        Message message = new Message();
        message.setId(1L);
        message.setContent("hello");
        message.setType(MessageType.TEXT);
        message.setState(MessageState.SENT);
        message.setSenderId("sender-1");
        message.setReceiverId("receiver-1");
        message.setCreatedDate(LocalDateTime.now());
        return message;
    }

    private static MessageReaction reaction(String userId, String emoji) {
        MessageReaction reaction = new MessageReaction();
        reaction.setMessageId(1L);
        reaction.setUserId(userId);
        reaction.setEmoji(emoji);
        return reaction;
    }
}

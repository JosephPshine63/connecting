package dev.pioruocco.wacchat.message;

import dev.pioruocco.wacchat.file.FileUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class MessageMapper {

    public MessageResponse toMessageResponse(Message message, String viewerId, List<MessageReaction> reactions, boolean starredByViewer) {
        boolean deleted = message.isDeleted();
        return MessageResponse.builder()
                .id(message.getId())
                .content(deleted ? null : message.getContent())
                .senderId(message.getSenderId())
                .receiverId(message.getReceiverId())
                .type(message.getType())
                .state(message.getState())
                .createdAt(message.getCreatedDate())
                .media(deleted ? null : FileUtils.resolveMedia(message.getMediaFilePath()))
                .replyToId(message.getReplyToId())
                .forwarded(message.isForwarded())
                .editedAt(hasBeenEdited(message) ? message.getLastModifiedDate() : null)
                .deleted(deleted)
                .reactions(aggregateReactions(reactions, viewerId))
                .starred(starredByViewer)
                .build();
    }

    // Spring Data JPA's auditing sets lastModifiedDate equal to createdDate in memory the
    // moment a new entity is created (before the insertable=false column exclusion even
    // applies to the INSERT statement) — so comparing against createdDate is required to
    // avoid every just-created message reporting itself as already edited.
    private boolean hasBeenEdited(Message message) {
        return message.getLastModifiedDate() != null && !message.getLastModifiedDate().equals(message.getCreatedDate());
    }

    private List<ReactionSummaryResponse> aggregateReactions(List<MessageReaction> reactions, String viewerId) {
        if (reactions == null || reactions.isEmpty()) {
            return List.of();
        }
        Map<String, List<MessageReaction>> byEmoji = reactions.stream()
                .collect(Collectors.groupingBy(MessageReaction::getEmoji));
        return byEmoji.entrySet().stream()
                .map(entry -> ReactionSummaryResponse.builder()
                        .emoji(entry.getKey())
                        .count(entry.getValue().size())
                        .reactedByMe(entry.getValue().stream().anyMatch(r -> r.getUserId().equals(viewerId)))
                        .build())
                .toList();
    }
}

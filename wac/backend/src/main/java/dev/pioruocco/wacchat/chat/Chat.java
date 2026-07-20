package dev.pioruocco.wacchat.chat;


import dev.pioruocco.wacchat.common.BaseAuditingEntity;
import dev.pioruocco.wacchat.message.Message;
import dev.pioruocco.wacchat.message.MessageState;
import dev.pioruocco.wacchat.message.MessageType;
import dev.pioruocco.wacchat.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

import static jakarta.persistence.GenerationType.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "chat")
@NamedQuery(name = ChatConstants.FIND_CHAT_BY_SENDER_ID,
            query = "SELECT DISTINCT c FROM Chat c WHERE c.sender.id = :senderId OR c.recipient.id = :senderId ORDER BY createdDate DESC"
)
@NamedQuery(name = ChatConstants.FIND_CHAT_BY_SENDER_ID_AND_RECEIVER,
            query = "SELECT DISTINCT c FROM Chat c WHERE (c.sender.id = :senderId AND c.recipient.id = :recipientId) OR (c.sender.id = :recipientId AND c.recipient.id = :senderId) ORDER BY createdDate DESC"
)
public class Chat extends BaseAuditingEntity {
    @Id
    @GeneratedValue(strategy = UUID)
    private String id;
    @ManyToOne
    @JoinColumn(name = "sender_id")
    private User sender;
    @ManyToOne
    @JoinColumn(name = "recipient_id")
    private User recipient;
    @OneToMany(mappedBy = "chat", fetch = FetchType.EAGER)
    @OrderBy("createdDate DESC")
    private List<Message> messages;
    @Enumerated(EnumType.STRING)
    private ChatStatus status;
    private int pendingMessageCount;
    @Column(columnDefinition = "BOOLEAN NOT NULL DEFAULT FALSE")
    private boolean senderFavorite;
    @Column(columnDefinition = "BOOLEAN NOT NULL DEFAULT FALSE")
    private boolean recipientFavorite;
    @Column(columnDefinition = "BOOLEAN NOT NULL DEFAULT FALSE")
    private boolean senderArchived;
    @Column(columnDefinition = "BOOLEAN NOT NULL DEFAULT FALSE")
    private boolean recipientArchived;
    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private ChatType type = ChatType.DIRECT;
    // GROUP-only: sender/recipient are null for GROUP chats, name/avatarUrl/createdBy are
    // null for DIRECT chats — the two shapes never overlap.
    private String name;
    private String avatarUrl;
    private String createdBy;

    @Transient
    public boolean isGroup() {
        return type == ChatType.GROUP;
    }

    /** Dispatch point so callers (ChatMapper, etc.) don't need their own type branch. */
    @Transient
    public String getDisplayName(String viewerId) {
        return isGroup() ? name : getChatName(viewerId);
    }

    /** Dispatch point so callers (ChatMapper, etc.) don't need their own type branch. */
    @Transient
    public String getDisplayAvatarUrl(String viewerId) {
        return isGroup() ? avatarUrl : getChatAvatarUrl(viewerId);
    }

    @Transient
    public String getChatName(String senderId) {
        if (recipient.getId().equals(senderId)) {
            return sender.getFirstName() + " " + sender.getLastName();
        }
        return recipient.getFirstName() + " " + recipient.getLastName();
    }
    @Transient
    public String getChatAvatarUrl(String viewerId) {
        if (recipient.getId().equals(viewerId)) {
            return sender.getAvatarUrl();
        }
        return recipient.getAvatarUrl();
    }

    @Transient
    public String getTargetChatName(String senderId) {
        if (sender.getId().equals(senderId)) {
            return sender.getFirstName() + " " + sender.getLastName();
        }
        return recipient.getFirstName() + " " + recipient.getLastName();
    }

    @Transient
    public long getUnreadMessages(String senderId) {
        return this.messages
                .stream()
                .filter(m -> m.getReceiverId().equals(senderId))
                .filter(m -> MessageState.SENT == m.getState())
                .count();
    }

    /** GROUP equivalent of {@link #getUnreadMessages}: messages.receiverId is always null
     *  for GROUP messages, so unread is derived from the viewer's persisted read cursor
     *  (ChatMember.lastReadMessageId) instead — messages from the viewer themselves never
     *  count as unread. */
    @Transient
    public long getUnreadMessagesForGroup(String viewerId, Long lastReadMessageId) {
        return this.messages
                .stream()
                .filter(m -> !m.getSenderId().equals(viewerId))
                .filter(m -> lastReadMessageId == null || m.getId() > lastReadMessageId)
                .count();
    }

    @Transient
    public String getLastMessage() {
        if (messages != null && !messages.isEmpty()) {
            return switch (messages.get(0).getType()) {
                case TEXT -> messages.get(0).getContent();
                case IMAGE -> "📷 Foto";
                case VIDEO -> "🎥 Video";
                case AUDIO -> "🎤 Messaggio vocale";
            };
        }
        return null; // No messages available
    }

    @Transient
    public boolean isFavorite(String viewerId) {
        return sender.getId().equals(viewerId) ? senderFavorite : recipientFavorite;
    }

    @Transient
    public boolean isArchived(String viewerId) {
        return sender.getId().equals(viewerId) ? senderArchived : recipientArchived;
    }

    @Transient
    public MessageType getLastMessageType() {
        if (messages != null && !messages.isEmpty()) {
            return messages.get(0).getType();
        }
        return null;
    }

    @Transient
    public LocalDateTime getLastMessageTime() {
        if (messages != null && !messages.isEmpty()) {
            return messages.get(0).getCreatedDate();
        }
        return null;
    }
}

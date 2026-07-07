package dev.pioruocco.wacchat.message;

import dev.pioruocco.wacchat.chat.Chat;
import dev.pioruocco.wacchat.common.BaseAuditingEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "messages", indexes = @Index(name = "idx_messages_chat_id_created_date", columnList = "chat_id, created_date"))
@NamedQuery(name = MessageConstants.FIND_MESSAGES_BY_CHAT_ID,
            query = "SELECT m FROM Message m WHERE m.chat.id = :chatId ORDER BY m.createdDate"
)
@NamedQuery(name = MessageConstants.SET_MESSAGES_TO_SEEN_BY_CHAT,
            query = "UPDATE Message SET state = :newState WHERE chat.id = :chatId"
)
public class Message extends BaseAuditingEntity {

    @Id
    @SequenceGenerator(name = "msg_seq", sequenceName = "msg_seq", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "msg_seq")
    private Long id;
    @Column(columnDefinition = "TEXT")
    private String content;
    @Enumerated(EnumType.STRING)
    private MessageState state;
    @Enumerated(EnumType.STRING)
    private MessageType type;
    @ManyToOne
    @JoinColumn(name = "chat_id")
    private Chat chat;
    @Column(name = "sender_id", nullable = false)
    private String senderId;
    // Null for GROUP messages — there's no single receiver once a chat has 3+ members;
    // fan-out to all other chat_members happens at notification-dispatch time instead.
    @Column(name = "receiver_id")
    private String receiverId;
    private String mediaFilePath;
    @Column(name = "reply_to_id")
    private Long replyToId;
    @Column(columnDefinition = "BOOLEAN NOT NULL DEFAULT FALSE")
    private boolean forwarded;
    @Column(columnDefinition = "BOOLEAN NOT NULL DEFAULT FALSE")
    private boolean deleted;

}

package dev.pioruocco.wacchat.chat;

import dev.pioruocco.wacchat.common.BaseAuditingEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Per-member state for a GROUP chat — favorite/archived/read-cursor cannot use the
 * sender/recipient two-column pattern on {@link Chat} once a chat has more than two
 * parties. DIRECT chats never get rows here; they keep using
 * chat.senderFavorite/recipientFavorite/senderArchived/recipientArchived.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "chat_members", uniqueConstraints = @UniqueConstraint(columnNames = {"chat_id", "user_id"}))
public class ChatMember extends BaseAuditingEntity {

    @Id
    @SequenceGenerator(name = "chat_members_seq", sequenceName = "chat_members_seq", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "chat_members_seq")
    private Long id;

    @Column(name = "chat_id", nullable = false)
    private String chatId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GroupMemberRole role;

    @Column(columnDefinition = "BOOLEAN NOT NULL DEFAULT FALSE")
    private boolean favorite;

    @Column(columnDefinition = "BOOLEAN NOT NULL DEFAULT FALSE")
    private boolean archived;

    @Column(name = "last_read_message_id")
    private Long lastReadMessageId;
}

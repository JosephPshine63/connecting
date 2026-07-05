package dev.pioruocco.wacchat.chat;

import dev.pioruocco.wacchat.message.MessageType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ChatResponse {

    private String id;
    private String name;
    private long unreadCount;
    private String lastMessage;
    private MessageType lastMessageType;
    private LocalDateTime lastMessageTime;
    private boolean isRecipientOnline;
    private String senderId;
    private String receiverId;
    private String avatarUrl;
    private ChatStatus status;
    private int pendingMessageCount;
    private boolean favorite;
}

package dev.pioruocco.wacchat.chat;

import dev.pioruocco.wacchat.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatMapper {

    private final ChatMemberRepository chatMemberRepository;
    private final UserRepository userRepository;

    @Value("${application.admin.user-id:}")
    private String adminUserId;

    public ChatResponse toChatResponse(Chat chat, String viewerId) {
        return chat.isGroup() ? toGroupChatResponse(chat, viewerId) : toDirectChatResponse(chat, viewerId);
    }

    private ChatResponse toDirectChatResponse(Chat chat, String senderId) {
        String otherUserId = chat.getSender().getId().equals(senderId)
                ? chat.getRecipient().getId()
                : chat.getSender().getId();
        return ChatResponse.builder()
                .id(chat.getId())
                .type(ChatType.DIRECT)
                .name(chat.getDisplayName(senderId))
                .unreadCount(chat.getUnreadMessages(senderId))
                .lastMessage(chat.getLastMessage())
                .lastMessageType(chat.getLastMessageType())
                .lastMessageTime(chat.getLastMessageTime())
                .isRecipientOnline(chat.getRecipient().isUserOnline())
                .senderId(chat.getSender().getId())
                .receiverId(chat.getRecipient().getId())
                .avatarUrl(chat.getDisplayAvatarUrl(senderId))
                .status(chat.getStatus())
                .pendingMessageCount(chat.getPendingMessageCount())
                .favorite(chat.isFavorite(senderId))
                .archived(chat.isArchived(senderId))
                .isAdminChat(!adminUserId.isBlank() && adminUserId.equals(otherUserId))
                .members(List.of())
                .build();
    }

    private ChatResponse toGroupChatResponse(Chat chat, String viewerId) {
        List<ChatMember> members = chatMemberRepository.findByChatId(chat.getId());
        ChatMember viewerMembership = members.stream()
                .filter(m -> m.getUserId().equals(viewerId))
                .findFirst()
                .orElse(null);
        Long lastReadMessageId = viewerMembership == null ? null : viewerMembership.getLastReadMessageId();

        return ChatResponse.builder()
                .id(chat.getId())
                .type(ChatType.GROUP)
                .name(chat.getDisplayName(viewerId))
                .unreadCount(chat.getUnreadMessagesForGroup(viewerId, lastReadMessageId))
                .lastMessage(chat.getLastMessage())
                .lastMessageType(chat.getLastMessageType())
                .lastMessageTime(chat.getLastMessageTime())
                .avatarUrl(chat.getDisplayAvatarUrl(viewerId))
                .status(chat.getStatus())
                .favorite(viewerMembership != null && viewerMembership.isFavorite())
                .archived(viewerMembership != null && viewerMembership.isArchived())
                .members(toMemberResponses(members))
                .build();
    }

    private List<GroupMemberResponse> toMemberResponses(List<ChatMember> members) {
        return members.stream()
                .map(member -> userRepository.findByPublicId(member.getUserId())
                        .map(user -> GroupMemberResponse.builder()
                                .userId(user.getId())
                                .name(user.getFirstName() + " " + user.getLastName())
                                .avatarUrl(user.getAvatarUrl())
                                .role(member.getRole())
                                .online(user.isUserOnline())
                                .build())
                        .orElseGet(() -> GroupMemberResponse.builder()
                                .userId(member.getUserId())
                                .role(member.getRole())
                                .build()))
                .toList();
    }
}

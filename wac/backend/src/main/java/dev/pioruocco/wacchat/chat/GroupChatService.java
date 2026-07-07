package dev.pioruocco.wacchat.chat;

import dev.pioruocco.wacchat.notification.Notification;
import dev.pioruocco.wacchat.notification.NotificationService;
import dev.pioruocco.wacchat.notification.NotificationType;
import dev.pioruocco.wacchat.user.User;
import dev.pioruocco.wacchat.user.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * GROUP-only surface, kept separate from ChatService so the existing DIRECT-chat
 * request/accept/reject flow (and its tests) stay untouched. Groups skip that dance
 * entirely — they're created ACCEPTED — and blocking is intentionally not enforced here
 * (per product decision: block only applies to DIRECT chats).
 */
@Service
@RequiredArgsConstructor
public class GroupChatService {

    private static final int MAX_GROUP_MEMBERS = 50;

    private final ChatRepository chatRepository;
    private final ChatMemberRepository chatMemberRepository;
    private final UserRepository userRepository;
    private final ChatMapper mapper;
    private final NotificationService notificationService;

    @Transactional
    public ChatResponse createGroup(String creatorId, List<String> memberIds, String name) {
        Set<String> otherMembers = new LinkedHashSet<>(memberIds);
        otherMembers.remove(creatorId);
        if (otherMembers.isEmpty()) {
            throw new IllegalArgumentException("A group needs at least one other member");
        }
        assertWithinSizeLimit(otherMembers.size() + 1);
        findUserOrThrow(creatorId);
        otherMembers.forEach(this::findUserOrThrow);

        Chat chat = new Chat();
        chat.setType(ChatType.GROUP);
        chat.setName(name);
        chat.setCreatedBy(creatorId);
        chat.setStatus(ChatStatus.ACCEPTED);
        chat.setPendingMessageCount(0);
        chat.setMessages(List.of());
        Chat saved = chatRepository.save(chat);

        chatMemberRepository.save(newMember(saved.getId(), creatorId, GroupMemberRole.OWNER));
        otherMembers.forEach(memberId -> chatMemberRepository.save(newMember(saved.getId(), memberId, GroupMemberRole.MEMBER)));
        otherMembers.forEach(memberId -> notifyGroupAdded(saved, creatorId, memberId));

        return mapper.toChatResponse(saved, creatorId);
    }

    @Transactional
    public void addMember(String chatId, String currentUserId, String newMemberId) {
        Chat chat = findGroupOrThrow(chatId);
        assertOwner(chatId, currentUserId);
        if (chatMemberRepository.existsByChatIdAndUserId(chatId, newMemberId)) {
            return;
        }
        assertWithinSizeLimit(chatMemberRepository.countByChatId(chatId) + 1);
        findUserOrThrow(newMemberId);
        chatMemberRepository.save(newMember(chatId, newMemberId, GroupMemberRole.MEMBER));
        notifyGroupAdded(chat, currentUserId, newMemberId);
    }

    private void notifyGroupAdded(Chat chat, String actorId, String recipientId) {
        notificationService.sendNotification(recipientId, Notification.builder()
                .type(NotificationType.GROUP_ADDED)
                .chatId(chat.getId())
                .senderId(actorId)
                .receiverId(recipientId)
                .chatName(chat.getName())
                .avatarUrl(chat.getAvatarUrl())
                .build());
    }

    /** Self-removal (leaving) is always allowed; removing someone else requires OWNER. */
    @Transactional
    public void removeMember(String chatId, String currentUserId, String memberId) {
        findGroupOrThrow(chatId);
        if (!memberId.equals(currentUserId)) {
            assertOwner(chatId, currentUserId);
        } else {
            assertMember(chatId, currentUserId);
        }
        chatMemberRepository.deleteByChatIdAndUserId(chatId, memberId);
    }

    @Transactional
    public void renameGroup(String chatId, String currentUserId, String newName) {
        Chat chat = findGroupOrThrow(chatId);
        assertOwner(chatId, currentUserId);
        chat.setName(newName);
        chatRepository.save(chat);
    }

    @Transactional(readOnly = true)
    public List<GroupMemberResponse> listMembers(String chatId, String currentUserId) {
        findGroupOrThrow(chatId);
        assertMember(chatId, currentUserId);
        return chatMemberRepository.findByChatId(chatId).stream()
                .map(member -> userRepository.findByPublicId(member.getUserId())
                        .map(user -> toMemberResponse(member, user))
                        .orElseGet(() -> GroupMemberResponse.builder()
                                .userId(member.getUserId())
                                .role(member.getRole())
                                .build()))
                .toList();
    }

    private GroupMemberResponse toMemberResponse(ChatMember member, User user) {
        return GroupMemberResponse.builder()
                .userId(user.getId())
                .name(user.getFirstName() + " " + user.getLastName())
                .avatarUrl(user.getAvatarUrl())
                .role(member.getRole())
                .online(user.isUserOnline())
                .build();
    }

    private ChatMember newMember(String chatId, String userId, GroupMemberRole role) {
        ChatMember member = new ChatMember();
        member.setChatId(chatId);
        member.setUserId(userId);
        member.setRole(role);
        return member;
    }

    private void assertWithinSizeLimit(long memberCountAfterChange) {
        if (memberCountAfterChange > MAX_GROUP_MEMBERS) {
            throw new GroupSizeLimitExceededException(MAX_GROUP_MEMBERS);
        }
    }

    private void assertOwner(String chatId, String userId) {
        ChatMember member = chatMemberRepository.findByChatIdAndUserId(chatId, userId)
                .orElseThrow(() -> new AccessDeniedException("You are not a member of this group"));
        if (member.getRole() != GroupMemberRole.OWNER) {
            throw new AccessDeniedException("Only the group owner can do this");
        }
    }

    private void assertMember(String chatId, String userId) {
        if (!chatMemberRepository.existsByChatIdAndUserId(chatId, userId)) {
            throw new AccessDeniedException("You are not a member of this group");
        }
    }

    private Chat findGroupOrThrow(String chatId) {
        Chat chat = chatRepository.findById(chatId)
                .orElseThrow(() -> new EntityNotFoundException("Chat with id " + chatId + " not found"));
        if (!chat.isGroup()) {
            throw new EntityNotFoundException("Chat with id " + chatId + " is not a group");
        }
        return chat;
    }

    private User findUserOrThrow(String userId) {
        return userRepository.findByPublicId(userId)
                .orElseThrow(() -> new EntityNotFoundException("User with id " + userId + " not found"));
    }
}

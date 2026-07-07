package dev.pioruocco.wacchat.chat;

import dev.pioruocco.wacchat.notification.NotificationService;
import dev.pioruocco.wacchat.user.User;
import dev.pioruocco.wacchat.user.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GroupChatServiceTest {

    private static final String OWNER_ID = "owner-1";
    private static final String MEMBER_ID = "member-1";
    private static final String OTHER_MEMBER_ID = "member-2";
    private static final String CHAT_ID = "group-chat-1";

    @Mock
    private ChatRepository chatRepository;
    @Mock
    private ChatMemberRepository chatMemberRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ChatMapper mapper;
    @Mock
    private NotificationService notificationService;

    private GroupChatService groupChatService;

    @BeforeEach
    void setUp() {
        groupChatService = new GroupChatService(chatRepository, chatMemberRepository, userRepository, mapper, notificationService);
    }

    @Test
    void createGroup_savesGroupChatWithOwnerAndMembers() {
        stubUserExists(OWNER_ID);
        stubUserExists(MEMBER_ID);
        stubUserExists(OTHER_MEMBER_ID);
        when(chatRepository.save(any(Chat.class))).thenAnswer(inv -> {
            Chat chat = inv.getArgument(0);
            chat.setId(CHAT_ID);
            return chat;
        });

        groupChatService.createGroup(OWNER_ID, List.of(MEMBER_ID, OTHER_MEMBER_ID), "Trip planning");

        ArgumentCaptor<Chat> savedChat = ArgumentCaptor.forClass(Chat.class);
        verify(chatRepository).save(savedChat.capture());
        assertThat(savedChat.getValue().getType()).isEqualTo(ChatType.GROUP);
        assertThat(savedChat.getValue().getName()).isEqualTo("Trip planning");
        assertThat(savedChat.getValue().getCreatedBy()).isEqualTo(OWNER_ID);
        assertThat(savedChat.getValue().getStatus()).isEqualTo(ChatStatus.ACCEPTED);

        ArgumentCaptor<ChatMember> savedMembers = ArgumentCaptor.forClass(ChatMember.class);
        verify(chatMemberRepository, times(3)).save(savedMembers.capture());
        assertThat(savedMembers.getAllValues())
                .filteredOn(m -> m.getUserId().equals(OWNER_ID))
                .extracting(ChatMember::getRole)
                .containsExactly(GroupMemberRole.OWNER);
        assertThat(savedMembers.getAllValues())
                .filteredOn(m -> !m.getUserId().equals(OWNER_ID))
                .extracting(ChatMember::getRole)
                .containsExactly(GroupMemberRole.MEMBER, GroupMemberRole.MEMBER);
    }

    @Test
    void createGroup_creatorListedAsOwnMember_isDeduped() {
        stubUserExists(OWNER_ID);
        stubUserExists(MEMBER_ID);
        when(chatRepository.save(any(Chat.class))).thenAnswer(inv -> {
            Chat chat = inv.getArgument(0);
            chat.setId(CHAT_ID);
            return chat;
        });

        groupChatService.createGroup(OWNER_ID, List.of(OWNER_ID, MEMBER_ID), "Solo dedup");

        verify(chatMemberRepository, times(2)).save(any(ChatMember.class));
    }

    @Test
    void createGroup_noOtherMembers_throwsIllegalArgument() {
        assertThatThrownBy(() -> groupChatService.createGroup(OWNER_ID, List.of(OWNER_ID), "Just me"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(chatRepository, never()).save(any());
    }

    @Test
    void createGroup_exceedsSizeLimit_throwsGroupSizeLimitExceeded() {
        List<String> tooMany = java.util.stream.IntStream.range(0, 50)
                .mapToObj(i -> "member-" + i)
                .toList();

        assertThatThrownBy(() -> groupChatService.createGroup(OWNER_ID, tooMany, "Huge group"))
                .isInstanceOf(GroupSizeLimitExceededException.class);
        verify(chatRepository, never()).save(any());
    }

    @Test
    void addMember_byOwner_savesNewMember() {
        when(chatRepository.findById(CHAT_ID)).thenReturn(Optional.of(groupChat()));
        when(chatMemberRepository.findByChatIdAndUserId(CHAT_ID, OWNER_ID)).thenReturn(Optional.of(member(OWNER_ID, GroupMemberRole.OWNER)));
        when(chatMemberRepository.existsByChatIdAndUserId(CHAT_ID, OTHER_MEMBER_ID)).thenReturn(false);
        when(chatMemberRepository.countByChatId(CHAT_ID)).thenReturn(1L);
        stubUserExists(OTHER_MEMBER_ID);

        groupChatService.addMember(CHAT_ID, OWNER_ID, OTHER_MEMBER_ID);

        ArgumentCaptor<ChatMember> saved = ArgumentCaptor.forClass(ChatMember.class);
        verify(chatMemberRepository).save(saved.capture());
        assertThat(saved.getValue().getUserId()).isEqualTo(OTHER_MEMBER_ID);
        assertThat(saved.getValue().getRole()).isEqualTo(GroupMemberRole.MEMBER);
    }

    @Test
    void addMember_byNonOwner_throwsAccessDenied() {
        when(chatRepository.findById(CHAT_ID)).thenReturn(Optional.of(groupChat()));
        when(chatMemberRepository.findByChatIdAndUserId(CHAT_ID, MEMBER_ID)).thenReturn(Optional.of(member(MEMBER_ID, GroupMemberRole.MEMBER)));

        assertThatThrownBy(() -> groupChatService.addMember(CHAT_ID, MEMBER_ID, OTHER_MEMBER_ID))
                .isInstanceOf(AccessDeniedException.class);
        verify(chatMemberRepository, never()).save(any());
    }

    @Test
    void addMember_alreadyMember_isIdempotentNoOp() {
        when(chatRepository.findById(CHAT_ID)).thenReturn(Optional.of(groupChat()));
        when(chatMemberRepository.findByChatIdAndUserId(CHAT_ID, OWNER_ID)).thenReturn(Optional.of(member(OWNER_ID, GroupMemberRole.OWNER)));
        when(chatMemberRepository.existsByChatIdAndUserId(CHAT_ID, MEMBER_ID)).thenReturn(true);

        groupChatService.addMember(CHAT_ID, OWNER_ID, MEMBER_ID);

        verify(chatMemberRepository, never()).save(any());
    }

    @Test
    void addMember_atSizeLimit_throwsGroupSizeLimitExceeded() {
        when(chatRepository.findById(CHAT_ID)).thenReturn(Optional.of(groupChat()));
        when(chatMemberRepository.findByChatIdAndUserId(CHAT_ID, OWNER_ID)).thenReturn(Optional.of(member(OWNER_ID, GroupMemberRole.OWNER)));
        when(chatMemberRepository.existsByChatIdAndUserId(CHAT_ID, OTHER_MEMBER_ID)).thenReturn(false);
        when(chatMemberRepository.countByChatId(CHAT_ID)).thenReturn(50L);

        assertThatThrownBy(() -> groupChatService.addMember(CHAT_ID, OWNER_ID, OTHER_MEMBER_ID))
                .isInstanceOf(GroupSizeLimitExceededException.class);
        verify(chatMemberRepository, never()).save(any());
    }

    @Test
    void removeMember_self_isAlwaysAllowed() {
        when(chatRepository.findById(CHAT_ID)).thenReturn(Optional.of(groupChat()));
        when(chatMemberRepository.existsByChatIdAndUserId(CHAT_ID, MEMBER_ID)).thenReturn(true);

        groupChatService.removeMember(CHAT_ID, MEMBER_ID, MEMBER_ID);

        verify(chatMemberRepository).deleteByChatIdAndUserId(CHAT_ID, MEMBER_ID);
    }

    @Test
    void removeMember_byOwner_removesOtherMember() {
        when(chatRepository.findById(CHAT_ID)).thenReturn(Optional.of(groupChat()));
        when(chatMemberRepository.findByChatIdAndUserId(CHAT_ID, OWNER_ID)).thenReturn(Optional.of(member(OWNER_ID, GroupMemberRole.OWNER)));

        groupChatService.removeMember(CHAT_ID, OWNER_ID, MEMBER_ID);

        verify(chatMemberRepository).deleteByChatIdAndUserId(CHAT_ID, MEMBER_ID);
    }

    @Test
    void removeMember_byNonOwnerTargetingSomeoneElse_throwsAccessDenied() {
        when(chatRepository.findById(CHAT_ID)).thenReturn(Optional.of(groupChat()));
        when(chatMemberRepository.findByChatIdAndUserId(CHAT_ID, MEMBER_ID)).thenReturn(Optional.of(member(MEMBER_ID, GroupMemberRole.MEMBER)));

        assertThatThrownBy(() -> groupChatService.removeMember(CHAT_ID, MEMBER_ID, OTHER_MEMBER_ID))
                .isInstanceOf(AccessDeniedException.class);
        verify(chatMemberRepository, never()).deleteByChatIdAndUserId(anyString(), anyString());
    }

    @Test
    void renameGroup_byOwner_updatesName() {
        Chat chat = groupChat();
        when(chatRepository.findById(CHAT_ID)).thenReturn(Optional.of(chat));
        when(chatMemberRepository.findByChatIdAndUserId(CHAT_ID, OWNER_ID)).thenReturn(Optional.of(member(OWNER_ID, GroupMemberRole.OWNER)));

        groupChatService.renameGroup(CHAT_ID, OWNER_ID, "New name");

        assertThat(chat.getName()).isEqualTo("New name");
        verify(chatRepository).save(chat);
    }

    @Test
    void renameGroup_byNonOwner_throwsAccessDenied() {
        when(chatRepository.findById(CHAT_ID)).thenReturn(Optional.of(groupChat()));
        when(chatMemberRepository.findByChatIdAndUserId(CHAT_ID, MEMBER_ID)).thenReturn(Optional.of(member(MEMBER_ID, GroupMemberRole.MEMBER)));

        assertThatThrownBy(() -> groupChatService.renameGroup(CHAT_ID, MEMBER_ID, "New name"))
                .isInstanceOf(AccessDeniedException.class);
        verify(chatRepository, never()).save(any());
    }

    @Test
    void listMembers_byNonMember_throwsAccessDenied() {
        when(chatRepository.findById(CHAT_ID)).thenReturn(Optional.of(groupChat()));
        when(chatMemberRepository.existsByChatIdAndUserId(CHAT_ID, "stranger")).thenReturn(false);

        assertThatThrownBy(() -> groupChatService.listMembers(CHAT_ID, "stranger"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void listMembers_byMember_returnsMemberSummaries() {
        when(chatRepository.findById(CHAT_ID)).thenReturn(Optional.of(groupChat()));
        when(chatMemberRepository.existsByChatIdAndUserId(CHAT_ID, OWNER_ID)).thenReturn(true);
        when(chatMemberRepository.findByChatId(CHAT_ID)).thenReturn(List.of(member(OWNER_ID, GroupMemberRole.OWNER)));
        when(userRepository.findByPublicId(OWNER_ID)).thenReturn(Optional.of(user(OWNER_ID)));

        List<GroupMemberResponse> members = groupChatService.listMembers(CHAT_ID, OWNER_ID);

        assertThat(members).hasSize(1);
        assertThat(members.get(0).getUserId()).isEqualTo(OWNER_ID);
        assertThat(members.get(0).getRole()).isEqualTo(GroupMemberRole.OWNER);
    }

    @Test
    void addMember_onDirectChat_throwsEntityNotFound() {
        Chat direct = new Chat();
        direct.setId(CHAT_ID);
        direct.setType(ChatType.DIRECT);
        when(chatRepository.findById(CHAT_ID)).thenReturn(Optional.of(direct));

        assertThatThrownBy(() -> groupChatService.addMember(CHAT_ID, OWNER_ID, MEMBER_ID))
                .isInstanceOf(EntityNotFoundException.class);
    }

    private void stubUserExists(String userId) {
        when(userRepository.findByPublicId(userId)).thenReturn(Optional.of(user(userId)));
    }

    private static User user(String id) {
        User user = new User();
        user.setId(id);
        user.setFirstName("First-" + id);
        user.setLastName("Last-" + id);
        return user;
    }

    private static Chat groupChat() {
        Chat chat = new Chat();
        chat.setId(CHAT_ID);
        chat.setType(ChatType.GROUP);
        chat.setName("Group");
        chat.setCreatedBy(OWNER_ID);
        chat.setStatus(ChatStatus.ACCEPTED);
        return chat;
    }

    private static ChatMember member(String userId, GroupMemberRole role) {
        ChatMember member = new ChatMember();
        member.setChatId(CHAT_ID);
        member.setUserId(userId);
        member.setRole(role);
        return member;
    }
}

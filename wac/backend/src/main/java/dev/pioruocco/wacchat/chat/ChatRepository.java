package dev.pioruocco.wacchat.chat;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChatRepository extends JpaRepository<Chat, String> {

    @Query(name = ChatConstants.FIND_CHAT_BY_SENDER_ID)
    List<Chat> findChatsBySenderId(@Param("senderId") String senderId);

    @Query(name = ChatConstants.FIND_CHAT_BY_SENDER_ID_AND_RECEIVER)
    Optional<Chat> findChatByReceiverAndSender(@Param("senderId") String id, @Param("recipientId") String recipientId);

    @Query("SELECT DISTINCT c FROM Chat c WHERE c.type = dev.pioruocco.wacchat.chat.ChatType.GROUP "
            + "AND c.id IN (SELECT m.chatId FROM ChatMember m WHERE m.userId = :userId) ORDER BY c.createdDate DESC")
    List<Chat> findGroupChatsByMemberId(@Param("userId") String userId);
}

package dev.pioruocco.wacchat.chat;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChatMemberRepository extends JpaRepository<ChatMember, Long> {

    List<ChatMember> findByChatId(String chatId);

    Optional<ChatMember> findByChatIdAndUserId(String chatId, String userId);

    boolean existsByChatIdAndUserId(String chatId, String userId);

    long countByChatId(String chatId);

    void deleteByChatIdAndUserId(String chatId, String userId);
}

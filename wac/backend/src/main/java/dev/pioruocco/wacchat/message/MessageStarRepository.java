package dev.pioruocco.wacchat.message;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MessageStarRepository extends JpaRepository<MessageStar, Long> {

    Optional<MessageStar> findByMessageIdAndUserId(Long messageId, String userId);

    List<MessageStar> findByMessageIdIn(List<Long> messageIds);
}

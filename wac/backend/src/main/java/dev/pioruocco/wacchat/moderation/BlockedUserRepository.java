package dev.pioruocco.wacchat.moderation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BlockedUserRepository extends JpaRepository<BlockedUser, Long> {

    boolean existsByBlockerIdAndBlockedId(String blockerId, String blockedId);

    Optional<BlockedUser> findByBlockerIdAndBlockedId(String blockerId, String blockedId);

    List<BlockedUser> findByBlockerId(String blockerId);

    void deleteByBlockerIdAndBlockedId(String blockerId, String blockedId);

    @Query("SELECT COUNT(b) > 0 FROM BlockedUser b WHERE " +
            "(b.blockerId = :userIdA AND b.blockedId = :userIdB) OR " +
            "(b.blockerId = :userIdB AND b.blockedId = :userIdA)")
    boolean existsBlockBetween(@Param("userIdA") String userIdA, @Param("userIdB") String userIdB);

    @Query("SELECT b.blockedId FROM BlockedUser b WHERE b.blockerId = :blockerId")
    List<String> findBlockedIdsByBlockerId(@Param("blockerId") String blockerId);
}

package dev.pioruocco.wacchat.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    @Query(name = UserConstants.FIND_USER_BY_EMAIL)
    Optional<User> findByEmail(@Param("email") String userEmail);

    @Query(name = UserConstants.FIND_ALL_USERS_EXCEPT_SELF)
    List<User> findAllUsersExceptSelf(@Param("publicId") String publicId);

    @Query(name = UserConstants.FIND_USER_BY_PUBLIC_ID)
    Optional<User> findByPublicId(@Param("publicId") String senderId);

    @Query("SELECT u FROM User u WHERE u.lastSeen < :cutoff OR (u.lastSeen IS NULL AND u.createdDate < :cutoff)")
    List<User> findInactiveUsersBefore(@Param("cutoff") LocalDateTime cutoff);

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);
}

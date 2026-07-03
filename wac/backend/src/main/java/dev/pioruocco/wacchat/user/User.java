package dev.pioruocco.wacchat.user;

import dev.pioruocco.wacchat.chat.Chat;
import dev.pioruocco.wacchat.common.BaseAuditingEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.domain.Persistable;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "users")
@NamedQuery(name = UserConstants.FIND_USER_BY_EMAIL,
            query = "SELECT u FROM User u WHERE u.email = :email"
)
@NamedQuery(name = UserConstants.FIND_ALL_USERS_EXCEPT_SELF,
            query = "SELECT u FROM User u WHERE u.id != :publicId")
@NamedQuery(name = UserConstants.FIND_USER_BY_PUBLIC_ID,
            query = "SELECT u FROM User u WHERE u.id = :publicId")
@NamedQuery(name = UserConstants.FIND_USER_BY_USERNAME,
            query = "SELECT u FROM User u WHERE u.username = :username")
public class User extends BaseAuditingEntity implements Persistable<String> {

    private static final int LAST_ACTIVATE_INTERVAL = 5;

    @Id
    private String id;

    // Manually-assigned @Id means Spring Data JPA's default isNew() heuristic ("id == null")
    // can't tell a transient object apart from an existing row that just hasn't been reloaded —
    // it would call merge() (which overwrites the whole row, nulls included) instead of
    // persist() for anything that already has an id set. Persistable<String> makes isNew()
    // explicit instead: true only until this instance is actually loaded from or written to
    // the DB, so a genuinely new row goes through persist() (and a clash surfaces as a
    // constraint violation, not a silent field wipe).
    @Transient
    private boolean isNew = true;
    private String firstName;
    private String lastName;
    private String email;
    @Column(unique = true)
    private String username;
    private LocalDateTime lastSeen;
    @Column(length = 500)
    private String avatarUrl;
    @Column(name = "active_session_id", length = 255)
    private String activeSessionId;

    @OneToMany(mappedBy = "sender")
    private List<Chat> chatsAsSender;

    @OneToMany(mappedBy = "recipient")
    private List<Chat> chatsAsRecipient;

    @Transient
    public boolean isUserOnline() {
        return lastSeen != null && lastSeen.isAfter(LocalDateTime.now().minusMinutes(LAST_ACTIVATE_INTERVAL));
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.isNew = false;
    }

}

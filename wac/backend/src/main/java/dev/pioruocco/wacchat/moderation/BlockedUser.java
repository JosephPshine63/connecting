package dev.pioruocco.wacchat.moderation;

import dev.pioruocco.wacchat.common.BaseAuditingEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "blocked_users", uniqueConstraints = @UniqueConstraint(columnNames = {"blocker_id", "blocked_id"}))
public class BlockedUser extends BaseAuditingEntity {

    @Id
    @SequenceGenerator(name = "blocked_users_seq", sequenceName = "blocked_users_seq", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "blocked_users_seq")
    private Long id;

    @Column(name = "blocker_id", nullable = false)
    private String blockerId;

    @Column(name = "blocked_id", nullable = false)
    private String blockedId;
}

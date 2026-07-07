package dev.pioruocco.wacchat.push;

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

/**
 * One row per browser/device Service Worker registration. Unique on endpoint ALONE (not
 * (user_id, endpoint)) so that if the same registration re-subscribes under a different
 * logged-in user (shared device), the row is reassigned rather than duplicated.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "push_subscriptions", uniqueConstraints = @UniqueConstraint(columnNames = {"endpoint"}))
public class PushSubscription extends BaseAuditingEntity {

    @Id
    @SequenceGenerator(name = "push_subscriptions_seq", sequenceName = "push_subscriptions_seq", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "push_subscriptions_seq")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "endpoint", nullable = false, columnDefinition = "TEXT")
    private String endpoint;

    @Column(name = "p256dh", nullable = false)
    private String p256dh;

    @Column(name = "auth_key", nullable = false)
    private String authKey;
}

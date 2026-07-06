package dev.pioruocco.wacchat.message;

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
@Table(name = "message_stars", uniqueConstraints = @UniqueConstraint(columnNames = {"message_id", "user_id"}))
public class MessageStar extends BaseAuditingEntity {

    @Id
    @SequenceGenerator(name = "message_stars_seq", sequenceName = "message_stars_seq", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "message_stars_seq")
    private Long id;
    @Column(name = "message_id", nullable = false)
    private Long messageId;
    @Column(name = "user_id", nullable = false)
    private String userId;

}

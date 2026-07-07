package dev.pioruocco.wacchat.user;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Embeddable
public class ActiveSession {

    @Column(name = "tab_id", length = 255)
    private String tabId;

    @Column(name = "last_seen")
    private LocalDateTime lastSeen;
}

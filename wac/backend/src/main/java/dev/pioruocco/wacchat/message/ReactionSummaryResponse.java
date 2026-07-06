package dev.pioruocco.wacchat.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ReactionSummaryResponse {

    private String emoji;
    private int count;
    private boolean reactedByMe;
}

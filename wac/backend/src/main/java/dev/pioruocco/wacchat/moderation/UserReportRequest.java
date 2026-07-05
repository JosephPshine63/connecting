package dev.pioruocco.wacchat.moderation;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
public class UserReportRequest {

    @NotNull
    private ReportReason reason;

    @Size(max = 1000)
    private String details;
}

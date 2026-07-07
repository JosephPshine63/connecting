package dev.pioruocco.wacchat.message;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
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
public class ReactionRequest {

    @NotBlank
    @Size(max = 32)
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String emoji;
}

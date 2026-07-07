package dev.pioruocco.wacchat.chat;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CreateGroupRequest {

    @NotBlank
    @Size(max = 255)
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @NotEmpty
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private List<String> memberIds;
}

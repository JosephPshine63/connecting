package dev.pioruocco.wacchat.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UserRequest {

    @NotBlank
    @Size(min = 3, max = 20, message = "Lo username deve avere tra 3 e 20 caratteri")
    @Pattern(regexp = "^[a-z0-9_.-]+$", message = "Solo lettere minuscole, numeri, underscore, trattini e punti")
    private String username;
}

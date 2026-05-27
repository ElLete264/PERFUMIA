package org.vedruna.perfumia.security.controller.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LoginRequestDTO {

    @NotBlank(message = "El usuario o email es obligatorio")
    String username;

    @NotBlank(message = "La password es obligatoria")
    String password;
}

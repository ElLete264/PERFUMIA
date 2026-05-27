package org.vedruna.perfumia.security.controller.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class GoogleLoginRequestDTO {

    @NotBlank(message = "El token de Google es obligatorio")
    String idToken;
}

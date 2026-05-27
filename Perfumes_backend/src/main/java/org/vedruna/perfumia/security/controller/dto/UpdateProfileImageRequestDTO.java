package org.vedruna.perfumia.security.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateProfileImageRequestDTO {

    @NotBlank(message = "La URL de imagen es obligatoria")
    @Size(max = 500, message = "La URL de imagen no puede superar 500 caracteres")
    String profileImageUrl;
}

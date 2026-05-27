package org.vedruna.perfumia.security.controller.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RegisterRequestDTO {

    @NotBlank(message = "El nombre de usuario es obligatorio")
    @Size(min = 3, max = 40, message = "El nombre de usuario debe tener entre 3 y 40 caracteres")
    String username;

    @NotBlank(message = "La password es obligatoria")
    @Size(min = 6, max = 100, message = "La password debe tener al menos 6 caracteres")
    String password;

    @NotBlank(message = "El email es obligatorio")
    @Email(message = "El email no tiene un formato valido")
    @Size(max = 90, message = "El email no puede superar 90 caracteres")
    String email;

    @Size(max = 1000, message = "La descripcion no puede superar 1000 caracteres")
    String description;
}


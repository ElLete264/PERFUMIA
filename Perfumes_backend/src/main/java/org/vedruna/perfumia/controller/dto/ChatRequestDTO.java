package org.vedruna.perfumia.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChatRequestDTO {

    @NotBlank(message = "El mensaje no puede estar vacio")
    @Size(max = 1000, message = "El mensaje no puede superar 1000 caracteres")
    String message;
}

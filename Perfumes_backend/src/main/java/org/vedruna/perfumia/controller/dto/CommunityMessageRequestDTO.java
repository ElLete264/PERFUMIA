package org.vedruna.perfumia.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CommunityMessageRequestDTO {
    @NotBlank(message = "El mensaje no puede estar vacio")
    @Size(max = 280, message = "El mensaje no puede superar 280 caracteres")
    String content;
}

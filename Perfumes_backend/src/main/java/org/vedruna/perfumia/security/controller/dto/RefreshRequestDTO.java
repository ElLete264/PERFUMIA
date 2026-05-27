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
public class RefreshRequestDTO {
    /** El Refresh Token que se desea canjear por un nuevo Access Token. */
    @NotBlank(message = "El refresh token es obligatorio")
    String refreshToken;
}


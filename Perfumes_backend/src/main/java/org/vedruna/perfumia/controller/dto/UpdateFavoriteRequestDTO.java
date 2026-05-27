package org.vedruna.perfumia.controller.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateFavoriteRequestDTO {

    @NotNull(message = "El favorito es obligatorio")
    Boolean favorite;
}

package org.vedruna.perfumia.service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class GoogleAccount {
    String subject;
    String email;
    String name;
    String pictureUrl;
}

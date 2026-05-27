package org.vedruna.perfumia.security.controller.dto;

import java.time.LocalDate;

import lombok.Data;

@Data
public class UserDTO {
    Integer userId;
    String username;
    String email;
    String description;
    String profileImageUrl;
    String authProvider;
    LocalDate createDate;
}


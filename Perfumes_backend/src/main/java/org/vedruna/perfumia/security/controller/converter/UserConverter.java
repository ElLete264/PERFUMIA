package org.vedruna.perfumia.security.controller.converter;

import org.springframework.stereotype.Component;
import org.vedruna.perfumia.persistance.model.User;
import org.vedruna.perfumia.security.controller.dto.LoginRequestDTO;
import org.vedruna.perfumia.security.controller.dto.RegisterRequestDTO;
import org.vedruna.perfumia.security.controller.dto.UserDTO;

/**
 * Componente de utilidad encargado de la conversión de datos entre las 
 * entidades del dominio (User) y los DTOs (Data Transfer Objects) 
 * utilizados en las peticiones y respuestas de la API.
 * 
 * Esto permite desacoplar la capa de la base de datos (Entidad) de la 
 * capa de presentación (DTO).
 */
@Component
public class UserConverter {
    
    /**
     * Convierte una entidad User del dominio a su correspondiente DTO de 
     * respuesta (UserDTO).
     * 
     * Este método es útil para enviar datos del usuario al cliente, 
     * excluyendo información sensible como la contraseña.
     *
     * @param user La entidad User a convertir.
     * @return UserDTO que contiene los datos visibles del usuario.
     */
    public UserDTO toDto(User user) {
        UserDTO dto = new UserDTO();
        dto.setUserId(user.getUserId());
        dto.setUsername(user.getUsername());
        dto.setEmail(user.getEmail());
        dto.setDescription(user.getDescription());
        dto.setProfileImageUrl(user.getProfileImageUrl());
        dto.setAuthProvider(user.getAuthProvider());
        dto.setCreateDate(user.getCreateDate());
        return dto; 
    }

    /**
     * Convierte un DTO de solicitud de inicio de sesión (LoginRequestDTO) 
     * a una entidad User.
     * 
     * Solo mapea los campos necesarios para la autenticación: 
     * nombre de usuario y contraseña.
     * 
     * @param request El DTO de la solicitud de login.
     * @return Una entidad User con el nombre de usuario y la contraseña.
     */
    public User loginToEntity(LoginRequestDTO request) {
        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(request.getPassword());
        return user;
    }

    /**
     * Convierte un DTO de solicitud de registro (RegisterRequestDTO) 
     * a una entidad User.
     * 
     * Mapea todos los campos necesarios para crear un nuevo registro 
     * de usuario en la base de datos.
     * 
     * @param request El DTO de la solicitud de registro.
     * @return Una entidad User con los datos proporcionados para el registro.
     */
    public User registerToEntity(RegisterRequestDTO request) {
        User user = new User();
        user.setUsername(request.getUsername()); 
        user.setPassword(request.getPassword());
        user.setEmail(request.getEmail());
        user.setDescription(request.getDescription());
        return user;
    }
}


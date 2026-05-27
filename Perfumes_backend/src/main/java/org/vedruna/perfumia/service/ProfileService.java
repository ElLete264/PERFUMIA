package org.vedruna.perfumia.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.vedruna.perfumia.persistance.model.User;
import org.vedruna.perfumia.persistance.repository.UserRepository;

import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
public class ProfileService {

    private static final String CLOUDINARY_URL_PREFIX = "https://res.cloudinary.com/";

    private final UserRepository userRepository;

    /**
     * Guarda en el usuario autenticado la URL de avatar ya subida a Cloudinary.
     *
     * @param user usuario autenticado.
     * @param profileImageUrl URL segura devuelta por Cloudinary.
     * @return usuario actualizado.
     */
    @Transactional
    public User updateProfileImage(User user, String profileImageUrl) {
        if (!StringUtils.hasText(profileImageUrl)
                || !profileImageUrl.trim().startsWith(CLOUDINARY_URL_PREFIX)) {
            throw new IllegalArgumentException("La imagen debe ser una URL valida de Cloudinary");
        }

        user.setProfileImageUrl(profileImageUrl.trim());
        return userRepository.save(user);
    }

    /**
     * Actualiza los datos publicos editables del usuario.
     *
     * @param user usuario autenticado.
     * @param username nuevo nombre publico.
     * @param description nueva descripcion publica.
     * @return usuario actualizado.
     */
    @Transactional
    public User updateProfile(User user, String username, String description) {
        String normalizedUsername = username == null ? "" : username.trim();
        if (!StringUtils.hasText(normalizedUsername)) {
            throw new IllegalArgumentException("El nombre de usuario es obligatorio");
        }

        boolean usernameTaken = userRepository.findByUsername(normalizedUsername)
                .filter(existing -> !existing.getUserId().equals(user.getUserId()))
                .isPresent();
        if (usernameTaken) {
            throw new IllegalArgumentException("Ese nombre de usuario ya esta en uso");
        }

        user.setUsername(normalizedUsername);
        user.setDescription(description == null ? "" : description.trim());
        return userRepository.save(user);
    }
}

package org.vedruna.perfumia.security.service;

import java.time.LocalDate;
import java.util.NoSuchElementException;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.vedruna.perfumia.persistance.model.Rol;
import org.vedruna.perfumia.persistance.model.User;
import org.vedruna.perfumia.persistance.repository.RolRepository;
import org.vedruna.perfumia.persistance.repository.UserRepository;
import org.vedruna.perfumia.security.controller.dto.AuthResponseDTO;
import org.vedruna.perfumia.service.GoogleAuthService;
import org.vedruna.perfumia.service.dto.GoogleAccount;

import io.jsonwebtoken.JwtException;
import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
public class AuthService {

    private final UserRepository userRepo;
    private final RolRepository rolRepo;
    private final JWTServiceImpl jwtService;
    private final PasswordEncoder passwordEncoder;
    private final UserDetailsService userDetailsService;
    private final GoogleAuthService googleAuthService;

    public AuthResponseDTO login(User user) {
        User userEntity = userRepo.findByUsername(user.getUsername())
                .or(() -> userRepo.findByEmail(user.getUsername()))
                .orElseThrow(() -> new BadCredentialsException("Usuario o password incorrectos"));

        if (!passwordEncoder.matches(user.getPassword(), userEntity.getPassword())) {
            throw new BadCredentialsException("Usuario o password incorrectos");
        }

        return buildAuthResponse(userEntity);
    }

    public User register(User user) {
        Rol rol = rolRepo.findByRolName("USER").orElseThrow(
                () -> new NoSuchElementException("Rol not found"));

        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user.setCreateDate(LocalDate.now());
        user.setUserRol(rol);
        user.setAuthProvider("LOCAL");

        return userRepo.save(user);
    }

    public AuthResponseDTO googleLogin(String idToken) {
        GoogleAccount account = googleAuthService.verify(idToken);

        User user = userRepo.findByGoogleSubject(account.getSubject())
                .or(() -> userRepo.findByEmail(account.getEmail()))
                .orElseGet(() -> createGoogleUser(account));

        if (user.getGoogleSubject() == null) {
            user.setGoogleSubject(account.getSubject());
        }
        if (!StringUtils.hasText(user.getProfileImageUrl()) && StringUtils.hasText(account.getPictureUrl())) {
            user.setProfileImageUrl(account.getPictureUrl());
        }
        user.setAuthProvider("GOOGLE");
        userRepo.save(user);

        return buildAuthResponse(user);
    }

    public AuthResponseDTO refreshToken(String refreshToken) {
        final String username;
        try {
            username = jwtService.getUsernameFromRefreshToken(refreshToken);
        } catch (JwtException e) {
            throw new IllegalArgumentException("Refresh Token invalido: " + e.getMessage());
        }

        UserDetails userDetails = userDetailsService.loadUserByUsername(username);

        if (!jwtService.isRefreshTokenValid(refreshToken, userDetails)) {
            throw new IllegalArgumentException("Refresh Token expirado o no valido para el usuario.");
        }

        User userEntity = (User) userDetails;
        String newAccessToken = jwtService.generateAccessToken(userEntity);

        return AuthResponseDTO.builder()
                .accessToken(newAccessToken)
                .expiresIn(jwtService.getAccessTokenExpiresIn())
                .refreshToken(refreshToken)
                .build();
    }

    private User createGoogleUser(GoogleAccount account) {
        Rol rol = rolRepo.findByRolName("USER").orElseThrow(
                () -> new NoSuchElementException("Rol not found"));

        User user = new User();
        user.setEmail(account.getEmail());
        user.setUsername(buildUniqueUsername(account));
        user.setPassword(passwordEncoder.encode("GOOGLE-" + account.getSubject()));
        user.setDescription("Cuenta creada con Google");
        user.setProfileImageUrl(account.getPictureUrl());
        user.setCreateDate(LocalDate.now());
        user.setUserRol(rol);
        user.setGoogleSubject(account.getSubject());
        user.setAuthProvider("GOOGLE");
        return userRepo.save(user);
    }

    private String buildUniqueUsername(GoogleAccount account) {
        String base = account.getEmail().split("@")[0].replaceAll("[^A-Za-z0-9_]", "");
        if (base.isBlank()) {
            base = "googleuser";
        }

        String candidate = base;
        int counter = 1;
        while (userRepo.findByUsername(candidate).isPresent()) {
            counter++;
            candidate = base + counter;
        }
        return candidate;
    }

    private AuthResponseDTO buildAuthResponse(User user) {
        return new AuthResponseDTO(
                jwtService.generateAccessToken(user),
                jwtService.getAccessTokenExpiresIn(),
                jwtService.generateRefreshToken(user),
                null);
    }
}

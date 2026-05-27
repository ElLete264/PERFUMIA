package org.vedruna.perfumia.persistance.model;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;

/**
 * Entidad que representa un usuario y sus detalles de seguridad (implementa UserDetails).
 */
@Data
@Entity
@Table(name = "users")
public class User implements UserDetails {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    Integer userId;

    @Column(name = "username", unique = true, nullable = false)
    String username;

    @Column(name = "email", unique = true, nullable = false)
    String email;

    @Column(name = "password", nullable = false)
    String password;

    @Column(name = "auth_provider")
    String authProvider;

    @Column(name = "google_subject")
    String googleSubject;

    @Column(name = "description")
    String description;

    @Column(name = "profile_image_url")
    String profileImageUrl;

    @Column(name = "create_date")
    LocalDate createDate;

    //IMPORTANTE EAGER para que se cargue el rol en la consulta
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "roles_rol_id", referencedColumnName = "rol_id")
    Rol userRol;

    /**
     * Devuelve las autoridades (roles) concedidas al usuario.
     * La autoridad se construye a partir del nombre del rol de la base de datos.
     * 
     * NOTA: Por convención de Spring Security, los nombres de roles deben ir prefijados con 'ROLE_'.
     * Si el rolName es 'ADMIN', la autoridad será 'ROLE_ADMIN'.
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // Si el rol es nulo (aunque esto debería ser prevenido por la lógica de negocio/BD)
        if (this.userRol == null || this.userRol.getRolName() == null) {
            return Collections.emptyList();
        }
        
        // Mapea el nombre del rol a una autoridad de Spring Security
        // Se añade el prefijo "ROLE_" como buena práctica de Spring Security.
        String authorityName = "ROLE_" + this.userRol.getRolName().toUpperCase();
        
        return List.of(new SimpleGrantedAuthority(authorityName));
    }

    /**
     * Indica si la cuenta del usuario ha expirado.
     * @return true si la cuenta es válida (nunca expira en esta implementación).
     */
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    /**
     * Indica si la cuenta del usuario está bloqueada.
     * @return true si la cuenta no está bloqueada (nunca se bloquea en esta implementación).
     */
    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    /**
     * Indica si las credenciales (contraseña) del usuario han expirado.
     * @return true si las credenciales son válidas (nunca expiran en esta implementación).
     */
    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    /**
     * Indica si el usuario está habilitado o deshabilitado.
     * @return true si el usuario está habilitado (siempre habilitado en esta implementación).
     */
    @Override
    public boolean isEnabled() {
        return true;
    }
}


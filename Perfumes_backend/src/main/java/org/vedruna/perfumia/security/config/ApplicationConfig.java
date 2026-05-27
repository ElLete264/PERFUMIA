package org.vedruna.perfumia.security.config;

import java.util.NoSuchElementException;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.vedruna.perfumia.persistance.repository.UserRepository;

import lombok.AllArgsConstructor;

/**
 * Clase de configuración principal para definir los Beans relacionados 
 * con la seguridad de la aplicación (Spring Security).
 * 
 * Es responsable de configurar el gestor de autenticación, el proveedor 
 * de autenticación, el servicio de detalles de usuario y el codificador 
 * de contraseñas.
 */
@Configuration
@AllArgsConstructor
public class ApplicationConfig {

    /** Repositorio utilizado para acceder a los datos de los usuarios. */
    private final UserRepository userRepo;

    /**
     * Define el Bean de AuthenticationManager.
     * 
     * AuthenticationManager es el componente principal de Spring Security 
     * que delega la autenticación a los AuthenticationProviders.
     * 
     * @param config La configuración de autenticación proporcionada 
     * por Spring Security.
     * @return El gestor de autenticación configurado.
     * @throws Exception Si ocurre un error al obtener el gestor de 
     * autenticación.
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * Define el Bean de AuthenticationProvider.
     * 
     * Configura un DaoAuthenticationProvider, que es el encargado de 
     * buscar los detalles del usuario a través del UserDetailsService 
     * y de verificar la contraseña usando el PasswordEncoder.
     * 
     * @param config La configuración de autenticación (aunque no se 
     * utiliza directamente en este método, se mantiene para consistencia si 
     * se requiere en el futuro).
     * @return El proveedor de autenticación configurado.
     */
    @Bean
    public AuthenticationProvider authenticationProvider(AuthenticationConfiguration config) {
        // Crea un proveedor que usa nuestro UserDetailsService personalizado
        DaoAuthenticationProvider authenticationProvider = new DaoAuthenticationProvider(userDetailService());
        
        // Asigna el codificador de contraseñas
        authenticationProvider.setPasswordEncoder(passwordEncoder());
        
        return authenticationProvider;
    }

    /**
     * Define el Bean de UserDetailsService.
     * 
     * Este es un contrato de Spring Security que define cómo se carga 
     * la información del usuario (incluyendo roles y contraseña codificada) 
     * a partir de un nombre de usuario.
     * 
     * Aquí se implementa buscando al usuario por nombre de usuario en 
     * el UserRepository.
     * 
     * @return Una implementación de UserDetailsService.
     * @throws NoSuchElementException Si el usuario no es encontrado.
     */
    @Bean
    public UserDetailsService userDetailService() {
        return username -> userRepo.findByUsername(username)
                .or(() -> userRepo.findByEmail(username))
                .orElseThrow(() -> new NoSuchElementException("User not found"));
    }

    /**
     * Define el Bean de PasswordEncoder.
     * 
     * Especifica qué algoritmo se utilizará para codificar y verificar 
     * las contraseñas. BCryptPasswordEncoder es el estándar recomendado 
     * por Spring Security.
     * 
     * @return Una instancia de BCryptPasswordEncoder.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}

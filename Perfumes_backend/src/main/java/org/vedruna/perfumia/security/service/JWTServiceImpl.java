package org.vedruna.perfumia.security.service;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.vedruna.perfumia.persistance.model.User;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;

/**
 * Servicio de implementación para la creación, firma, y validación de tokens JWT.
 * Esta clase utiliza la librería io.jsonwebtoken (JJWT) para manejar tanto los 
 * Access Tokens (corta duración) como los Refresh Tokens (larga duración).
 */
@Service
public class JWTServiceImpl {

    /**
     * Clave secreta (Base64) utilizada para firmar y verificar el Access Token.
     * Inyectada desde la configuración de la aplicación (e.g., application.properties).
     */
    @Value("${auth.access-token-secret-key}")
    private String accessTokenSecretKey;

    /**
     * Tiempo de vida del Access Token en milisegundos.
     * Debe ser un periodo corto (máximo unos 3 minutos!).
     */
    @Value("${auth.access-token-expiration}")
    private Long accessTokenExpiration;

    /**
     * Clave secreta (Base64) utilizada para firmar y verificar el Refresh Token.
     * Se recomienda que sea distinta a la del Access Token por seguridad.
     */
    @Value("${auth.refresh-token-secret-key}")
    private String refreshTokenSecretKey;

    /**
     * Tiempo de vida del Refresh Token en milisegundos.
     * Debe ser un periodo largo (máximo unos 7 días!).
     */
    @Value("${auth.refresh-token-expiration}")
    private Long refreshTokenExpiration;

    /**
     * Convierte la clave secreta Base64 inyectada en un objeto SecretKey
     * compatible con JJWT (Keys.hmacShaKeyFor), listo para firmar y verificar tokens.
     *
     * @param secretKey La clave secreta en formato Base64.
     * @return La clave de firma como SecretKey.
     */
    public SecretKey getKey(String secretKey) {
        // 1. Decodifica la clave secreta de Base64 a un arreglo de bytes.
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        // 2. Crea una clave HMAC Sha lista para la firma.
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Método centralizado para la construcción de cualquier tipo de token JWT.
     * Asigna claims, sujeto, fechas de emisión/expiración y la firma.
     *
     * @param extraClaims Claims (datos adicionales) a incluir en el payload del token.
     * @param user Los detalles del usuario, utilizado para establecer el 'Subject' (username).
     * @param expirationTime El tiempo de vida del token en milisegundos.
     * @param secretKey La clave secreta para la firma (Access o Refresh).
     * @return El token JWT como una cadena compacta (header.payload.signature).
     */
    private String buildToken(Map<String, Object> extraClaims, UserDetails user, Long expirationTime, String secretKey) {
        return Jwts.builder()
                // 1. Agrega los claims personalizados.
                .claims(extraClaims)
                // 2. Establece el 'Subject' (identificador principal, usualmente el username).
                .subject(user.getUsername())
                // 3. Fecha de emisión ('iat').
                .issuedAt(new Date(System.currentTimeMillis()))
                // 4. Fecha de expiración ('exp').
                .expiration(new Date(System.currentTimeMillis() + expirationTime))
                // 5. Firma el token usando la clave secreta.
                .signWith(getKey(secretKey))
                // 6. Finaliza la construcción y compacta el token.
                .compact(); 
    }

    /**
     * Genera el Access Token, el cual se utiliza para acceder a los recursos protegidos de la API.
     * Este token contiene información relevante del usuario (ej. roles) y tiene una corta duración.
     *
     * @param user La entidad de usuario que será el sujeto del token.
     * @return El Access Token JWT.
     */
    public String generateAccessToken(User user) {
        // Se incluyen claims específicos (ej. email o roles) en el Access Token.
        return buildToken(Map.of(
            "email", user.getEmail()
            //, se pueden añadir más atributos del usuario como los authorities...
        ), user, accessTokenExpiration, accessTokenSecretKey);
    }

    /**
     * Genera el Refresh Token, el cual se utiliza únicamente para solicitar un nuevo Access Token.
     * Este token tiene una larga duración y generalmente no contiene claims detallados.
     *
     * @param user La entidad de usuario que será el sujeto del token.
     * @return El Refresh Token JWT.
     */
    public String generateRefreshToken(User user) {
        // El Refresh Token se mantiene simple (pocos claims) para minimizar el riesgo.
        return buildToken(new HashMap<>(), user, refreshTokenExpiration, refreshTokenSecretKey);
    }

    /**
     * Obtiene el payload completo (Claims) de un token después de verificar su firma y expiración.
     * Este es el método que lanza excepciones si el token es inválido o expirado.
     *
     * @param token El token JWT recibido (Access o Refresh).
     * @param secretKey La clave secreta correspondiente al token.
     * @return El objeto Claims (payload).
     */
    private Claims getAllClaims(String token, String secretKey) {
        return Jwts
                .parser()
                // Provee la clave secreta para verificar la firma del token.
                .verifyWith(getKey(secretKey))
                .build()
                // Parsea el token, verifica su firma y obtiene el payload.
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Método genérico para extraer un claim específico usando una función resolutora.
     *
     * @param token El token JWT.
     * @param claimsResolver Función que define cómo extraer el valor (T) de los Claims.
     * @param secretKey La clave secreta (Access o Refresh) a utilizar.
     * @param <T> El tipo de dato del claim a retornar.
     * @return El valor del claim extraído.
     */
    public <T> T getClaim(String token, Function<Claims, T> claimsResolver, String secretKey) {
        // 1. Obtiene todos los claims (implica verificación de firma y expiración).
        final Claims claims = getAllClaims(token, secretKey);
        // 2. Aplica la función resolutora para obtener el claim deseado.
        return claimsResolver.apply(claims);
    }

    /**
     * Obtiene el nombre de usuario (Subject) del Access Token.
     * Lanza JwtException si el token es inválido.
     */
    public String getUsernameFromAccessToken(String token) {
        // Extrae el claim 'Subject' (Claims::getSubject) usando la clave de Access Token.
        return getClaim(token, Claims::getSubject, accessTokenSecretKey);
    }

    /**
     * Obtiene el tiempo de expiración (en segundos) configurado para el Access Token.
     * Utilizado para el campo 'expires_in' del DTO de respuesta.
     */
    public Long getAccessTokenExpiresIn() {
        return accessTokenExpiration/1000;
    }

    /**
     * Obtiene la fecha de expiración ('exp') del Access Token.
     */
    private Date getAccessTokenExpiration(String token) {
        // Extrae el claim 'Expiration' (Claims::getExpiration).
        return getClaim(token, Claims::getExpiration, accessTokenSecretKey);
    }

    /**
     * Verifica si el Access Token ha caducado.
     */
    private boolean isAccessTokenExpired(String token) {
        // Compara la fecha de expiración del token con la fecha y hora actuales.
        return getAccessTokenExpiration(token).before(new Date());
    }

    /**
     * Realiza la validación lógica final del Access Token.
     *
     * @param token El Access Token JWT a validar.
     * @param userDetails Los detalles del usuario cargados por el UserDetailsService.
     * @return true si el token pertenece al usuario y no ha expirado.
     */
    public boolean isAccessTokenValid(String token, UserDetails userDetails) {
        // 1. Obtiene el nombre de usuario del token.
        final String username = getUsernameFromAccessToken(token);

        // 2. Verifica que el username coincida y que el token NO haya expirado.
        return (username.equals(userDetails.getUsername()) && !isAccessTokenExpired(token));
    }

    /**
     * Obtiene el nombre de usuario (Subject) del Refresh Token.
     * Lanza JwtException si el token es inválido.
     */
    public String getUsernameFromRefreshToken(String token) {
        // Extrae el claim 'Subject' (Claims::getSubject) usando la clave de Refresh Token.
        return getClaim(token, Claims::getSubject, refreshTokenSecretKey);
    }

    /**
     * Obtiene el tiempo de expiración (en segundos) configurado para el Refresh Token.
     */
    public Long getRefreshTokenExpiresIn() {
        return refreshTokenExpiration/1000;
    }

    /**
     * Obtiene la fecha de expiración ('exp') del Refresh Token.
     */
    private Date getRefreshTokenExpiration(String token) {
        // Extrae el claim 'Expiration' (Claims::getExpiration) usando la clave de Refresh Token.
        return getClaim(token, Claims::getExpiration, refreshTokenSecretKey);
    }

    /**
     * Verifica si el Refresh Token ha caducado.
     */
    private boolean isRefreshTokenExpired(String token) {
        // Compara la fecha de expiración del token con la fecha y hora actuales.
        return getRefreshTokenExpiration(token).before(new Date());
    }

    /**
     * Realiza la validación lógica final del Refresh Token.
     *
     * @param token El Refresh Token JWT a validar.
     * @param userDetails Los detalles del usuario cargados por el UserDetailsService.
     * @return true si el token pertenece al usuario y no ha expirado.
     */
    public boolean isRefreshTokenValid(String token, UserDetails userDetails) {
        // 1. Obtiene el nombre de usuario del token.
        final String username = getUsernameFromRefreshToken(token);

        // 2. Verifica que el username coincida y que el token NO haya expirado.
        return (username.equals(userDetails.getUsername()) && !isRefreshTokenExpired(token));
    }
}



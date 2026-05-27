package org.vedruna.perfumia.controller;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Manejador global de errores REST.
 *
 * Centraliza las respuestas de error para evitar devolver trazas tecnicas al
 * frontend y mantener mensajes consistentes en la API.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    /**
     * Convierte errores de credenciales incorrectas en una respuesta 401.
     *
     * @param ex excepcion lanzada durante la autenticacion.
     * @return respuesta con mensaje de error de autenticacion.
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, String>> handleBadCredentials(BadCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("message", ex.getMessage()));
    }

    /**
     * Agrupa errores de validacion de DTOs y los devuelve por campo.
     *
     * @param ex excepcion de validacion generada por Bean Validation.
     * @return respuesta 400 con mapa de campos no validos.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        error -> error.getField(),
                        error -> error.getDefaultMessage() == null ? "Valor no valido" : error.getDefaultMessage(),
                        (first, second) -> first,
                        LinkedHashMap::new));

        return ResponseEntity.badRequest().body(Map.of(
                "message", "Hay campos no validos",
                "errors", fieldErrors));
    }

    /**
     * Devuelve como 400 los errores de argumentos no validos controlados por la
     * aplicacion.
     *
     * @param ex excepcion con el mensaje de negocio.
     * @return respuesta 400 con mensaje claro para el cliente.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest()
                .body(Map.of("message", ex.getMessage()));
    }

    /**
     * Transforma busquedas sin resultado en una respuesta 404.
     *
     * @param ex excepcion lanzada cuando no se encuentra un recurso.
     * @return respuesta 404 con mensaje de recurso no encontrado.
     */
    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(NoSuchElementException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("message", ex.getMessage()));
    }

    /**
     * Controla conflictos de unicidad o integridad de base de datos.
     *
     * @param ex excepcion de integridad generada por Spring Data.
     * @return respuesta 409 sin exponer detalles internos de la base de datos.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleDataIntegrity(DataIntegrityViolationException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("message", "Ya existe un registro con esos datos"));
    }
}

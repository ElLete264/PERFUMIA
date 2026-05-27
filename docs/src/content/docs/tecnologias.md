---
title: Tecnologias
description: Stack tecnico usado en PerfumIA
---

## Backend

- Java 17.
- Spring Boot 3.5.
- Spring Web para API REST.
- Spring Security para autenticacion y autorizacion.
- JWT con `jjwt` para access token y refresh token.
- Spring Data JPA / Hibernate para persistencia.
- Spring Validation para validar DTOs.
- MySQL como base de datos relacional.
- Swagger / OpenAPI con `springdoc-openapi`.
- Gemini API para apoyo conversacional.
- Fragella API para busqueda de perfumes.
- JUnit y Mockito para pruebas.

## Frontend

- React 19 como estructura principal.
- Vue 3 para el componente del chat olfativo.
- Vite como herramienta de desarrollo y build.
- Bootstrap 5 como framework CSS.
- React Bootstrap como libreria de componentes.
- Lucide React para iconos.
- CSS responsive mobile-first.
- Cloudinary unsigned upload para foto de perfil.

## Documentacion

- Astro.
- Starlight.
- Diagramas en sintaxis Mermaid dentro de las paginas tecnicas.

## Herramientas

- Maven.
- npm.
- MySQL Workbench.
- PowerShell.
- Git para versionado y entrega en repositorio.

## Decisiones tecnicas importantes

El recomendador no usa azar. Las recomendaciones se ordenan mediante scoring determinista, combinando perfil actual, notas, temporada, ocasion, presupuesto, mood, historial de aceptaciones y rechazos.

Gemini mejora la conversacion, pero no sustituye la logica de negocio. El backend normaliza y valida lo que se usa para recomendar.

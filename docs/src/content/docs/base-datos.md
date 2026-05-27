---
title: Base de datos
description: Modelo de datos de PerfumIA
---

La base de datos principal se llama `perfumia` y se ejecuta sobre MySQL.

## Tablas principales

- `roles`: roles disponibles para seguridad.
- `users`: usuarios registrados, credenciales, proveedor de autenticacion, descripcion e imagen.
- `perfume_profiles`: preferencias olfativas del usuario.
- `chat_messages`: historial de conversacion con PerfumIA.
- `perfume_recommendations`: recomendaciones generadas y su estado.
- `community_messages`: mensajes publicos de comunidad.

## Relaciones principales

- Un rol puede pertenecer a muchos usuarios.
- Un usuario tiene un perfil olfativo.
- Un usuario tiene muchos mensajes de chat.
- Un usuario tiene muchas recomendaciones.
- Un usuario tiene muchos mensajes de comunidad.

## Integridad

La base incluye:

- claves primarias autoincrementales,
- claves foraneas con `ON DELETE CASCADE` en datos dependientes del usuario,
- indices unicos para `username`, `email` y `google_subject`,
- indices compuestos para consultas frecuentes,
- constraint de rating entre 1 y 5,
- trigger `trg_chat_messages_before_insert`,
- procedimiento `sp_accept_recommendation`,
- migraciones SQL para actualizar bases existentes.

## Trigger

`trg_chat_messages_before_insert` valida que `role_name` sea `USER`, `ASSISTANT` o `SYSTEM`. Tambien rellena `create_date` con `NOW()` si llega vacio.

## Procedimiento

`sp_accept_recommendation` acepta una recomendacion solo si existe y pertenece al usuario indicado. Esto evita aceptar recomendaciones de otros usuarios.

## Estado de recomendaciones

El campo `accepted` usa tres estados:

- `NULL`: recomendacion rechazada.
- `0`: recomendacion pendiente.
- `1`: recomendacion aceptada.

## Historial en el recomendador

El motor de scoring usa el historial:

- refuerza notas, marcas y estilos aceptados,
- penaliza notas o perfumes rechazados,
- evita repetir opciones descartadas,
- mantiene el resultado determinista y explicable.

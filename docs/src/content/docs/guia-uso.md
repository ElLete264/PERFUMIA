---
title: Guia de uso
description: Flujo principal para utilizar PerfumIA
---

## Registro e inicio de sesion

El usuario puede registrarse con credenciales propias o iniciar sesion con Google si `GOOGLE_CLIENT_ID` y `VITE_GOOGLE_CLIENT_ID` estan configurados.

Tras iniciar sesion, el frontend guarda el access token y el refresh token. Si el access token caduca, la aplicacion intenta renovar la sesion.

## Perfil visual

El perfil muestra:

- avatar o foto de perfil,
- nombre de usuario,
- descripcion,
- estilo olfativo calculado desde recomendaciones,
- recomendaciones guardadas,
- favoritos y puntuaciones.

La foto de perfil se sube a Cloudinary desde el frontend. El backend guarda solo la URL segura.

## Chat con PerfumIA

El usuario escribe de forma natural. PerfumIA puede preguntar por:

- genero objetivo,
- estacion,
- intensidad,
- ocasion,
- presupuesto,
- notas favoritas,
- notas rechazadas,
- estilo o mood.

Ejemplo:

```txt
Usuario: no entiendo de perfumes, pero quiero uno que huela a fresa
PerfumIA: Me falta saber para quien lo orientamos: hombre, mujer o unisex?
Usuario: para hombre
PerfumIA: Para afinarlo, lo buscas para diario, trabajo, citas o noche?
Usuario: para salir de noche y premium
```

## Recomendaciones Top 3

Cuando el perfil tiene suficiente informacion, PerfumIA devuelve hasta tres perfumes ordenados por compatibilidad.

Cada card incluye:

- marca,
- nombre,
- imagen o iniciales,
- descripcion,
- notas principales,
- temporada,
- precio aproximado,
- favorito,
- rating,
- explicacion "Por que encaja",
- acciones de guardar o descartar.

## Comunidad

La vista de comunidad permite publicar mensajes publicos, consultar perfiles de otros usuarios y ver rankings de perfumes mejor y peor valorados.

## Movil

La interfaz esta preparada para movil. El login ocupa la pantalla completa sin recortar, la parte visual de escritorio se oculta y los paneles principales se adaptan para evitar scroll horizontal.

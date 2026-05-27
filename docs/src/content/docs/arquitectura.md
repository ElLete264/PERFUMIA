---
title: Arquitectura
description: Organizacion tecnica de PerfumIA
---

PerfumIA se divide en tres bloques principales:

```txt
APP_PERFUMES/
  Perfumes_backend/   API REST con Spring Boot
  Perfumes_Front/     Frontend React + Vue con Vite
  docs/               Documentacion Starlight
```

## Backend

El backend usa arquitectura por capas:

- `controller`: endpoints REST.
- `service`: logica de negocio e integraciones externas.
- `persistance/model`: entidades JPA.
- `persistance/repository`: acceso a datos.
- `controller/dto` y `security/controller/dto`: objetos de entrada y salida.
- `security`: JWT, filtros, autenticacion y configuracion.
- `resources/db/mysql`: schema, datos iniciales y migraciones.

## Servicios principales

- `PerfumeAdvisorService`: flujo principal del chat y recomendacion.
- `AiDecisionService`: deteccion de parametros olfativos desde texto libre.
- `GeminiService`: llamadas a Gemini en modo texto y modo JSON.
- `PerfumeCatalogService`: busqueda en Fragella y catalogo local fallback.
- `PerfumeScoringService`: ranking determinista de perfumes.
- `PerfumeBudgetClassifier`: clasificacion coherente de presupuesto.
- `RecommendationPersistenceService`: persistencia de mensajes, recomendaciones, favoritos y ratings.
- `ProfileService`: actualizacion de perfil e imagen.
- `CommunityChatService`: mensajes y perfiles publicos de comunidad.

## Frontend

React controla la aplicacion principal: autenticacion, sesion, perfil, comunidad, paneles y tema. Vue controla el chat olfativo en `PerfumeChat.vue`.

La integracion entre ambos se hace mediante `VueChatMount.jsx`, que monta una app Vue dentro de un contenedor React y le pasa token, URL de API y callbacks.

## Flujo de recomendacion

1. El usuario envia un mensaje.
2. El backend guarda el mensaje en `chat_messages`.
3. Se carga o crea el `PerfumeProfile`.
4. `AiDecisionService` extrae preferencias.
5. Gemini puede guiar la siguiente pregunta o estructurar una decision.
6. Si faltan datos importantes, PerfumIA pregunta una cosa mas.
7. Si hay datos suficientes, se busca en Fragella o catalogo local.
8. `PerfumeScoringService` filtra y ordena candidatos.
9. Se guardan hasta tres recomendaciones pendientes.
10. El frontend muestra el Top 3 y permite guardar, descartar, marcar favorito o puntuar.

## Seguridad

La API usa JWT en modo stateless. Las rutas `/auth/**`, Swagger y `/public` son publicas. El resto requiere token valido. El filtro JWT valida el token y carga el usuario autenticado para que los controladores trabajen siempre con el usuario real.

---
title: Casos de prueba
description: Pruebas manuales y automaticas del proyecto
---

## Introduccion

Los casos de prueba verifican que PerfumIA funciona como aplicacion completa: autenticacion, autorizacion, chat, recomendaciones, perfil, comunidad, responsive y documentacion.

## Pruebas manuales

| Caso | Pasos | Resultado esperado |
| --- | --- | --- |
| Registro correcto | Crear cuenta nueva | Se crea el usuario y permite iniciar sesion. |
| Registro con email invalido | Enviar email no valido | Se muestra error de validacion. |
| Login correcto | Enviar usuario y contrasena | Devuelve JWT y carga la app. |
| Login incorrecto | Enviar credenciales falsas | Muestra error sin iniciar sesion. |
| Login con Google | Usar boton de Google | Backend valida token y genera JWT propio. |
| Ruta protegida sin token | Llamar endpoint privado | Devuelve 401/403. |
| Refresh token | Caducar access token | La sesion se renueva si el refresh token es valido. |
| Chat con Gemini | Escribir preferencia natural | Responde de forma conversacional. |
| Chat sin Gemini | Quitar clave o agotar cuota | Usa fallback local sin romper. |
| Fragella sin cuota | Simular API no disponible | Usa catalogo local. |
| Busqueda premium | Pedir perfume premium | Evita opciones claramente economicas/medias si hay alternativas premium. |
| Busqueda por genero | Pedir perfume para hombre | Penaliza o filtra perfumes claramente femeninos. |
| Notas rechazadas | Indicar una nota no deseada | Evita perfumes que contengan esa nota. |
| Recomendacion Top 3 | Completar perfil | Devuelve hasta 3 opciones ordenadas. |
| Aceptar recomendacion | Pulsar guardar | Cambia estado y aparece en perfil. |
| Descartar recomendacion | Pulsar descartar | Queda marcada como rechazada y se evita repetirla. |
| Favorito y rating | Marcar estrella/rating | Se guarda el valor y se refleja en perfil. |
| Foto de perfil | Subir imagen valida | Cloudinary devuelve URL y backend la guarda. |
| Chat comunidad | Enviar mensaje publico | Aparece en la lista de comunidad. |
| Perfil comunidad | Abrir usuario desde comunidad | Muestra datos publicos y recomendaciones visibles. |
| Movil login | Abrir en 360x640 | Ocupa ancho y alto sin recortar ni scroll horizontal. |
| Movil app | Usar chat y cards | La app es usable desde movil. |

## Tests automaticos

| Test | Cobertura |
| --- | --- |
| `PerfumiaApplicationTests` | Arranque del contexto Spring. |
| `AiDecisionServiceTest` | Intenciones, genero, estacion, presupuesto, notas, notas rechazadas y mood. |
| `PerfumeAdvisorServiceTest` | Flujo del chat, Gemini guiado, saludos con preferencias y busqueda con defaults. |
| `PerfumeScoringServiceTest` | Scoring, Top 3, genero, temporada, presupuesto, historial y razones. |
| `PromptBuilderServiceTest` | Preguntas locales y respuestas aclaratorias. |
| `RecommendationPersistenceServiceTest` | Guardado de recomendaciones, duplicados, favoritos y ratings. |

## Comandos

Backend:

```powershell
cd Perfumes_backend
.\mvnw.cmd clean test
```

Frontend:

```powershell
cd Perfumes_Front
npm run build
```

Documentacion:

```powershell
cd docs
npm run build
```

## Resultado actual

- Backend: 151 tests pasando.
- Frontend: build correcto.
- Documentacion: build correcto.
- Responsive movil revisado en 360x640 y 390x844.

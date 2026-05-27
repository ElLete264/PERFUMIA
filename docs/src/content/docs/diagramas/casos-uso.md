---
title: Diagrama de casos de uso
description: Actores y funcionalidades principales de PerfumIA
---

## Diagrama

```mermaid
flowchart LR
  Usuario[Usuario registrado]
  Visitante[Visitante]
  Profesor[Profesor evaluador]

  UC1((Registrarse))
  UC2((Iniciar sesion))
  UC3((Iniciar sesion con Google))
  UC4((Conversar con PerfumIA))
  UC5((Recibir Top 3))
  UC6((Guardar recomendacion))
  UC7((Descartar recomendacion))
  UC8((Puntuar y marcar favorito))
  UC9((Editar perfil))
  UC10((Subir foto de perfil))
  UC11((Usar chat de comunidad))
  UC12((Consultar documentacion))
  UC13((Ver Swagger))

  Visitante --> UC1
  Visitante --> UC2
  Visitante --> UC3

  Usuario --> UC4
  Usuario --> UC5
  Usuario --> UC6
  Usuario --> UC7
  Usuario --> UC8
  Usuario --> UC9
  Usuario --> UC10
  Usuario --> UC11

  Profesor --> UC12
  Profesor --> UC13
  Profesor --> UC2
  Profesor --> UC4
```

## Actores

| Actor | Descripcion |
| --- | --- |
| Visitante | Persona que aun no ha iniciado sesion. Puede registrarse o entrar. |
| Usuario registrado | Persona autenticada que usa el asesor, perfil, recomendaciones y comunidad. |
| Profesor evaluador | Persona que revisa el proyecto, documentacion, Swagger y demo. |

## Casos principales

El caso de uso central es conversar con PerfumIA hasta generar recomendaciones. Alrededor de ese flujo aparecen acciones secundarias: guardar, descartar, puntuar, marcar favorito, editar perfil y participar en comunidad.

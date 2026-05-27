---
title: Diagrama de actividades
description: Flujo de actividad para obtener recomendaciones
---

## Diagrama

```mermaid
flowchart TD
  A([Inicio]) --> B[Usuario inicia sesion]
  B --> C[Abre asesor olfativo]
  C --> D[Escribe mensaje]
  D --> E[Backend guarda mensaje]
  E --> F[Actualizar perfil olfativo]
  F --> G{Faltan datos bloqueantes?}
  G -->|Si| H[Generar pregunta guiada con Gemini o fallback]
  H --> I[Mostrar pregunta al usuario]
  I --> D
  G -->|No| J[Construir consulta de busqueda]
  J --> K{Fragella disponible?}
  K -->|Si| L[Buscar en Fragella]
  K -->|No| M[Usar catalogo local]
  L --> N[Filtrar candidatos]
  M --> N
  N --> O[Puntuar por scoring determinista]
  O --> P[Guardar hasta 3 recomendaciones pendientes]
  P --> Q[Mostrar Top 3 en frontend]
  Q --> R{Usuario decide}
  R -->|Guardar| S[Aceptar recomendacion]
  R -->|Descartar| T[Marcar como rechazada]
  R -->|Puntuar/favorito| U[Actualizar rating o favorito]
  S --> V([Fin])
  T --> V
  U --> V
```

## Explicacion

La actividad central es iterativa. Mientras faltan datos importantes, PerfumIA pregunta una sola cosa mas. Cuando el perfil es suficiente, se consulta catalogo, se filtra, se puntua y se devuelve el Top 3.

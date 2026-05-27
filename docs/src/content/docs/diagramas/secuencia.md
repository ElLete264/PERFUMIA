---
title: Diagrama de secuencia
description: Interaccion entre frontend, backend, Gemini, Fragella y base de datos
---

## Diagrama

```mermaid
sequenceDiagram
  actor U as Usuario
  participant F as Frontend React/Vue
  participant C as ChatController
  participant A as PerfumeAdvisorService
  participant D as AiDecisionService
  participant G as GeminiService
  participant P as PerfumeCatalogService
  participant S as PerfumeScoringService
  participant R as RecommendationPersistenceService
  participant DB as MySQL
  participant API as Fragella API

  U->>F: Escribe mensaje
  F->>C: POST /chat con JWT
  C->>A: chat(user, message)
  A->>R: saveMessage(USER)
  R->>DB: INSERT chat_messages
  A->>DB: cargar/crear PerfumeProfile
  A->>D: updateProfileFromMessage()
  D-->>A: perfil actualizado
  A->>G: pregunta guiada o decision JSON
  G-->>A: respuesta o null si no disponible

  alt faltan datos
    A->>R: saveMessage(ASSISTANT)
    R->>DB: INSERT chat_messages
    A-->>C: pregunta siguiente
    C-->>F: ChatResponseDTO
    F-->>U: Muestra pregunta
  else perfil suficiente
    A->>P: searchPerfumes(query)
    P->>API: buscar perfumes
    API-->>P: resultados o error/cuota
    P-->>A: catalogo Fragella o fallback local
    A->>S: chooseTopPerfumes()
    S-->>A: Top 3 ordenado
    A->>R: savePendingRecommendations()
    R->>DB: INSERT/UPDATE perfume_recommendations
    A-->>C: ChatResponseDTO con propuestas
    C-->>F: JSON
    F-->>U: Muestra Top 3
  end
```

## Puntos clave

Gemini participa en la conversacion, pero la seleccion final pasa por servicios propios. Esto evita que una respuesta generativa rompa las reglas de presupuesto, genero, temporada o historial.

---
title: Diagrama de clases
description: Entidades y servicios principales del backend
---

## Diagrama

```mermaid
classDiagram
  class User {
    Integer userId
    String username
    String email
    String password
    String authProvider
    String googleSubject
    String description
    String profileImageUrl
    LocalDate createDate
    getAuthorities()
  }

  class Rol {
    Integer rolId
    String rolName
  }

  class PerfumeProfile {
    Integer profileId
    String genderTarget
    String season
    String intensity
    String preferredNotes
    String dislikedNotes
    String occasion
    String budget
    String lastSummary
  }

  class ChatMessage {
    Integer messageId
    String roleName
    String content
    LocalDateTime createDate
  }

  class PerfumeRecommendation {
    Integer recommendationId
    String perfumeName
    String brand
    String description
    String season
    String notes
    String source
    String imageUrl
    Boolean accepted
    Boolean favorite
    Integer rating
    String fragellaRating
    LocalDateTime createDate
  }

  class CommunityMessage {
    Integer messageId
    String content
    LocalDateTime createDate
  }

  class PerfumeAdvisorService
  class AiDecisionService
  class GeminiService
  class PerfumeCatalogService
  class PerfumeScoringService
  class PerfumeBudgetClassifier
  class RecommendationPersistenceService
  class AuthService

  Rol "1" --> "*" User
  User "1" --> "0..1" PerfumeProfile
  User "1" --> "*" ChatMessage
  User "1" --> "*" PerfumeRecommendation
  User "1" --> "*" CommunityMessage

  PerfumeAdvisorService --> AiDecisionService
  PerfumeAdvisorService --> GeminiService
  PerfumeAdvisorService --> PerfumeCatalogService
  PerfumeAdvisorService --> PerfumeScoringService
  PerfumeAdvisorService --> RecommendationPersistenceService
  PerfumeScoringService --> PerfumeBudgetClassifier
  AuthService --> User
```

## Lectura del diagrama

Las clases de entidad representan el modelo persistente. Los servicios contienen la logica de negocio. `PerfumeAdvisorService` es el orquestador del flujo principal y delega en servicios especializados para IA, catalogo, scoring y persistencia.

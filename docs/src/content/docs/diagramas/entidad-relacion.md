---
title: Diagrama Entidad-Relacion
description: Modelo relacional de MySQL
---

## Diagrama

```mermaid
erDiagram
  ROLES ||--o{ USERS : asigna
  USERS ||--o| PERFUME_PROFILES : tiene
  USERS ||--o{ CHAT_MESSAGES : escribe
  USERS ||--o{ PERFUME_RECOMMENDATIONS : recibe
  USERS ||--o{ COMMUNITY_MESSAGES : publica

  ROLES {
    int rol_id PK
    varchar rol_name UK
  }

  USERS {
    int user_id PK
    varchar username UK
    varchar email UK
    char password
    varchar auth_provider
    varchar google_subject UK
    longtext description
    varchar profile_image_url
    date create_date
    int roles_rol_id FK
  }

  PERFUME_PROFILES {
    int profile_id PK
    int user_id FK
    varchar gender_target
    varchar season
    varchar intensity
    varchar preferred_notes
    varchar disliked_notes
    varchar occasion
    varchar budget
    varchar last_summary
  }

  CHAT_MESSAGES {
    int message_id PK
    int user_id FK
    varchar role_name
    text content
    datetime create_date
  }

  PERFUME_RECOMMENDATIONS {
    int recommendation_id PK
    int user_id FK
    varchar perfume_name
    varchar brand
    text description
    varchar season
    varchar notes
    varchar source
    varchar image_url
    tinyint accepted
    tinyint favorite
    int rating
    varchar fragella_rating
    datetime create_date
  }

  COMMUNITY_MESSAGES {
    int message_id PK
    int user_id FK
    varchar content
    datetime create_date
  }
```

## Integridad

El modelo usa claves foraneas para que los datos dependan siempre de un usuario real. En las tablas de historial, recomendaciones y comunidad se usa `ON DELETE CASCADE` para limpiar datos del usuario si se elimina su cuenta.

Ademas, se incluyen indices compuestos para optimizar las consultas mas frecuentes por usuario, fecha, estado y perfume.

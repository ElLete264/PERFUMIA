---
title: Diagrama de componentes
description: Componentes principales y dependencias externas
---

## Diagrama

```mermaid
flowchart LR
  subgraph Cliente["Cliente web"]
    Browser["Navegador movil/escritorio"]
    React["React App"]
    Vue["Vue PerfumeChat"]
    CSS["Bootstrap + CSS responsive"]
    Browser --> React
    React --> Vue
    React --> CSS
  end

  subgraph Backend["Spring Boot API"]
    Controllers["REST Controllers"]
    Security["Spring Security + JWT"]
    Services["Servicios de negocio"]
    Repositories["Repositorios JPA"]
    Swagger["Swagger/OpenAPI"]
    Controllers --> Security
    Controllers --> Services
    Services --> Repositories
  end

  subgraph Datos["Persistencia"]
    MySQL["MySQL perfumia"]
  end

  subgraph Externos["Servicios externos"]
    Gemini["Gemini API"]
    Fragella["Fragella API"]
    Google["Google Token Info"]
    Cloudinary["Cloudinary Upload"]
  end

  React -->|HTTP JSON| Controllers
  Vue -->|HTTP JSON| Controllers
  Repositories --> MySQL
  Services --> Gemini
  Services --> Fragella
  Security --> Google
  React --> Cloudinary
  Swagger --> Controllers
```

## Componentes destacados

- React administra aplicacion, sesion, perfil y comunidad.
- Vue administra el chat, las cards y el auto-scroll.
- Spring Boot expone endpoints REST protegidos.
- MySQL mantiene usuarios, perfiles, mensajes y recomendaciones.
- Gemini mejora la conversacion.
- Fragella aporta catalogo externo.
- Cloudinary aloja la imagen de perfil.

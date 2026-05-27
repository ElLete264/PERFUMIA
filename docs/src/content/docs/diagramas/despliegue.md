---
title: Diagrama de despliegue
description: Vista de despliegue objetivo para PerfumIA
---

## Diagrama

```mermaid
flowchart TB
  subgraph Usuario["Dispositivo del usuario"]
    Mobile["Movil"]
    Desktop["PC"]
  end

  subgraph HostingFront["Hosting frontend"]
    Front["Build Vite: HTML/CSS/JS"]
  end

  subgraph HostingBack["Servidor backend"]
    Spring["Spring Boot API"]
    Logs["logs/app.log"]
  end

  subgraph Database["Servidor BBDD"]
    MySQL["MySQL perfumia"]
  end

  subgraph Docs["Hosting documentacion"]
    Starlight["Astro Starlight"]
  end

  subgraph APIs["Servicios externos"]
    Gemini["Gemini API"]
    Fragella["Fragella API"]
    Google["Google OAuth tokeninfo"]
    Cloudinary["Cloudinary"]
  end

  Mobile --> Front
  Desktop --> Front
  Mobile --> Starlight
  Desktop --> Starlight
  Front -->|HTTPS JSON| Spring
  Spring --> MySQL
  Spring --> Logs
  Spring --> Gemini
  Spring --> Fragella
  Spring --> Google
  Front --> Cloudinary
```

## Despliegue objetivo

- El frontend se publica como build estatico de Vite.
- El backend se ejecuta como aplicacion Spring Boot con variables de entorno.
- MySQL se ejecuta en un servicio de base de datos persistente.
- Starlight se puede publicar como documentacion estatica.
- Google, Gemini, Fragella y Cloudinary se configuran con claves reales y dominios autorizados.

## Requisito de entrega

El profesor debe poder abrir la aplicacion desde el movil mediante una URL publica. Antes de entregar, prueba el enlace desde datos moviles o desde otro dispositivo para confirmar que no depende de `localhost`.

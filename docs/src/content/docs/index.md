---
title: PerfumIA
description: Proyecto final DAW - asesor olfativo con IA
template: splash
hero:
  tagline: Proyecto final de Desarrollo de Aplicaciones Web
  actions:
    - text: Ver documentacion
      link: /introduccion/
      icon: right-arrow
    - text: Cumplimiento DAW
      link: /cumplimiento-daw/
      variant: minimal
---

**PerfumIA** es una aplicacion web que funciona como asesor olfativo inteligente. El usuario conversa con PerfumIA, explica sus gustos y recibe recomendaciones de perfumes ordenadas por compatibilidad.

**Ciclo:** Desarrollo de Aplicaciones Web (DAW)  
**Alumno/a:** CARLOS PÉREZ LARA  
**Repositorio:** [https://github.com/ElLete264/PERFUMIA.git](https://github.com/ElLete264/PERFUMIA.git)  
**Despliegue:** [http://135.225.93.190/](http://135.225.93.190/)  
**Figma:** [PERFUMIA](https://www.figma.com/design/MhdWeqJJPyEkn8T02pvqhC/PERFUMIA?node-id=0-1&t=dQ4AiaU9o0KblHoK-1)

## Resumen ejecutivo

PerfumIA no es una tienda online. Es una aplicacion de recomendacion personalizada: combina una interfaz premium, un chat con IA, un perfil olfativo persistente y un motor propio de scoring para explicar cada recomendacion.

El usuario no necesita saber de perfumes. Puede escribir preferencias naturales y el sistema las convierte en criterios tecnicos como notas, temporada, ocasion, intensidad, genero objetivo y presupuesto.

## Que demuestra el proyecto

- Frontend responsive mobile-first con React, Vue, Vite, Bootstrap y componentes.
- Backend Spring Boot con autenticacion, autorizacion y JWT.
- Base de datos MySQL con relaciones, indices, trigger y procedimiento.
- Integracion con Gemini, Fragella y Cloudinary.
- Motor de recomendacion determinista, explicable y probado.
- Documentacion completa con diagramas y casos de prueba.

## Partes diferenciales

- Chat olfativo guiado por Gemini con fallback local.
- Busqueda en Fragella con catalogo local de respaldo.
- Scoring explicable para evitar recomendaciones incoherentes.
- Perfil izquierdo con avatar Cloudinary, favoritos, ratings y mejores valorados.
- Comunidad con mensajes publicos y perfiles consultables.
- Documentacion Starlight con diagramas de casos de uso, clases, entidad-relacion, componentes, actividades, secuencia y despliegue objetivo.

## Flujo principal

1. El usuario se registra o inicia sesion.
2. Habla con PerfumIA en lenguaje natural.
3. El backend transforma sus respuestas en un perfil olfativo.
4. Gemini ayuda a preguntar o conversar.
5. Fragella o el catalogo local aportan perfumes candidatos.
6. El scoring filtra y ordena por genero, ocasion, temporada, notas, presupuesto e historial.
7. El frontend muestra un Top 3 con razones personalizadas.

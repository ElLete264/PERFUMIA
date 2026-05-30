---
title: Cumplimiento DAW
description: Checklist de requisitos minimos del proyecto
---

Esta pagina resume como PerfumIA cubre los requisitos minimos del proyecto final.

## Entregables

| Requisito | Estado | Evidencia |
| --- | --- | --- |
| Documentacion del proyecto final | Cumplido | README principal y documentacion Starlight en `docs/`. |
| Proyecto en formato zip | Generado localmente | `entrega/PerfumIA_DAW_entrega.zip`. No se versiona en GitHub para mantener limpio el repositorio. |
| Enlace del repositorio | Cumplido | `https://github.com/ElLete264/PERFUMIA.git`. |
| Enlace del proyecto desplegado | Cumplido | `https://perfumia-nine.vercel.app/`. |
| Exposicion o video de 15 minutos | Se acredita con el video | La prueba de este apartado es el propio video de defensa de 15 minutos. |

## Requisitos tecnicos

| Requisito | Estado | Evidencia |
| --- | --- | --- |
| Base de datos consistente | Cumplido | MySQL con claves, indices, `CHECK`, trigger y procedimiento. |
| Indices | Cumplido | Indices unicos y compuestos en `schema.sql`. |
| Funcion/procedimiento/trigger | Cumplido | `trg_chat_messages_before_insert` y `sp_accept_recommendation`. |
| Frontend mobile-first | Cumplido | CSS responsive, login movil sin recorte y paneles adaptados. |
| Framework CSS | Cumplido | Bootstrap 5. |
| Framework JS | Cumplido | React y Vue. |
| Libreria de componentes | Cumplido | React Bootstrap y Lucide React. |
| Backend con framework | Cumplido | Spring Boot. |
| Autenticacion y autorizacion | Cumplido | Spring Security + JWT + roles. |
| Metodos comentados | Cubierto en endpoints, servicios principales y funciones criticas | JavaDoc/comentarios en controladores y logica relevante. Recomendable revision final si el profesor exige comentario literal en cada metodo privado. |
| Control de versiones | Cumplido | Repositorio GitHub: `https://github.com/ElLete264/PERFUMIA.git`. |
| Proyecto desplegado | Cumplido | Frontend en Vercel y backend en Azure VM con Docker Compose y Azure MySQL. |

## Documentacion Starlight

| Requisito | Estado |
| --- | --- |
| Portada creativa con DAW, titulo y alumno | Cumplido. |
| Indice o NavBar ordenado | Cumplido mediante sidebar de Starlight. |
| Diagrama de casos de uso | Cumplido. |
| Diagrama de clases | Cumplido. |
| Diagrama Entidad-Relacion | Cumplido. |
| Diagrama de componentes | Cumplido. |
| Diagrama de actividades | Cumplido. |
| Diagrama de secuencia | Cumplido. |
| Diagrama de despliegue | Cumplido. |
| Casos de prueba | Cumplido. |

## Acciones finales antes de subir

- Completar el enlace publico de la documentacion si se publica Starlight.
- Regenerar el zip final si se cambia codigo o documentacion.
- Probar el enlace publico desde movil real antes de entregar.

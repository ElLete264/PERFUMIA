# PerfumIA - Asesor olfativo inteligente

**Proyecto final de Desarrollo de Aplicaciones Web (DAW)**

**Alumno/a:** CARLOS PÉREZ LARA  
**Repositorio:** [https://github.com/ElLete264/PERFUMIA.git](https://github.com/ElLete264/PERFUMIA.git)  
**Despliegue aplicacion:** [http://135.225.93.190/](http://135.225.93.190/)  
**Despliegue documentacion:** pendiente de URL publica o ejecucion local  
**Figma:** [PERFUMIA](https://www.figma.com/design/MhdWeqJJPyEkn8T02pvqhC/PERFUMIA?node-id=0-1&t=dQ4AiaU9o0KblHoK-1)  
**Documentacion Starlight:** [`docs/`](docs/)  

## Indice

- [Introduccion](#introduccion)
- [Justificacion](#justificacion)
- [Objetivos](#objetivos)
- [Funcionalidades](#funcionalidades)
- [Tecnologias utilizadas](#tecnologias-utilizadas)
- [Arquitectura resumida](#arquitectura-resumida)
- [Base de datos](#base-de-datos)
- [Instalacion](#instalacion)
- [Guia de uso](#guia-de-uso)
- [Verificacion y tests](#verificacion-y-tests)
- [Documentacion](#documentacion)
- [Enlaces del proyecto](#enlaces-del-proyecto)
- [Conclusion](#conclusion)
- [Contribuciones, agradecimientos y referencias](#contribuciones-agradecimientos-y-referencias)
- [Licencias](#licencias)
- [Contacto](#contacto)

## Introduccion

PerfumIA es una aplicacion web full stack que actua como asesor olfativo. El usuario puede registrarse, iniciar sesion y conversar con una IA para encontrar perfumes adecuados a sus gustos, presupuesto, ocasion, intensidad, estacion y notas favoritas o rechazadas.

La aplicacion no se limita a mostrar un catalogo. Primero conversa con el usuario, transforma sus respuestas en un perfil olfativo y despues busca perfumes en Fragella o en un catalogo local de respaldo. Finalmente ordena las opciones mediante un sistema de scoring determinista y muestra un Top 3 con una explicacion personalizada de por que encaja cada perfume.

## Justificacion

Elegir un perfume puede ser dificil para alguien que no conoce familias olfativas, notas, marcas o diferencias entre perfumes de dia, noche, verano o invierno. PerfumIA reduce esa dificultad con una experiencia guiada y cercana: el usuario habla con el asesor, no rellena un formulario rigido.

El proyecto permite demostrar competencias completas de DAW: frontend responsive mobile-first, backend con framework, seguridad, base de datos relacional consistente, integraciones externas, IA, pruebas, documentacion tecnica y preparacion para despliegue.

## Objetivos

- Crear una aplicacion web full stack funcional y defendible.
- Implementar registro, login local, refresh token y login con Google.
- Proteger rutas privadas mediante JWT y Spring Security.
- Usar MySQL con relaciones, indices, trigger y procedimiento.
- Integrar Gemini para mejorar la conversacion del asesor.
- Integrar Fragella como catalogo externo de perfumes.
- Mantener un fallback local cuando Gemini o Fragella no tengan cuota o configuracion.
- Recomendar hasta tres perfumes ordenados por compatibilidad.
- Explicar cada recomendacion de forma coherente.
- Ofrecer una interfaz profesional en escritorio y movil.
- Documentar el proyecto con README y Astro Starlight.

## Funcionalidades

- Registro e inicio de sesion con usuario y contrasena.
- Inicio de sesion con Google.
- Renovacion de sesion mediante refresh token.
- Rutas privadas protegidas por JWT.
- Perfil visual con nombre, descripcion, avatar, recomendaciones y estilo olfativo.
- Subida de imagen de perfil a Cloudinary desde el frontend.
- Chat con PerfumIA usando Vue montado dentro de la aplicacion React.
- Estado de proveedores externos: Gemini, Fragella y fallback local.
- Reinicio de conversacion.
- Deteccion de genero objetivo: hombre, mujer o unisex.
- Deteccion de estacion: primavera, verano, otono, invierno o todo el ano.
- Deteccion de ocasion: diario, trabajo, cita, noche, eventos o uso versatil.
- Deteccion de intensidad: suave, media o intensa.
- Deteccion de presupuesto: economico, medio o premium.
- Deteccion de notas: fresa, vainilla, coco, caramelo, madera, citricos, marino, almizcle, oud, pachuli y otras.
- Deteccion de notas rechazadas para evitar recomendaciones incoherentes.
- Deteccion de mood o estilo: limpio, elegante, sensual, oscuro, lujoso, juvenil, calido, casual o minimalista.
- Busqueda en Fragella cuando hay cuota y fallback local cuando no la hay.
- Scoring determinista sin azar.
- Filtrado por genero, estacion y presupuesto.
- Top 3 de perfumes ordenados por compatibilidad.
- Cards con marca, nombre, notas, temporada, precio aproximado, favorito, rating y razon personalizada.
- Acciones de guardar, descartar, marcar favorito y puntuar.
- Historial de recomendaciones guardadas.
- Ranking comunitario de perfumes mejor y peor valorados.
- Chat de comunidad con perfiles publicos.
- Documentacion Starlight con diagramas y casos de prueba.

## Tecnologias utilizadas

### Backend

- Java 17.
- Spring Boot 3.5.
- Spring Web.
- Spring Security.
- Spring Data JPA / Hibernate.
- Spring Validation.
- JWT con `jjwt`.
- MySQL.
- Swagger / OpenAPI con `springdoc-openapi`.
- Gemini API.
- Fragella API.
- Cloudinary mediante subida unsigned desde frontend.
- JUnit y Mockito para tests.

### Frontend

- React 19.
- Vue 3 para el componente de chat.
- Vite.
- Bootstrap 5.
- React Bootstrap.
- Lucide React.
- CSS responsive mobile-first.
- LocalStorage para tokens, tema y sesion.

### Documentacion

- Astro.
- Starlight.
- Diagramas en sintaxis Mermaid dentro de la documentacion.

### Herramientas

- Maven.
- npm.
- MySQL Workbench.
- PowerShell.
- Git como sistema de control de versiones para la entrega final.

## Arquitectura resumida

```txt
APP_PERFUMES/
  Perfumes_backend/   API REST con Spring Boot
  Perfumes_Front/     Frontend React + Vue con Vite
  docs/               Documentacion Astro Starlight
  scripts/            Scripts de arranque y parada
```

El backend sigue una arquitectura por capas:

```txt
controller/      Endpoints REST
service/         Logica de negocio e integraciones externas
persistance/     Entidades y repositorios JPA
security/        JWT, filtros, configuracion y autenticacion
dto/             Objetos de entrada y salida
resources/db/    Schema, datos iniciales y migraciones SQL
```

Servicios principales:

- `PerfumeAdvisorService`: orquesta la conversacion, decide si preguntar mas o recomendar, llama a Gemini, Fragella, scoring y persistencia.
- `AiDecisionService`: extrae preferencias del mensaje del usuario y actualiza el perfil.
- `GeminiService`: comunica con Gemini, usa modo JSON para decisiones estructuradas y modo texto para respuestas conversacionales.
- `PerfumeCatalogService`: consulta Fragella y usa catalogo local si la API externa no esta disponible.
- `PerfumeScoringService`: puntua perfumes por genero, notas, estacion, ocasion, intensidad, presupuesto, mood e historial.
- `PerfumeBudgetClassifier`: clasifica perfumes como economicos, medios o premium para evitar recomendaciones fuera de presupuesto.
- `RecommendationPersistenceService`: guarda mensajes, recomendaciones, favoritos, ratings y estados de aceptacion.

En el frontend, React controla autenticacion, perfil, paneles y comunidad. El chat olfativo esta implementado en Vue y se monta desde React mediante `VueChatMount`, manteniendo una separacion clara entre la zona principal de la aplicacion y la experiencia conversacional.

## Base de datos

La aplicacion usa MySQL y la base `perfumia`.

Tablas principales:

- `roles`: roles de seguridad.
- `users`: usuarios, credenciales, proveedor de autenticacion, descripcion e imagen de perfil.
- `perfume_profiles`: preferencias olfativas del usuario.
- `chat_messages`: historial de conversacion con PerfumIA.
- `perfume_recommendations`: recomendaciones generadas, guardadas, rechazadas, favoritas y puntuadas.
- `community_messages`: mensajes publicos de comunidad.

La base cumple el requisito de consistencia porque incluye:

- claves primarias y foraneas,
- indices unicos para usuario, email y Google subject,
- indices compuestos para consultas frecuentes,
- `CHECK` para limitar ratings entre 1 y 5,
- trigger `trg_chat_messages_before_insert` para validar roles de mensajes,
- procedimiento `sp_accept_recommendation` para aceptar recomendaciones comprobando que pertenecen al usuario,
- migraciones SQL para evolucionar una base existente sin borrar datos.

Estado de `perfume_recommendations.accepted`:

- `NULL`: recomendacion rechazada.
- `0`: recomendacion pendiente.
- `1`: recomendacion aceptada.

## Instalacion

### Requisitos previos

- Java 17.
- Maven o el wrapper incluido.
- Node.js y npm.
- MySQL Server.
- MySQL Workbench o cliente equivalente.

### Variables de entorno

Copia el archivo de ejemplo:

```powershell
Copy-Item .env.local.example .env.local
```

El proyecto queda configurado mediante `.env.local`. Las claves reales se usan solo en local o en el hosting y no se publican en el repositorio por seguridad.

Estructura final de variables:

```txt
DB_URL=jdbc:mysql://localhost:3306/perfumia?createDatabaseIfNotExist=true&useUnicode=true&useLegacyDatetimeCode=false&serverTimezone=UTC
DB_USER=root
DB_PASSWORD=root
JPA_DDL_AUTO=none
SHOW_SQL=false
SERVER_PORT=8080

JWT_ACCESS_SECRET=perfumia-local-access-secret-change-in-production-2026
JWT_REFRESH_SECRET=perfumia-local-refresh-secret-change-in-production-2026
JWT_ACCESS_EXPIRATION=3600000
JWT_REFRESH_EXPIRATION=604800000

GOOGLE_CLIENT_ID=tu-google-client-id.apps.googleusercontent.com
VITE_GOOGLE_CLIENT_ID=tu-google-client-id.apps.googleusercontent.com

GEMINI_API_KEY=tu-gemini-api-key
GEMINI_MODEL=gemini-2.5-flash-lite
GEMINI_BASE_URL=https://generativelanguage.googleapis.com/v1beta/models

PERFUME_API_BASE_URL=https://api.fragella.com/api/v1
PERFUME_API_KEY=tu-fragella-api-key

VITE_API_URL=http://localhost:8080
VITE_CLOUDINARY_CLOUD_NAME=tu-cloudinary-cloud-name
VITE_CLOUDINARY_UPLOAD_PRESET=tu-upload-preset

APP_LOG_LEVEL=INFO
SPRING_WEB_LOG_LEVEL=INFO
SPRING_SECURITY_LOG_LEVEL=INFO
HIBERNATE_SQL_LOG_LEVEL=INFO
```

### Crear base de datos

Ejecuta el schema inicial si la base esta vacia:

```powershell
mysql -u root -p < Perfumes_backend/src/main/resources/db/mysql/schema.sql
mysql -u root -p < Perfumes_backend/src/main/resources/db/mysql/data.sql
```

Si la base ya existia, aplica las migraciones de `Perfumes_backend/src/main/resources/db/mysql/migrations/` en orden.

### Arranque rapido

Desde la raiz del proyecto:

```powershell
.\scripts\start-dev.ps1
```

Para parar los procesos:

```powershell
.\scripts\stop-dev.ps1
```

URLs locales:

- Frontend: `http://localhost:5173/`
- Backend: `http://localhost:8080/public`
- Swagger: `http://localhost:8080/swagger-ui.html`
- Documentacion Starlight: `http://localhost:4321/`

### Arranque manual

Backend:

```powershell
cd Perfumes_backend
.\mvnw.cmd spring-boot:run
```

Frontend:

```powershell
cd Perfumes_Front
npm install
npm run dev
```

Documentacion:

```powershell
cd docs
npm install
npm run dev
```

## Guia de uso

1. Abrir el frontend.
2. Registrarse o iniciar sesion.
3. Opcionalmente, iniciar sesion con Google si esta configurado.
4. Entrar en el asesor olfativo.
5. Explicar que se busca con lenguaje natural.
6. Responder las preguntas de PerfumIA sobre genero, ocasion, presupuesto, notas o intensidad.
7. Revisar las tres recomendaciones.
8. Guardar, descartar, marcar favorito o puntuar perfumes.
9. Consultar el perfil para ver el historial.
10. Usar la comunidad para publicar mensajes o ver perfiles publicos.

Ejemplo de conversacion:

```txt
Usuario: No entiendo de perfumes, pero quiero algo que huela a fresa.
PerfumIA: Me falta saber para quien lo orientamos: hombre, mujer o unisex?
Usuario: Para hombre.
PerfumIA: Perfecto. Para afinarlo, lo quieres para diario, trabajo, citas o noche?
Usuario: Para salir de noche y premium.
PerfumIA: Genera un Top 3 coherente con genero, nota de fresa/frutal, noche y presupuesto premium.
```

## Verificacion y tests

Backend:

```powershell
cd Perfumes_backend
.\mvnw.cmd clean test
```

Frontend:

```powershell
cd Perfumes_Front
npm install
npm run build
```

Documentacion:

```powershell
cd docs
npm install
npm run build
```

Estado de verificacion en esta revision:

- Backend: 151 tests pasando.
- Frontend: build de Vite correcto.
- Documentacion: build de Astro Starlight correcto.
- Vista movil de login: revisada en 360x640 y 390x844 para ocupar el ancho y alto del viewport sin recortar.

## Documentacion

La documentacion completa esta en `docs/` y contiene:

- portada creativa,
- introduccion,
- tecnologias,
- instalacion,
- guia de uso,
- cumplimiento de requisitos DAW,
- arquitectura,
- base de datos,
- diagramas de casos de uso, clases, entidad-relacion, componentes, actividades, secuencia y despliegue,
- casos de prueba,
- despliegue,
- conclusion.

Para abrirla:

```powershell
cd docs
npm run dev
```

Despues entra en `http://localhost:4321/`.

## Zip de entrega

El zip de entrega se genera localmente y no se versiona en GitHub para no duplicar el codigo dentro del repositorio. Cuando vayas a entregar, generarlo o comprobarlo en:

```txt
entrega/PerfumIA_DAW_entrega.zip
```

Si modificas codigo o documentacion despues de este punto, regenera el zip antes de entregar. El zip debe excluir `node_modules`, `target`, `dist`, `logs`, `.env.local` y claves reales.

## Enlaces del proyecto

- Repositorio: [PERFUMIA](https://github.com/ElLete264/PERFUMIA.git).
- Aplicacion desplegada: [http://135.225.93.190/](http://135.225.93.190/).
- Documentacion desplegada: pendiente de URL publica o ejecucion local.
- Figma: [PERFUMIA](https://www.figma.com/design/MhdWeqJJPyEkn8T02pvqhC/PERFUMIA?node-id=0-1&t=dQ4AiaU9o0KblHoK-1).
- Documentacion local: [`docs/`](docs/).

## Conclusion

PerfumIA demuestra una aplicacion web completa: interfaz responsive, autenticacion, autorizacion, persistencia, consultas externas, inteligencia artificial, scoring explicable, comunidad, pruebas y documentacion.

La parte mas diferencial es el asesor olfativo: Gemini ayuda a conversar y entender al usuario, pero la recomendacion final se controla con reglas propias para que el resultado sea coherente, repetible y justificable ante el profesor.

## Contribuciones, agradecimientos y referencias

Proyecto desarrollado como trabajo final de DAW.

Referencias:

- Documentacion oficial de Spring Boot.
- Documentacion oficial de Spring Security.
- Documentacion oficial de React.
- Documentacion oficial de Vue.
- Documentacion oficial de Vite.
- Documentacion oficial de Bootstrap.
- Documentacion oficial de Astro Starlight.
- Documentacion de Gemini API.
- Documentacion de Fragella API.
- Documentacion de Cloudinary.

Agradecimientos: al profesorado del ciclo DAW por la orientacion tecnica y a la documentacion oficial de las tecnologias usadas.

## Licencias

Licencia del proyecto: uso academico para entrega DAW. Puedes sustituir este texto por MIT, Apache 2.0 u otra licencia si el repositorio se publica con licencia formal.

Las tecnologias y librerias usadas mantienen sus propias licencias oficiales.

## Contacto

Alumno/a: CARLOS PÉREZ LARA  
Email: carlospl772@gmail.com  
Ciclo: Desarrollo de Aplicaciones Web (DAW)

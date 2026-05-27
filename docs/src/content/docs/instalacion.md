---
title: Instalacion
description: Como ejecutar PerfumIA en local
---

## Requisitos previos

- Java 17.
- Maven o wrapper Maven incluido.
- Node.js y npm.
- MySQL Server.
- MySQL Workbench o cliente equivalente.

## Variables de entorno

Copia el archivo de ejemplo:

```powershell
Copy-Item .env.local.example .env.local
```

El proyecto queda configurado mediante `.env.local`. Las claves reales se usan solo en local o en el hosting y no se publican en el repositorio por seguridad. Para documentar la instalacion, esta es la estructura final de variables:

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

## Base de datos

La aplicacion usa MySQL y la base `perfumia`.

Para crear la base desde cero:

```powershell
mysql -u root -p < Perfumes_backend/src/main/resources/db/mysql/schema.sql
mysql -u root -p < Perfumes_backend/src/main/resources/db/mysql/data.sql
```

Si ya existe una base antigua, aplica las migraciones de `Perfumes_backend/src/main/resources/db/mysql/migrations/` en orden.

## Backend

```powershell
cd Perfumes_backend
.\mvnw.cmd spring-boot:run
```

URL local:

```txt
http://localhost:8080
```

Swagger:

```txt
http://localhost:8080/swagger-ui.html
```

## Frontend

```powershell
cd Perfumes_Front
npm install
npm run dev
```

URL local:

```txt
http://localhost:5173/
```

## Documentacion Starlight

```powershell
cd docs
npm install
npm run dev
```

URL local:

```txt
http://localhost:4321/
```

## Scripts de ayuda

Desde la raiz del proyecto:

```powershell
.\scripts\start-dev.ps1
.\scripts\stop-dev.ps1
```

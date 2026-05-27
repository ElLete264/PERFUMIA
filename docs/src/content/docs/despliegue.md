---
title: Despliegue
description: Preparacion para publicar PerfumIA
---

## Estado de entrega

El proyecto esta preparado para despliegue. El repositorio y Figma ya estan definidos; antes de entregar faltan los enlaces publicos de la aplicacion y la documentacion:

- Aplicacion desplegada: pendiente de URL publica.
- Repositorio: https://github.com/ElLete264/PERFUMIA.git.
- Documentacion desplegada: pendiente de URL publica o ejecucion local.
- Figma: https://www.figma.com/design/MhdWeqJJPyEkn8T02pvqhC/PERFUMIA?node-id=0-1&t=dQ4AiaU9o0KblHoK-1.

## Backend en produccion

Configurar:

- Java 17.
- MySQL accesible desde el backend.
- `DB_URL`, `DB_USER`, `DB_PASSWORD`.
- `JWT_ACCESS_SECRET` y `JWT_REFRESH_SECRET` largos y distintos.
- `GOOGLE_CLIENT_ID`.
- `GEMINI_API_KEY`.
- `PERFUME_API_KEY`.
- CORS con el dominio real del frontend.
- Logs persistentes si el hosting lo permite.

Build:

```powershell
cd Perfumes_backend
.\mvnw.cmd clean package
```

## Frontend en produccion

Configurar:

- `VITE_API_URL` con la URL publica del backend.
- `VITE_GOOGLE_CLIENT_ID`.
- `VITE_CLOUDINARY_CLOUD_NAME`.
- `VITE_CLOUDINARY_UPLOAD_PRESET`.

Build:

```powershell
cd Perfumes_Front
npm install
npm run build
```

El resultado esta en `Perfumes_Front/dist/`.

## Documentacion Starlight

Build:

```powershell
cd docs
npm install
npm run build
```

El resultado esta en `docs/dist/`.

## Google Login

En Google Cloud hay que autorizar:

- dominio del frontend desplegado,
- dominio local si se quiere probar en desarrollo,
- Client ID usado por frontend y backend.

Si aparece `origin_mismatch`, el dominio no esta autorizado.

## Cloudinary

La subida se hace desde frontend con unsigned upload. Solo se exponen:

- `VITE_CLOUDINARY_CLOUD_NAME`,
- `VITE_CLOUDINARY_UPLOAD_PRESET`.

No se debe poner `API_SECRET` en frontend.

## Entrega zip

El zip debe incluir codigo, documentacion, scripts y SQL. No debe incluir:

- `node_modules`,
- `target`,
- `dist`,
- `logs`,
- `.env.local`,
- claves reales.

## Prueba final

Antes de entregar:

1. Abrir el enlace desde movil.
2. Registrarse o entrar con usuario de prueba.
3. Enviar un mensaje al asesor.
4. Generar recomendaciones.
5. Guardar una recomendacion.
6. Abrir Starlight o documentacion desplegada.
7. Comprobar Swagger si el backend lo expone.

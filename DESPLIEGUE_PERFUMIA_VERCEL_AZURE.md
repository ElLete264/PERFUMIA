# Despliegue de PerfumIA con Vercel, Azure VM y Azure MySQL

Esta guia adapta el despliegue recibido a la estructura real de PerfumIA:

```text
Usuario
  -> Vercel: Perfumes_Front
  -> /api
  -> Azure VM: Nginx + Spring Boot en Docker Compose
  -> Azure Database for MySQL Flexible Server
```

## 1. Archivos preparados

- `Perfumes_backend/Dockerfile`: empaqueta Spring Boot en Java 17.
- `Perfumes_Front/Dockerfile`: genera el build Vite y lo sirve con Nginx.
- `Perfumes_Front/nginx.conf`: sirve la SPA y reenvia `/api/*` al backend.
- `docker-compose.yml`: levanta backend y frontend alternativo en Azure.
- `.env.deploy.example`: plantilla de variables reales para la VM.
- `Perfumes_Front/vercel.example.json`: plantilla para el rewrite de Vercel.

No se sube `.env.deploy` a GitHub.

## 2. Crear MySQL en Azure

Crea un recurso:

```text
Azure Database for MySQL Flexible Server
```

Datos que debes guardar:

```text
Host
Usuario
Contrasena
Nombre de base de datos: perfumia
Puerto: 3306
```

En `Networking`, permite la IP publica de la VM. Si quieres probar desde tu PC, anade tambien tu IP actual.

## 3. Crear VM en Azure

Crea una VM Linux:

```text
Ubuntu 22.04 LTS o Ubuntu 24.04 LTS
Puerto 22 abierto para SSH
Puerto 80 abierto para la web/proxy
```

Guarda:

```text
IP publica
Usuario SSH
Clave SSH o contrasena
```

## 4. Instalar Docker en la VM

Entra por SSH:

```bash
ssh azureuser@IP_PUBLICA
```

Instala Docker:

```bash
sudo apt update
sudo apt upgrade -y
sudo apt install docker.io docker-compose-plugin git -y
sudo systemctl enable docker
sudo systemctl start docker
docker --version
docker compose version
```

## 5. Clonar el repositorio en la VM

```bash
cd /home/azureuser
git clone https://github.com/ElLete264/PERFUMIA.git
cd PERFUMIA
```

Crea el archivo real de entorno:

```bash
cp .env.deploy.example .env.deploy
nano .env.deploy
```

Completa:

```env
DB_URL=jdbc:mysql://TU_HOST.mysql.database.azure.com:3306/perfumia?useSSL=true&requireSSL=true&serverTimezone=Europe/Madrid
DB_USER=TU_USUARIO
DB_PASSWORD=TU_PASSWORD
GOOGLE_CLIENT_ID=...
VITE_GOOGLE_CLIENT_ID=...
GEMINI_API_KEY=...
PERFUME_API_KEY=...
VITE_CLOUDINARY_CLOUD_NAME=...
VITE_CLOUDINARY_UPLOAD_PRESET=...
```

Para el primer arranque, si la base esta vacia, puedes poner temporalmente:

```env
dbinitmode=always
```

Despues del primer arranque correcto, vuelve a:

```env
dbinitmode=never
```

## 6. Levantar PerfumIA en Azure

Desde la raiz del repo dentro de la VM:

```bash
docker compose --env-file .env.deploy up --build -d
docker compose ps
docker compose logs -f service
```

Pruebas rapidas:

```text
http://IP_PUBLICA/public
http://IP_PUBLICA/api/public
http://IP_PUBLICA/api/actuator/health
```

La ruta importante para Vercel sera:

```text
http://IP_PUBLICA/api
```

Nginx elimina `/api` antes de enviar la peticion a Spring Boot. Por ejemplo:

```text
Vercel: /api/auth/login
Azure Nginx: /api/auth/login
Spring Boot: /auth/login
```

## 7. Preparar Vercel

Cuando tengas la IP publica de Azure, copia la plantilla:

```powershell
Copy-Item Perfumes_Front\vercel.example.json Perfumes_Front\vercel.json
```

Edita `Perfumes_Front/vercel.json` y cambia:

```text
IP_PUBLICA_AZURE
```

por tu IP real.

En Vercel:

```text
Add New Project
Import Git Repository
Root Directory: Perfumes_Front
Framework Preset: Vite
Install Command: npm ci
Build Command: npm run build
Output Directory: dist
```

Variables de entorno en Vercel:

```env
VITE_API_URL=/api
VITE_GOOGLE_CLIENT_ID=tu-google-client-id.apps.googleusercontent.com
VITE_CLOUDINARY_CLOUD_NAME=tu-cloudinary-cloud-name
VITE_CLOUDINARY_UPLOAD_PRESET=tu-upload-preset
```

## 8. Google OAuth

En Google Cloud Console, anade como origen autorizado:

```text
http://localhost:5173
https://TU_PROYECTO.vercel.app
```

Si usas dominio propio, anade tambien:

```text
https://tudominio.com
```

## 9. Actualizar el backend despues de cambios

```bash
ssh azureuser@IP_PUBLICA
cd /home/azureuser/PERFUMIA
git pull origin main
docker compose --env-file .env.deploy up --build -d
docker compose ps
```

## 10. Actualizar el frontend despues de cambios

Sube cambios a GitHub:

```powershell
git add .
git commit -m "Actualiza frontend"
git push
```

Vercel redespliega automaticamente.

## 11. Ultimo paso para la entrega

Cuando tengas:

- URL de Vercel.
- URL publica de Azure.
- URL de Starlight desplegada si decides desplegar docs.

Actualiza `README.md` y `docs/src/content/docs/despliegue.md` con esos enlaces.

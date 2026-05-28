# Despliegue de PerfumIA desde Windows con PuTTY, PuTTYgen y WinSCP

Esta guia es la receta paso a paso para desplegar PerfumIA siendo nuevo en Azure.

## 0. Importante

No pegues contrasenas en chats, repositorios ni capturas. Si has compartido una contrasena real, cambiala antes de desplegar.

## 1. Lo que necesitas

- PuTTY.
- PuTTYgen.
- WinSCP.
- Azure CLI instalado.
- Cuenta de Azure con suscripcion activa.
- Repo subido a GitHub.
- Cuenta de Vercel conectada a GitHub.

Comprueba Azure CLI:

```powershell
az --version
```

Inicia sesion de forma segura:

```powershell
az login
```

## 2. Crear clave con PuTTYgen

1. Abre PuTTYgen.
2. Pulsa `Generate`.
3. Mueve el raton por la ventana hasta que termine.
4. Pulsa `Save private key` y guarda:

```text
C:\Users\cperez\.ssh\perfumia-azure.ppk
```

5. Pulsa `Save public key` y guarda:

```text
C:\Users\cperez\.ssh\perfumia-azure.pub
```

6. Copia tambien el texto superior de PuTTYgen por si lo necesitas en Azure Portal.

## 3. Crear Azure automaticamente desde PowerShell

Desde la raiz del proyecto:

```powershell
.\scripts\azure-provision-perfumia.ps1
```

El script pedira:

- Confirmacion escribiendo `CREAR`.
- Password para el usuario MySQL.

El script crea:

- Resource group.
- VM Ubuntu.
- Puerto 22 para SSH.
- Puerto 80 para la web.
- Azure MySQL Flexible Server.
- Base de datos `perfumia`.
- Regla de firewall para que MySQL acepte la IP de la VM.

Al terminar genera:

```text
azure-deploy-output.txt
```

Ese archivo contiene la IP de la VM, el host MySQL y la URL JDBC que debes pegar en `.env.deploy`.

## 4. Entrar en la VM con PuTTY

1. Abre PuTTY.
2. En `Host Name` pon:

```text
azureuser@IP_PUBLICA_DE_LA_VM
```

3. Ve a `Connection > SSH > Auth > Credentials`.
4. En `Private key file for authentication`, selecciona:

```text
C:\Users\cperez\.ssh\perfumia-azure.ppk
```

5. Vuelve a `Session`.
6. Guarda la sesion como `perfumia-azure`.
7. Pulsa `Open`.

## 5. Instalar Docker en la VM

En PuTTY:

```bash
sudo apt update
sudo apt upgrade -y
sudo apt install docker.io docker-compose-plugin git -y
sudo systemctl enable docker
sudo systemctl start docker
docker --version
docker compose version
```

## 6. Clonar el proyecto en la VM

```bash
cd /home/azureuser
git clone https://github.com/ElLete264/PERFUMIA.git
cd PERFUMIA
```

## 7. Crear `.env.deploy` en la VM

```bash
cp .env.deploy.example .env.deploy
nano .env.deploy
```

Rellena como minimo:

```env
DB_URL=jdbc:mysql://TU_HOST.mysql.database.azure.com:3306/perfumia?useSSL=true&requireSSL=true&serverTimezone=Europe/Madrid
DB_USER=perfumiaadmin
DB_PASSWORD=TU_PASSWORD_MYSQL
dbinitmode=always

GOOGLE_CLIENT_ID=tu-google-client-id
VITE_GOOGLE_CLIENT_ID=tu-google-client-id

GEMINI_API_KEY=tu-gemini-key
PERFUME_API_KEY=tu-fragella-key

VITE_API_URL=/api
VITE_CLOUDINARY_CLOUD_NAME=tu-cloudinary-cloud-name
VITE_CLOUDINARY_UPLOAD_PRESET=tu-upload-preset
```

Guarda en nano:

```text
CTRL + O
ENTER
CTRL + X
```

## 8. Levantar PerfumIA en Azure

```bash
docker compose --env-file .env.deploy up --build -d
docker compose ps
```

Ver logs del backend:

```bash
docker compose logs -f service
```

Pruebas en navegador:

```text
http://IP_PUBLICA/api/public
http://IP_PUBLICA/api/actuator/health
```

Si responde, cambia `dbinitmode=always` a:

```env
dbinitmode=never
```

Y reinicia:

```bash
docker compose --env-file .env.deploy up -d
```

## 9. Usar WinSCP para editar archivos

1. Abre WinSCP.
2. Protocolo: `SFTP`.
3. Host: `IP_PUBLICA`.
4. Usuario: `azureuser`.
5. Advanced > SSH > Authentication.
6. Selecciona la private key `.ppk`.
7. Entra en:

```text
/home/azureuser/PERFUMIA
```

Desde WinSCP puedes editar `.env.deploy`.

Despues de editarlo, vuelve a PuTTY:

```bash
cd /home/azureuser/PERFUMIA
docker compose --env-file .env.deploy up -d
```

## 10. Preparar Vercel

Cuando Azure responda, en tu PC:

```powershell
Copy-Item Perfumes_Front\vercel.example.json Perfumes_Front\vercel.json
```

Edita `Perfumes_Front\vercel.json`:

```json
"destination": "http://IP_PUBLICA_AZURE/api/:path*"
```

Cambia `IP_PUBLICA_AZURE` por la IP real.

Sube el archivo:

```powershell
git add Perfumes_Front/vercel.json
git commit -m "Configurar Vercel para Azure"
git push
```

En Vercel:

```text
Add New Project
Import Git Repository
PERFUMIA
Root Directory: Perfumes_Front
Framework Preset: Vite
Install Command: npm ci
Build Command: npm run build
Output Directory: dist
```

Variables en Vercel:

```env
VITE_API_URL=/api
VITE_GOOGLE_CLIENT_ID=tu-google-client-id
VITE_CLOUDINARY_CLOUD_NAME=tu-cloudinary-cloud-name
VITE_CLOUDINARY_UPLOAD_PRESET=tu-upload-preset
```

## 11. Google OAuth

En Google Cloud Console, anade:

```text
http://localhost:5173
https://TU_PROYECTO.vercel.app
```

## 12. Actualizar Azure cuando cambie el backend

```bash
ssh azureuser@IP_PUBLICA
cd /home/azureuser/PERFUMIA
git pull origin main
docker compose --env-file .env.deploy up --build -d
docker compose ps
```

## 13. Comandos de emergencia

Ver contenedores:

```bash
docker compose ps
```

Ver logs:

```bash
docker compose logs -f
```

Reiniciar:

```bash
docker compose restart
```

Parar:

```bash
docker compose down
```

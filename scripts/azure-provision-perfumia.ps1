param(
    [string]$ResourceGroup = "perfumia-rg",
    [string]$Location = "westeurope",
    [string]$VmName = "perfumia-vm",
    [string]$VmUser = "azureuser",
    [string]$VmSize = "Standard_B1s",
    [string]$SshPublicKeyPath = "$env:USERPROFILE\.ssh\perfumia-azure.pub",
    [string]$MySqlServerName = "",
    [string]$MySqlAdminUser = "perfumiaadmin",
    [string]$DatabaseName = "perfumia"
)

$ErrorActionPreference = "Stop"

function Require-Command {
    param([string]$Name)
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "No encuentro el comando '$Name'. Instala Azure CLI o abre una terminal donde este disponible."
    }
}

function ConvertTo-PlainText {
    param([Security.SecureString]$Secure)
    $ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($Secure)
    try {
        return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr)
    } finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr)
    }
}

Require-Command "az"

if (-not (Test-Path $SshPublicKeyPath)) {
    throw "No existe la clave publica SSH: $SshPublicKeyPath. Crea una con PuTTYgen y guardala tambien en formato .pub."
}

if ([string]::IsNullOrWhiteSpace($MySqlServerName)) {
    $suffix = Get-Random -Minimum 10000 -Maximum 99999
    $MySqlServerName = "perfumia-db-$suffix"
}

Write-Host "Comprobando sesion de Azure..." -ForegroundColor Cyan
$null = az account show --only-show-errors
if ($LASTEXITCODE -ne 0) {
    throw "No hay sesion de Azure. Ejecuta primero: az login"
}

Write-Host ""
Write-Host "Este script va a crear recursos en Azure y pueden generar coste." -ForegroundColor Yellow
Write-Host "Resource group: $ResourceGroup"
Write-Host "Location:       $Location"
Write-Host "VM:             $VmName ($VmSize)"
Write-Host "MySQL server:   $MySqlServerName"
Write-Host "Database:       $DatabaseName"
Write-Host ""
$confirm = Read-Host "Escribe CREAR para continuar"
if ($confirm -ne "CREAR") {
    Write-Host "Cancelado. No se ha creado nada." -ForegroundColor Yellow
    exit 1
}

$mysqlPassword = Read-Host "Password del usuario MySQL '$MySqlAdminUser'" -AsSecureString
$mysqlPasswordPlain = ConvertTo-PlainText $mysqlPassword

try {
    Write-Host "Creando grupo de recursos..." -ForegroundColor Cyan
    az group create `
        --name $ResourceGroup `
        --location $Location `
        --only-show-errors `
        --output none

    Write-Host "Creando maquina virtual Ubuntu..." -ForegroundColor Cyan
    az vm create `
        --resource-group $ResourceGroup `
        --name $VmName `
        --image "Ubuntu2204" `
        --size $VmSize `
        --admin-username $VmUser `
        --ssh-key-values $SshPublicKeyPath `
        --public-ip-sku Standard `
        --nsg-rule SSH `
        --only-show-errors `
        --output none

    Write-Host "Abriendo puerto 80..." -ForegroundColor Cyan
    az vm open-port `
        --resource-group $ResourceGroup `
        --name $VmName `
        --port 80 `
        --priority 1001 `
        --only-show-errors `
        --output none

    $vmIp = az vm show `
        --resource-group $ResourceGroup `
        --name $VmName `
        --show-details `
        --query publicIps `
        --output tsv

    Write-Host "Creando Azure MySQL Flexible Server..." -ForegroundColor Cyan
    az mysql flexible-server create `
        --resource-group $ResourceGroup `
        --name $MySqlServerName `
        --location $Location `
        --admin-user $MySqlAdminUser `
        --admin-password $mysqlPasswordPlain `
        --sku-name Standard_B1ms `
        --tier Burstable `
        --storage-size 20 `
        --version 8.0.21 `
        --public-access $vmIp `
        --only-show-errors `
        --yes `
        --output none

    Write-Host "Creando base de datos..." -ForegroundColor Cyan
    az mysql flexible-server db create `
        --resource-group $ResourceGroup `
        --server-name $MySqlServerName `
        --database-name $DatabaseName `
        --only-show-errors `
        --output none

    Write-Host "Asegurando firewall MySQL para la IP de la VM..." -ForegroundColor Cyan
    az mysql flexible-server firewall-rule create `
        --resource-group $ResourceGroup `
        --name "AllowPerfumiaVm" `
        --server-name $MySqlServerName `
        --start-ip-address $vmIp `
        --end-ip-address $vmIp `
        --only-show-errors `
        --output none

    $mysqlHost = az mysql flexible-server show `
        --resource-group $ResourceGroup `
        --name $MySqlServerName `
        --query fullyQualifiedDomainName `
        --output tsv

    $outputPath = Join-Path (Resolve-Path "$PSScriptRoot\..").Path "azure-deploy-output.txt"
    @"
ResourceGroup=$ResourceGroup
Location=$Location
VmName=$VmName
VmUser=$VmUser
VmPublicIp=$vmIp
MySqlServerName=$MySqlServerName
MySqlHost=$mysqlHost
MySqlAdminUser=$MySqlAdminUser
DatabaseName=$DatabaseName

DB_URL=jdbc:mysql://$mysqlHost`:3306/$DatabaseName`?useSSL=true&requireSSL=true&serverTimezone=Europe/Madrid
"@ | Set-Content -Path $outputPath -Encoding UTF8

    Write-Host ""
    Write-Host "Infraestructura creada." -ForegroundColor Green
    Write-Host "IP publica VM: $vmIp"
    Write-Host "Host MySQL:     $mysqlHost"
    Write-Host "Resumen guardado en: $outputPath"
    Write-Host ""
    Write-Host "Siguiente paso: entra por PuTTY y ejecuta los comandos de DESPLIEGUE_WINDOWS_PUTTY_WINSCP.md"
} finally {
    $mysqlPasswordPlain = $null
}

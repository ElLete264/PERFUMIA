param(
    [string]$Root = (Resolve-Path "$PSScriptRoot\..").Path
)

$envFile = Join-Path $Root ".env.local"
if (Test-Path $envFile) {
    Get-Content $envFile | ForEach-Object {
        $line = $_.Trim()
        if ($line -and -not $line.StartsWith("#") -and $line.Contains("=")) {
            $key, $value = $line.Split("=", 2)
            [Environment]::SetEnvironmentVariable($key.Trim(), $value.Trim(), "Process")
        }
    }
}

if (-not $env:VITE_GOOGLE_CLIENT_ID -and $env:GOOGLE_CLIENT_ID) {
    [Environment]::SetEnvironmentVariable("VITE_GOOGLE_CLIENT_ID", $env:GOOGLE_CLIENT_ID, "Process")
}

foreach ($port in 8080, 5173, 5174) {
    $busy = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
    if ($busy) {
        Write-Host "El puerto $port esta ocupado. Ejecuta scripts\stop-dev.ps1 antes de arrancar." -ForegroundColor Yellow
        exit 1
    }
}

$backend = Join-Path $Root "Perfumes_backend"
$frontend = Join-Path $Root "Perfumes_Front"

Start-Process -FilePath "mvn.cmd" -ArgumentList "spring-boot:run" -WorkingDirectory $backend -WindowStyle Hidden -RedirectStandardOutput (Join-Path $backend "logs\spring-run.log") -RedirectStandardError (Join-Path $backend "logs\spring-run.err.log")
Start-Process -FilePath "npm.cmd" -ArgumentList "run","dev","--","--host","0.0.0.0" -WorkingDirectory $frontend -WindowStyle Hidden -RedirectStandardOutput (Join-Path $frontend "vite-run.log") -RedirectStandardError (Join-Path $frontend "vite-run.err.log")

Write-Host "PerfumIA arrancando..." -ForegroundColor Green
Write-Host "Backend:  http://localhost:8080/public"
Write-Host "Frontend: http://localhost:5173/"

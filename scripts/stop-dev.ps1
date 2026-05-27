foreach ($port in 8080, 5173, 5174) {
    Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue | ForEach-Object {
        Write-Host "Parando proceso $($_.OwningProcess) en puerto $port"
        Stop-Process -Id $_.OwningProcess -Force -ErrorAction SilentlyContinue
    }
}

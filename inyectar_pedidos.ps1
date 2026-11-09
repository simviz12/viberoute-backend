# Script para inyectar pedidos masivos en VibeRoute Backend
$baseUrl = "http://localhost:8080/api/v1"

Write-Host "1. Autenticando como Super Admin..." -ForegroundColor Cyan
$loginPayload = @{
    email    = "superadmin@viberoute.com"
    password = "VibeRoute2026!"
} | ConvertTo-Json

$auth = Invoke-RestMethod -Uri "$baseUrl/auth/login" -Method Post -Body $loginPayload -ContentType "application/json"
$headers = @{ Authorization = "Bearer $($auth.token)" }

Write-Host "2. Leyendo 'carga_masiva_pedidos.json'..." -ForegroundColor Cyan
$jsonText = Get-Content -Raw -Encoding UTF8 "$PSScriptRoot\carga_masiva_pedidos.json"
$bodyBytes = [System.Text.Encoding]::UTF8.GetBytes($jsonText)

Write-Host "3. Enviando carga masiva al backend ($baseUrl/orders/bulk)..." -ForegroundColor Cyan
$response = Invoke-RestMethod -Uri "$baseUrl/orders/bulk" -Method Post -Body $bodyBytes -ContentType "application/json; charset=utf-8" -Headers $headers

Write-Host "==========================================" -ForegroundColor Green
Write-Host "   RESULTADO DE LA CARGA MASIVA" -ForegroundColor Green
Write-Host "==========================================" -ForegroundColor Green
$statusColor = if ($response.errorCount -eq 0) { "Green" } else { "Red" }
Write-Host "Pedidos creados con éxito : $($response.createdCount)" -ForegroundColor Green
Write-Host "Errores en la carga       : $($response.errorCount)" -ForegroundColor $statusColor

if ($response.errorCount -gt 0) {
    Write-Host "`nDetalle de errores:" -ForegroundColor Red
    $response.errors | Format-Table
}

Write-Host "`nÚltimos pedidos registrados:" -ForegroundColor Cyan
$response.created | Select-Object -First 5 | Format-Table id, clientReference, clientName, address, city, price


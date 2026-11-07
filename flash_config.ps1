# =========================================================
#  flash_config.ps1 - Picur
#  Detecta la red Wi-Fi e IP actuales del PC y flashea solo
#  la configuracion del Pico W. Reutiliza deploy_pico.ps1.
#
#  Uso:
#    .\flash_config.ps1
#    .\flash_config.ps1 -Port com7
#    .\flash_config.ps1 -Ssid "MiRed" -Password "clave" -Ip 192.168.1.5
#    .\flash_config.ps1 -Run    ademas ejecuta main.py
# =========================================================

param(
    [string]$Port = "com8",
    [switch]$Run,
    [string]$Ssid,
    [string]$Password,
    [string]$Ip
)

$ErrorActionPreference = "Stop"

if (-not $PSScriptRoot) {
    $PSScriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
}

$parametros = @{
    Port         = $Port
    SkipFirmware = $true
    NoReset      = $true
}

if ($Ssid) { $parametros.Ssid = $Ssid }
if ($Password) { $parametros.Password = $Password }
if ($Ip) { $parametros.Ip = $Ip }

& (Join-Path $PSScriptRoot "deploy_pico.ps1") @parametros

if ($Run) {
    Write-Host ""
    Write-Host "Ejecutando main.py en el Pico..."
    python -m mpremote connect $Port run (Join-Path $PSScriptRoot "firmware\main.py")
    if ($LASTEXITCODE -ne 0) {
        throw "mpremote run fallo en $Port"
    }
}

Write-Host ""
Write-Host ("Listo. Reinicia el Pico con: python -m mpremote connect {0} reset" -f $Port)

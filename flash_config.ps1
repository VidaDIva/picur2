# =========================================================
#  flash_config.ps1 - Picur
#  Detecta la red Wi-Fi e IP actuales del PC, genera la
#  config del Pico W y la flashea por USB (mpremote).
#  No toca el config.py del repo: la config se arma en un
#  archivo temporal y se copia solo al dispositivo.
#
#  Uso:
#    .\flash_config.ps1             detecta y flashea
#    .\flash_config.ps1 -Port com7  otro puerto USB
#    .\flash_config.ps1 -Run        ademas ejecuta main.py
# =========================================================

param(
    [string]$Port = "com8",
    [switch]$Run
)

$ErrorActionPreference = "Stop"

if (-not $PSScriptRoot) {
    $PSScriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
}

$CarpetaFirmware = Join-Path $PSScriptRoot "firmware"
$TempConfig = Join-Path $env:TEMP "picur_config.py"


function Obtener-IpActiva {
    $config = Get-NetIPConfiguration -ErrorAction SilentlyContinue |
        Where-Object { $_.NetAdapter.Status -eq "Up" -and $_.IPv4DefaultGateway } |
        Select-Object -First 1
    if ($config) {
        return $config.IPv4Address.IPAddress
    }
    return $null
}


function Obtener-Ssid {
    foreach ($linea in (netsh wlan show interfaces)) {
        if ($linea -match '^\s*SSID\s*:\s*(.+)\s*$') {
            $valor = $Matches[1].Trim()
            if ($valor) {
                return $valor
            }
        }
    }
    return $null
}


function Obtener-Clave([string]$Ssid) {
    foreach ($linea in (netsh wlan show profile name="$Ssid" key=clear)) {
        if ($linea -match 'Contenido de la clave\s*:\s*(.+)') {
            return $Matches[1].Trim()
        }
        if ($linea -match 'Key Content\s*:\s*(.+)') {
            return $Matches[1].Trim()
        }
    }
    return $null
}


function Leer-ClaveManual {
    $Segura = Read-Host "Escribe la contrasena de la red '$Ssid'" -AsSecureString
    $ptr = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($Segura)
    try {
        return [System.Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr)
    } finally {
        [System.Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr)
    }
}


function Enmascarar([string]$Valor) {
    if ($Valor.Length -le 2) {
        return "*" * $Valor.Length
    }
    return $Valor.Substring(0, 2) + ("*" * ($Valor.Length - 2))
}


function Generar-Config([string]$Ssid, [string]$Clave, [string]$Ip) {
    $plantilla = @'
# ============ Configuracion del Pico W ============
# Generado por flash_config.ps1 segun la red actual del PC.

WIFI_SSID = "@@SSID@@"
WIFI_PASSWORD = "@@CLAVE@@"
PC_IP = "@@IP@@"

PUERTO = 8080

DISPOSITIVO = "PICO-001"
PERIODO_ENVIO_S = 2

NUM_MUESTRAS = 128
'@
    $plantilla = $plantilla.Replace('@@SSID@@', $Ssid)
    $plantilla = $plantilla.Replace('@@CLAVE@@', $Clave)
    $plantilla = $plantilla.Replace('@@IP@@', $Ip)
    return $plantilla
}


function Flashear([string]$ArchivoRemoto, [string]$OrigenLocal) {
    Write-Host ("  -> " + $ArchivoRemoto)
    python -m mpremote connect $Port cp $OrigenLocal $ArchivoRemoto
    if ($LASTEXITCODE -ne 0) {
        throw "mpremote cp fallo para $ArchivoRemoto"
    }
}


# ---------- 1. Detectar valores ----------
Write-Host ""
Write-Host "== Picur: config automatica del Pico =="

$Ip = Obtener-IpActiva
if (-not $Ip) {
    $Ip = Read-Host "No se detecto IP activa. Escribe la IP local del PC"
}

$Ssid = Obtener-Ssid
if (-not $Ssid) {
    $Ssid = Read-Host "No hay Wi-Fi conectada. Escribe el SSID de la red"
}

$Clave = Obtener-Clave -Ssid $Ssid
if (-not $Clave) {
    $Clave = Leer-ClaveManual
}

Write-Host ""
Write-Host ("Red detectada : {0}" -f $Ssid)
Write-Host ("IP del PC     : {0}" -f $Ip)
Write-Host ("Contrasena    : {0}" -f (Enmascarar $Clave))

# ---------- 2. Generar config ----------
$Contenido = Generar-Config $Ssid $Clave $Ip
Set-Content -LiteralPath $TempConfig -Value $Contenido -Encoding ASCII

# ---------- 3. Flashear al Pico ----------
Write-Host ""
Write-Host "Flasheando config al Pico (puerto $Port)..."
try {
    Flashear ":/config.py" $TempConfig

    $Verif = python -m mpremote connect $Port exec "import config; print('CONFIG DEVICE:', config.WIFI_SSID, config.PC_IP)"
    $Salida = ($Verif -join ' ')
    Write-Host ""
    Write-Host ("Verificacion: {0}" -f $Salida)
    if ($Salida -notmatch 'CONFIG DEVICE:') {
        throw "La config flasheada no es valida (revisa delimitadores en el dispositivo)."
    }
    Write-Host "CONFIG OK - el Pico ahora usara la red actual."
} finally {
    Remove-Item -LiteralPath $TempConfig -ErrorAction SilentlyContinue
}

# ---------- 4. Opcional: correr main.py ----------
if ($Run) {
    Write-Host ""
    Write-Host "Ejecutando main.py en el Pico..."
    python -m mpremote connect $Port run (Join-Path $CarpetaFirmware "main.py")
}

Write-Host ""
Write-Host "Listo. Reinicia el Pico (o mpremote connect $Port reset) para que arranque main.py."
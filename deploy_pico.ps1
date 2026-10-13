# =========================================================
#  deploy_pico.ps1 - Picur
#  Un solo comando para dejar el Pico W corriendo el firmware
#  actual con la config de la red donde estes ahora (uni/casa).
#
#   1. Detecta SSID, clave e IP actuales del PC y flashea
#      config.py (igual que flash_config.ps1, sin tocar el
#      config.py del repo, que es solo plantilla).
#   2. Copia todos los modulos de firmware/ al Pico.
#   3. Reinicia el Pico para que main.py arranque solo.
#
#  Uso:
#    .\deploy_pico.ps1              detecta red, despliega y reinicia
#    .\deploy_pico.ps1 -Port com7   otro puerto USB
#    .\deploy_pico.ps1 -SkipConfig  solo copia el firmware y reinicia
#                                   (si la config ya esta correcta)
# =========================================================

param(
    [string]$Port = "com8",
    [switch]$SkipConfig
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
# Generado por deploy_pico.ps1 segun la red actual del PC.

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


function Reiniciar {
    Write-Host ("  -> reset (reinicia y arranca main.py en " + $Port + ")")
    python -m mpremote connect $Port reset
    if ($LASTEXITCODE -ne 0) {
        throw "mpremote reset fallo en $Port"
    }
}


# ---------- 1. Detectar red y flashear config (opcional) ----------
Write-Host ""
Write-Host "== Picur: despliegue del Pico W =="

if (-not $SkipConfig) {
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

    $Contenido = Generar-Config $Ssid $Clave $Ip
    Set-Content -LiteralPath $TempConfig -Value $Contenido -Encoding ASCII

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
    } finally {
        Remove-Item -LiteralPath $TempConfig -ErrorAction SilentlyContinue
    }
} else {
    Write-Host ""
    Write-Host "Config no se toca (-SkipConfig)."
}

# ---------- 2. Copiar modulos del firmware (sin config.py) ----------
$ArchivosFirmware = Get-ChildItem -LiteralPath $CarpetaFirmware -Filter *.py |
    Where-Object { $_.Name -ne "config.py" }

Write-Host ""
Write-Host ("Desplegando firmware (pruebas/ excluida, " + $ArchivosFirmware.Count + " modulos)...")
foreach ($archivo in $ArchivosFirmware) {
    Flashear (":/" + $archivo.Name) $archivo.FullName
}

# ---------- 3. Reiniciar para que main.py arranque ----------
Write-Host ""
Write-Host "Reiniciando el Pico..."
Reiniciar

Write-Host ""
Write-Host "Listo. El Pico solo arranca main.py:"
Write-Host "  Wi-Fi -> autotest -> calibracion ~10 s -> monitoreo"
Write-Host ("Para ver la salida USB en vivo: python -m mpremote connect {0} repl" -f $Port)
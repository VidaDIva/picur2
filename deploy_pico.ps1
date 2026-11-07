# =========================================================
#  deploy_pico.ps1 - Picur
#  Deja el Pico W corriendo el firmware actual con la red
#  donde estes ahora (casa, universidad, hotspot).
#
#   1. Detecta SSID, clave e IP actuales del PC.
#   2. Mantiene una lista de redes conocidas en
#      firmware/redes.local.json (ignorada por git).
#   3. Genera y flashea config.py en UTF-8, con backup y
#      rollback si la config no es valida.
#   4. Copia todos los modulos de firmware/ al Pico.
#   5. Reinicia el Pico para que main.py arranque solo.
#
#  Uso:
#    .\deploy_pico.ps1
#    .\deploy_pico.ps1 -Port com7
#    .\deploy_pico.ps1 -SkipConfig   solo firmware
#    .\deploy_pico.ps1 -Ssid "MiRed" -Password "clave" -Ip 192.168.1.5
#    .\deploy_pico.ps1 -SkipFirmware -NoReset
# =========================================================

param(
    [string]$Port = "com8",
    [switch]$SkipConfig,
    [switch]$SkipFirmware,
    [switch]$NoReset,
    [string]$Ssid,
    [string]$Password,
    [string]$Ip
)

$ErrorActionPreference = "Stop"

if (-not $PSScriptRoot) {
    $PSScriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
}

. (Join-Path $PSScriptRoot "picur_red.ps1")

$CarpetaFirmware = Join-Path $PSScriptRoot "firmware"
$TempConfig = Join-Path $env:TEMP "picur_config.py"
$TempBackup = Join-Path $env:TEMP "picur_config_backup.py"


function Reiniciar {
    Write-Host ("  -> reset (reinicia y arranca main.py en " + $Port + ")")
    python -m mpremote connect $Port reset
    if ($LASTEXITCODE -ne 0) {
        throw "mpremote reset fallo en $Port"
    }
}


Write-Host ""
Write-Host "== Picur: despliegue del Pico W =="

if (-not $SkipConfig) {
    if (-not $Ip) {
        $Ip = Obtener-IpActiva
    }
    if (-not $Ip) {
        $Ip = Read-Host "No se detecto IP activa. Escribe la IP local del PC"
    }

    if (-not $Ssid) {
        $Ssid = Obtener-Ssid
    }
    if (-not $Ssid) {
        $Ssid = Read-Host "No hay Wi-Fi conectada. Escribe el SSID de la red"
    }

    if (-not $Password) {
        $Password = Obtener-Clave -Ssid $Ssid
    }
    if (-not $Password) {
        $Password = Leer-ClaveManual -Ssid $Ssid
    }

    Write-Host ""
    Write-Host ("Red detectada : {0}" -f $Ssid)
    Write-Host ("IP del PC     : {0}" -f $Ip)
    Write-Host ("Contrasena    : {0}" -f (Enmascarar $Password))

    $Redes = @(Leer-RedesLocales)
    $Redes = @(Actualizar-Redes -Redes $Redes -Ssid $Ssid -Password $Password -Ip $Ip)

    $Contenido = Generar-Config -Redes $Redes
    Escribir-Utf8SinBom $TempConfig $Contenido

    if (-not (Validar-ConfigLocal $TempConfig)) {
        Remove-Item -LiteralPath $TempConfig -ErrorAction SilentlyContinue
        throw "La config generada no es Python valido. No se flasheo nada."
    }

    Write-Host ""
    Write-Host ("Flasheando config al Pico (puerto $Port, {0} redes)..." -f $Redes.Count)

    $BackupOk = $false
    try {
        python -m mpremote connect $Port cp :/config.py $TempBackup | Out-Null
        $BackupOk = ($LASTEXITCODE -eq 0)
    } catch {
        $BackupOk = $false
    }

    try {
        Flashear -Port $Port -ArchivoRemoto ":/config.py" -OrigenLocal $TempConfig

        $Verif = python -m mpremote connect $Port exec "import config; print('CONFIG DEVICE:', config.WIFI_SSID, config.PC_IP, len(config.REDES))"
        $Salida = ($Verif -join ' ')
        Write-Host ""
        Write-Host ("Verificacion: {0}" -f $Salida)

        if ($LASTEXITCODE -ne 0 -or $Salida -notmatch 'CONFIG DEVICE:') {
            throw "La config flasheada no es valida en el dispositivo."
        }

        Guardar-RedesLocales -Redes $Redes
        Write-Host "CONFIG OK - red guardada en firmware/redes.local.json"
    } catch {
        if ($BackupOk -and (Test-Path -LiteralPath $TempBackup)) {
            Write-Warning "Restaurando config anterior..."
            python -m mpremote connect $Port cp $TempBackup ":/config.py" | Out-Null
        }
        throw
    } finally {
        Remove-Item -LiteralPath $TempConfig -ErrorAction SilentlyContinue
        Remove-Item -LiteralPath $TempBackup -ErrorAction SilentlyContinue
    }
} else {
    Write-Host ""
    Write-Host "Config no se toca (-SkipConfig)."
}

if (-not $SkipFirmware) {
    $ArchivosFirmware = Get-ChildItem -LiteralPath $CarpetaFirmware -Filter *.py |
        Where-Object { $_.Name -ne "config.py" }

    Write-Host ""
    Write-Host ("Desplegando firmware (pruebas/ excluida, " + $ArchivosFirmware.Count + " modulos)...")
    foreach ($archivo in $ArchivosFirmware) {
        Flashear -Port $Port -ArchivoRemoto (":/" + $archivo.Name) -OrigenLocal $archivo.FullName
    }
} else {
    Write-Host ""
    Write-Host "Firmware no se copia (-SkipFirmware)."
}

if (-not $NoReset) {
    Write-Host ""
    Write-Host "Reiniciando el Pico..."
    Reiniciar

    Write-Host ""
    Write-Host "Listo. El Pico solo arranca main.py:"
    Write-Host "  Wi-Fi -> NTP -> autotest -> calibracion ~10 s -> monitoreo"
    Write-Host ("Para ver la salida USB en vivo: python -m mpremote connect {0} repl" -f $Port)
    Write-Host ("Para ver el log guardado: python -m mpremote connect {0} cp :/picur.log .\picur.log" -f $Port)
} else {
    Write-Host ""
    Write-Host "Sin reset (-NoReset)."
}

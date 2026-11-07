# =========================================================
#  picur_red.ps1 - funciones compartidas por los scripts de
#  despliegue del Pico W (redes conocidas y config.py).
# =========================================================

$RedesLocales = Join-Path $PSScriptRoot "firmware\redes.local.json"


function Obtener-IpActiva {
    $config = Get-NetIPConfiguration -ErrorAction SilentlyContinue |
        Where-Object {
            $_.NetAdapter.Status -eq "Up" -and
            $_.IPv4DefaultGateway -and
            $_.NetAdapter.InterfaceDescription -notmatch 'VPN|Virtual|Hyper-V|Loopback|TAP'
        } |
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


function Leer-ClaveManual([string]$Ssid) {
    $Segura = Read-Host "Escribe la contrasena de la red '$Ssid'" -AsSecureString
    $ptr = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($Segura)
    try {
        return [System.Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr)
    } finally {
        [System.Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr)
    }
}


function Enmascarar([string]$Valor) {
    if ($null -eq $Valor) {
        return ""
    }
    if ($Valor.Length -le 2) {
        return "*" * $Valor.Length
    }
    return $Valor.Substring(0, 2) + ("*" * ($Valor.Length - 2))
}


function ConvertTo-PythonString([string]$Valor) {
    if ($null -eq $Valor) {
        $Valor = ""
    }
    $Valor = $Valor.Replace('\', '\\')
    $Valor = $Valor.Replace('"', '\"')
    $Valor = $Valor.Replace("`r", '\r')
    $Valor = $Valor.Replace("`n", '\n')
    $Valor = $Valor.Replace("`t", '\t')
    return '"' + $Valor + '"'
}


function Leer-RedesLocales {
    if (-not (Test-Path -LiteralPath $RedesLocales)) {
        return @()
    }

    try {
        $contenido = Get-Content -LiteralPath $RedesLocales -Raw -Encoding UTF8
        if (-not $contenido -or -not $contenido.Trim()) {
            return @()
        }

        $datos = $contenido | ConvertFrom-Json
        $lista = @()

        foreach ($red in @($datos)) {
            if ($red.ssid) {
                $lista += [PSCustomObject]@{
                    ssid     = [string]$red.ssid
                    password = [string]$red.password
                    pc_ip    = if ($red.pc_ip) { [string]$red.pc_ip } else { "" }
                }
            }
        }

        return $lista
    } catch {
        Write-Warning "No se pudo leer redes.local.json: $($_.Exception.Message)"
        return @()
    }
}


function Guardar-RedesLocales([object[]]$Redes) {
    $json = ConvertTo-Json -InputObject @($Redes) -Depth 5
    Escribir-Utf8SinBom $RedesLocales $json
}


function Actualizar-Redes([object[]]$Redes, [string]$Ssid, [string]$Password, [string]$Ip) {
    $actual = [PSCustomObject]@{
        ssid     = $Ssid
        password = $Password
        pc_ip    = $Ip
    }

    $lista = @($actual)
    foreach ($red in @($Redes)) {
        if ($red.ssid -ne $Ssid) {
            $lista += $red
        }
    }
    return $lista
}


function Generar-Config([object[]]$Redes) {
    $primera = $Redes[0]

    $lineas = @(
        "# ============ Configuracion del Pico W ============",
        "# Generado por deploy_pico.ps1 segun la red actual.",
        "",
        "WIFI_SSID = $(ConvertTo-PythonString $primera.ssid)",
        "WIFI_PASSWORD = $(ConvertTo-PythonString $primera.password)",
        "PC_IP = $(ConvertTo-PythonString $primera.pc_ip)",
        "",
        "# Redes conocidas (la actual queda primero).",
        "REDES = ["
    )

    foreach ($red in @($Redes)) {
        $ssid = ConvertTo-PythonString $red.ssid
        $clave = ConvertTo-PythonString $red.password
        $pcIp = if ($red.pc_ip) { ConvertTo-PythonString $red.pc_ip } else { "None" }
        $lineas += "    {`"ssid`": $ssid, `"password`": $clave, `"pc_ip`": $pcIp},"
    }

    $lineas += @(
        "]",
        "",
        "PUERTO = 8080",
        "",
        "DISPOSITIVO = `"PICO-001`"",
        "PERIODO_ENVIO_S = 2",
        "",
        "NUM_MUESTRAS = 128",
        "",
        "DESCUBRIR_SERVIDOR = True",
        "PUERTO_DESCUBRIMIENTO = 5001",
        "ARCHIVO_SERVIDOR = `"/servidor.json`"",
        "TTL_SERVIDOR_MS = 60000",
        "COOLDOWN_DESCUBRIMIENTO_MS = 15000",
        "",
        "LOG_NIVEL = `"INFO`"",
        "LOG_ARCHIVO = `"/picur.log`"",
        "LOG_ARCHIVO_HABILITADO = True",
        "LOG_MAX_BYTES = 8192",
        "TZ_OFFSET_S = -5 * 3600"
    )

    return ($lineas -join "`r`n")
}


function Escribir-Utf8SinBom([string]$Ruta, [string]$Contenido) {
    $utf8 = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($Ruta, $Contenido, $utf8)
}


function Validar-ConfigLocal([string]$Ruta) {
    python -c "import ast,sys; ast.parse(open(sys.argv[1], encoding='utf-8').read())" $Ruta
    return ($LASTEXITCODE -eq 0)
}


function Flashear([string]$Port, [string]$ArchivoRemoto, [string]$OrigenLocal) {
    Write-Host ("  -> " + $ArchivoRemoto)
    python -m mpremote connect $Port cp $OrigenLocal $ArchivoRemoto
    if ($LASTEXITCODE -ne 0) {
        throw "mpremote cp fallo para $ArchivoRemoto"
    }
}

import os
import sys
import time

import config


_NIVELES = {
    "DEBUG": 10,
    "INFO": 20,
    "WARN": 30,
    "ERROR": 40,
}

_NIVEL = _NIVELES.get(
    str(getattr(config, "LOG_NIVEL", "INFO")).upper(), 20)
_ARCHIVO = getattr(config, "LOG_ARCHIVO", "/picur.log")
_MAX_BYTES = int(getattr(config, "LOG_MAX_BYTES", 8192))
_ARCHIVO_HABILITADO = bool(
    getattr(config, "LOG_ARCHIVO_HABILITADO", True))
_TZ_OFFSET_S = int(getattr(config, "TZ_OFFSET_S", -5 * 3600))


def _fecha_local(segundos):
    partes = time.localtime(segundos + _TZ_OFFSET_S)
    return "{:04d}-{:02d}-{:02d} {:02d}:{:02d}:{:02d}".format(*partes[:6])


def timestamp_iso():
    """Timestamp para el payload (se sanea en Spring si el RTC no tiene hora)."""
    try:
        ahora = time.time()
        if ahora >= 1700000000:
            partes = time.localtime(ahora + _TZ_OFFSET_S)
        else:
            partes = time.localtime()
        return "{:04d}-{:02d}-{:02d}T{:02d}:{:02d}:{:02d}".format(
            *partes[:6])
    except Exception:
        partes = time.localtime()
        return "{:04d}-{:02d}-{:02d}T{:02d}:{:02d}:{:02d}".format(
            *partes[:6])


def _timestamp_log():
    try:
        ahora = time.time()
        if ahora >= 1700000000:
            return _fecha_local(ahora)
    except Exception:
        pass
    return "up{}ms".format(time.ticks_ms())


def _rotar():
    if not _ARCHIVO_HABILITADO:
        return
    try:
        if os.stat(_ARCHIVO)[6] < _MAX_BYTES:
            return
    except OSError:
        return

    try:
        os.remove(_ARCHIVO + ".1")
    except OSError:
        pass

    try:
        os.rename(_ARCHIVO, _ARCHIVO + ".1")
    except OSError:
        try:
            os.remove(_ARCHIVO)
        except OSError:
            pass


def log(nivel, modulo, mensaje):
    nivel = str(nivel).upper()
    if _NIVELES.get(nivel, 20) < _NIVEL:
        return

    linea = "[{}] [{}] [{}] {}".format(
        _timestamp_log(), nivel, modulo, mensaje)
    print(linea)

    if not _ARCHIVO_HABILITADO:
        return

    _rotar()
    try:
        with open(_ARCHIVO, "a") as archivo:
            archivo.write(linea + "\n")
    except OSError as e:
        print("[logger] No se pudo escribir {}: {}".format(_ARCHIVO, e))


def debug(modulo, mensaje):
    log("DEBUG", modulo, mensaje)


def info(modulo, mensaje):
    log("INFO", modulo, mensaje)


def warn(modulo, mensaje):
    log("WARN", modulo, mensaje)


def error(modulo, mensaje):
    log("ERROR", modulo, mensaje)


def excepcion(modulo, mensaje, error):
    log("ERROR", modulo, "{}: {}".format(mensaje, error))
    try:
        sys.print_exception(error)
    except Exception:
        pass


def sincronizar_hora():
    """Sincroniza el RTC por NTP despues de conectar Wi-Fi."""
    try:
        import ntptime
        ntptime.settime()
        info("ntp", "Hora sincronizada: {}".format(timestamp_iso()))
        return True
    except Exception as e:
        warn("ntp", "No se pudo sincronizar la hora: {}".format(e))
        return False

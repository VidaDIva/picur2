import network
import time
import usocket
import ujson

import config
import logger


_PETICION = b"PICUR_DISCOVER_V1"
_CACHE = getattr(config, "ARCHIVO_SERVIDOR", "/servidor.json")
_PUERTO_DESCUBRIMIENTO = int(
    getattr(config, "PUERTO_DESCUBRIMIENTO", 5001))
_PUERTO_API = int(getattr(config, "PUERTO", 8080))
_TTL_MS = int(getattr(config, "TTL_SERVIDOR_MS", 60000))
_COOLDOWN_MS = int(getattr(config, "COOLDOWN_DESCUBRIMIENTO_MS", 15000))
_HABILITADO = bool(getattr(config, "DESCUBRIR_SERVIDOR", True))

_servidor = None
_servidor_hasta = 0
_proximo_intento = 0


def _alimentar(feed):
    if feed:
        feed()


def _misma_subred(ip, ip_local, mascara):
    try:
        a = [int(x) for x in ip.split(".")]
        b = [int(x) for x in ip_local.split(".")]
        m = [int(x) for x in mascara.split(".")]
        if len(a) != 4 or len(b) != 4 or len(m) != 4:
            return False
        for i in range(4):
            if (a[i] & m[i]) != (b[i] & m[i]):
                return False
        return True
    except Exception:
        return False


def _broadcast(ip, mascara):
    try:
        partes_ip = [int(x) for x in ip.split(".")]
        partes_mascara = [int(x) for x in mascara.split(".")]
        if len(partes_ip) != 4 or len(partes_mascara) != 4:
            return None
        return ".".join(
            str(partes_ip[i] | (255 - partes_mascara[i]))
            for i in range(4))
    except Exception:
        return None


def _leer_cache():
    try:
        with open(_CACHE) as archivo:
            datos = ujson.load(archivo)
        ip = datos.get("ip")
        puerto = int(datos.get("puerto", _PUERTO_API))
        if ip:
            return {"ip": ip, "puerto": puerto}
    except Exception:
        pass
    return None


def _guardar_cache(servidor):
    try:
        with open(_CACHE, "w") as archivo:
            ujson.dump(servidor, archivo)
    except OSError as e:
        logger.warn("descubrimiento",
                    "No se pudo guardar {}: {}".format(_CACHE, e))


def _descubrir(feed=None):
    try:
        wlan = network.WLAN(network.STA_IF)
        ip_local, mascara = wlan.ifconfig()[0], wlan.ifconfig()[1]
    except Exception as e:
        logger.warn("descubrimiento", "Sin datos de red: {}".format(e))
        return None

    destinos = ["255.255.255.255"]
    broadcast_local = _broadcast(ip_local, mascara)
    if broadcast_local and broadcast_local not in destinos:
        destinos.append(broadcast_local)

    sock = None
    try:
        sock = usocket.socket(usocket.AF_INET, usocket.SOCK_DGRAM)
        sock.setsockopt(usocket.SOL_SOCKET, usocket.SO_BROADCAST, 1)
        sock.settimeout(1.0)

        for destino in destinos:
            _alimentar(feed)
            try:
                sock.sendto(_PETICION, (destino, _PUERTO_DESCUBRIMIENTO))
            except Exception as e:
                logger.debug("descubrimiento",
                             "Broadcast fallo a {}: {}".format(destino, e))

        inicio = time.ticks_ms()
        while time.ticks_diff(time.ticks_ms(), inicio) < 2500:
            _alimentar(feed)
            try:
                datos, origen = sock.recvfrom(512)
            except OSError:
                continue

            try:
                respuesta = ujson.loads(datos)
            except Exception:
                logger.debug("descubrimiento",
                             "Respuesta invalida de {}".format(origen))
                continue

            if respuesta.get("servicio") != "picur":
                continue

            puerto = int(respuesta.get("puerto", _PUERTO_API))
            ips = respuesta.get("ips") or []

            for candidata in ips:
                if _misma_subred(candidata, ip_local, mascara):
                    logger.info("descubrimiento",
                                "Servidor en {}:{}".format(candidata, puerto))
                    return {"ip": candidata, "puerto": puerto}

            if ips:
                logger.info("descubrimiento",
                            "Servidor en {}:{}".format(ips[0], puerto))
                return {"ip": ips[0], "puerto": puerto}

        logger.warn("descubrimiento", "Sin respuesta UDP del servidor")
        return None
    except Exception as e:
        logger.warn("descubrimiento", "Error buscando servidor: {}".format(e))
        return None
    finally:
        if sock is not None:
            try:
                sock.close()
            except Exception:
                pass


def _usar_servidor(servidor, ttl_ms=None):
    global _servidor, _servidor_hasta
    _servidor = servidor
    _servidor_hasta = time.ticks_add(
        time.ticks_ms(), _TTL_MS if ttl_ms is None else ttl_ms)
    return servidor


def resolver(red=None, forzar=False, feed=None):
    """Devuelve {'ip', 'puerto'} para la API, o None si no hay servidor."""
    global _proximo_intento

    ahora = time.ticks_ms()

    if (not forzar and _servidor is not None
            and time.ticks_diff(_servidor_hasta, ahora) > 0):
        return _servidor

    if _HABILITADO and (forzar or time.ticks_diff(
            _proximo_intento, ahora) <= 0):
        servidor = _descubrir(feed)
        if servidor:
            _usar_servidor(servidor)
            _guardar_cache(servidor)
            return servidor
        _proximo_intento = time.ticks_add(ahora, _COOLDOWN_MS)

    if red:
        pc_ip = red.get("pc_ip")
        if pc_ip:
            servidor = {"ip": pc_ip, "puerto": _PUERTO_API}
            logger.warn("descubrimiento",
                        "Usando PC_IP de respaldo {}:{}".format(
                            servidor["ip"], servidor["puerto"]))
            return _usar_servidor(servidor)

    cache = _leer_cache()
    if cache:
        logger.warn("descubrimiento",
                    "Usando servidor guardado {}:{}".format(
                        cache["ip"], cache["puerto"]))
        return _usar_servidor(cache)

    logger.error("descubrimiento", "No hay servidor disponible")
    return None


def invalidar(cooldown_ms=None):
    global _servidor, _servidor_hasta, _proximo_intento
    _servidor = None
    _servidor_hasta = 0
    _proximo_intento = time.ticks_add(
        time.ticks_ms(),
        _COOLDOWN_MS if cooldown_ms is None else cooldown_ms)

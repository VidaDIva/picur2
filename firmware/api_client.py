import time

import config
import logger
import urequests


def url_medicion(servidor=None):
    if servidor:
        ip = servidor.get("ip")
        puerto = int(servidor.get("puerto", getattr(config, "PUERTO", 8080)))
    else:
        ip = getattr(config, "PC_IP", None)
        puerto = int(getattr(config, "PUERTO", 8080))

    if not ip:
        return None

    return "http://{}:{}/api/sensores/medicion".format(ip, puerto)


def enviar_medicion(payload, servidor=None, feed=None):
    """Envia una medicion a Spring Boot. Devuelve (ok, cuerpo)."""
    url = url_medicion(servidor)
    if url is None:
        logger.warn("api", "No hay IP de servidor para enviar")
        return False, "sin servidor"

    r = None
    inicio = time.ticks_ms()
    dispositivo = payload.get("dispositivo", "?")

    try:
        r = urequests.post(url, json=payload, feed=feed)
        cuerpo = r.text
        ok = r.status_code in (200, 201)
        duracion = time.ticks_diff(time.ticks_ms(), inicio)
        if ok:
            logger.debug("api", "POST {} device={} -> {} ({} ms)".format(
                url, dispositivo, r.status_code, duracion))
        else:
            logger.warn("api", "POST {} device={} -> {} ({} ms)".format(
                url, dispositivo, r.status_code, duracion))
        return ok, cuerpo
    except Exception as e:
        logger.warn("api", "Error enviando a {}: {}".format(url, e))
        return False, str(e)
    finally:
        if r is not None:
            try:
                r.close()
            except Exception:
                pass

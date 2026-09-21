import urequests
import config


def url_medicion():
    return "http://{}:{}/api/sensores/medicion".format(config.PC_IP, config.PUERTO)


def enviar_medicion(payload):
    """Envía una medición a Spring Boot.
    Devuelve (ok, cuerpo_respuesta)."""
    try:
        r = urequests.post(url_medicion(), json=payload)
        cuerpo = r.text
        ok = r.status_code in (200, 201)
        r.close()
        return ok, cuerpo
    except Exception as e:
        print("[api] Error enviando:", e)
        return False, str(e)
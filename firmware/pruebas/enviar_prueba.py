# =============================================================================
#  enviar_prueba.py  -  Verifica Wi-Fi + POST a Spring Boot SIN el MPU6050
#
#  Uso (con el servidor corriendo en el PC):
#     python -m mpremote connect com8 run firmware/pruebas/enviar_prueba.py
# =============================================================================

import time

import config
import api_client


def timestamp_iso():
    ahora = time.localtime()
    return "{:04d}-{:02d}-{:02d}T{:02d}:{:02d}:{:02d}".format(*ahora[:6])


def main():
    import wifi_manager
    if not wifi_manager.conectar(config.WIFI_SSID, config.WIFI_PASSWORD):
        print("[prueba] Sin Wi-Fi")
        return

    payload = {
        "dispositivo": config.DISPOSITIVO,
        "timestamp": timestamp_iso(),
        "aceleracionX": 0.01,
        "aceleracionY": -0.02,
        "aceleracionZ": 1.01,
        "frecuencia": 12.5,
        "rms": 0.05,
        "inclinacion": 0.4,
        "vibracion": 58.4,
        "umbral": 42.1,
        "estado": "PELIGRO",
    }
    ok, cuerpo = api_client.enviar_medicion(payload)
    print("[prueba] enviado:", ok)
    print("[prueba] respuesta:", cuerpo)


main()

# =============================================================================
#  enviar_prueba.py  -  Verifica Wi-Fi + POST a Spring Boot SIN el MPU6050
#
#  Uso (con el servidor corriendo en el PC):
#     python -m mpremote connect com8 run firmware/pruebas/enviar_prueba.py
# =============================================================================

import config
import logger
import descubrimiento
import wifi_manager
import api_client


def obtener_redes():
    redes = getattr(config, "REDES", None)
    if redes:
        return wifi_manager.normalizar_redes(redes)
    if getattr(config, "WIFI_SSID", None):
        return [{
            "ssid": config.WIFI_SSID,
            "password": getattr(config, "WIFI_PASSWORD", ""),
            "pc_ip": getattr(config, "PC_IP", None),
        }]
    return []


def main():
    red = wifi_manager.conectar_redes(obtener_redes())
    if red is None:
        print("[prueba] Sin Wi-Fi")
        return

    logger.sincronizar_hora()
    servidor = descubrimiento.resolver(red, forzar=True)
    if servidor is None:
        print("[prueba] Servidor no encontrado")
        return

    payload = {
        "dispositivo": config.DISPOSITIVO,
        "timestamp": logger.timestamp_iso(),
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
    ok, cuerpo = api_client.enviar_medicion(payload, servidor=servidor)
    print("[prueba] enviado:", ok)
    print("[prueba] respuesta:", cuerpo)


main()

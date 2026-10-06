import network
import time
import machine


def _wlan():
    """Devuelve la interfaz STA activa (reutilizable entre llamadas)."""
    w = network.WLAN(network.STA_IF)
    if not w.active():
        w.active(True)
        time.sleep(0.2)
    return w


def conectar(ssid, password, timeout_s=15):
    """Conecta el Pico W a la red Wi-Fi y devuelve True/False."""
    wlan = _wlan()

    if wlan.isconnected():
        print("[wifi] Ya conectado. IP:", wlan.ifconfig()[0])
        return True

    print("[wifi] Conectando a:", ssid)

    # Limpia cualquier estadio 'connecting/joined' heredado de antes:
    # la Pico W a veces ya no responde a connect() si no se desconecta antes.
    wlan.disconnect()
    time.sleep(0.2)

    wlan.connect(ssid, password)

    inicio = time.ticks_ms()
    while not wlan.isconnected():
        if time.ticks_diff(time.ticks_ms(), inicio) > timeout_s * 1000:
            print("[wifi] Error: timeout conectando a", ssid)
            return False
        time.sleep(0.5)

    print("[wifi] Conectado. IP:", wlan.ifconfig()[0])
    return True


def _reciclar_interfaz():
    """Apaga y enciende la interfaz STA: desbloquea el estado colgado
    del stack Wi-Fi que se produce tras perder la conexion."""
    wlan = network.WLAN(network.STA_IF)
    wlan.active(False)
    time.sleep(0.5)
    wlan.active(True)
    time.sleep(0.5)


def asegurar_conexion(ssid, password, timeout_s=15, intentos_maximos=3):
    """Devuelve True si hay conexion; si se cayo, intenta reconectar.

    Si tras varios intentos no lo logra, reinicia el Pico: este es el
    único remedio fiable cuando el firmware Wi-Fi queda en un estado
    corrupto que ya no responde a connect()."""
    if network.WLAN(network.STA_IF).isconnected():
        return True

    print("[wifi] Conexion perdida. Reconectando...")

    for intento in range(1, intentos_maximos + 1):
        _reciclar_interfaz()
        if conectar(ssid, password, timeout_s):
            return True
        print("[wifi] Intento %d/%d fallido" % (intento, intentos_maximos))
        time.sleep(2)

    print("[wifi] Sin Wi-Fi tras %d intentos. Reiniciando el Pico..." % intentos_maximos)
    time.sleep(2)
    machine.reset()
import network
import time


def conectar(ssid, password, timeout_s=15):
    """Conecta el Pico W a la red Wi-Fi y devuelve True/False."""
    wlan = network.WLAN(network.STA_IF)
    wlan.active(True)

    if wlan.isconnected():
        print("[wifi] Ya conectado. IP:", wlan.ifconfig()[0])
        return True

    print("[wifi] Conectando a:", ssid)
    wlan.connect(ssid, password)

    inicio = time.ticks_ms()
    while not wlan.isconnected():
        if time.ticks_diff(time.ticks_ms(), inicio) > timeout_s * 1000:
            print("[wifi] Error: timeout conectando a", ssid)
            return False
        time.sleep(0.5)

    print("[wifi] Conectado. IP:", wlan.ifconfig()[0])
    return True
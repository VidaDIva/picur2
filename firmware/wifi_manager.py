import network
import time

import logger


PM_SIN_AHORRO = 0xA11140

_red_actual = None
_ultima_red = None


def _wlan():
    """Devuelve la interfaz STA activa (reutilizable entre llamadas)."""
    w = network.WLAN(network.STA_IF)
    if not w.active():
        w.active(True)
        time.sleep_ms(200)
    _sin_ahorro(w)
    return w


def _sin_ahorro(wlan):
    """Desactiva el ahorro de energia del CYW43 (evita desconexiones)."""
    try:
        wlan.config(pm=PM_SIN_AHORRO)
    except Exception:
        pass


def _esperar(ms, feed=None):
    """Duerme en tramos cortos para poder alimentar el watchdog."""
    while ms > 0:
        tramo = 200 if ms > 200 else ms
        time.sleep_ms(tramo)
        ms -= tramo
        if feed:
            feed()


def _normalizar_red(red):
    if isinstance(red, dict):
        ssid = red.get("ssid")
        password = red.get("password")
        pc_ip = red.get("pc_ip")
    elif isinstance(red, (tuple, list)) and len(red) >= 2:
        ssid = red[0]
        password = red[1]
        pc_ip = red[2] if len(red) > 2 else None
    else:
        return None

    if not ssid:
        return None

    return {
        "ssid": str(ssid),
        "password": "" if password is None else str(password),
        "pc_ip": pc_ip,
    }


def normalizar_redes(redes):
    resultado = []
    if not redes:
        return resultado
    for red in redes:
        normalizada = _normalizar_red(red)
        if normalizada is not None:
            resultado.append(normalizada)
    return resultado


def _ssids_visibles(wlan):
    visibles = []
    try:
        for red in wlan.scan():
            ssid = red[0]
            if isinstance(ssid, bytes):
                ssid = ssid.decode("utf-8")
            if ssid:
                visibles.append(str(ssid))
    except Exception as e:
        logger.warn("wifi", "No se pudo escanear redes: {}".format(e))
    return visibles


def _ssid_actual(wlan):
    try:
        ssid = wlan.config("ssid")
        if isinstance(ssid, bytes):
            ssid = ssid.decode("utf-8")
        return str(ssid) if ssid else None
    except Exception:
        return None


def _recordar(red):
    global _red_actual, _ultima_red
    _red_actual = red
    _ultima_red = red


def esta_conectado():
    return network.WLAN(network.STA_IF).isconnected()


def red_actual():
    return _red_actual


def ip_actual():
    try:
        return network.WLAN(network.STA_IF).ifconfig()[0]
    except Exception:
        return None


def _ordenar_redes(redes, visibles):
    orden = []
    pendientes = list(redes)

    if _ultima_red is not None:
        for red in pendientes:
            if red["ssid"] == _ultima_red["ssid"]:
                orden.append(red)
                pendientes.remove(red)
                break

    for red in list(pendientes):
        if red["ssid"] in visibles:
            orden.append(red)
            pendientes.remove(red)

    orden.extend(pendientes)
    return orden


def conectar(ssid, password, timeout_s=15, feed=None):
    """Conecta el Pico W a una red y devuelve True/False."""
    wlan = _wlan()

    if wlan.isconnected():
        logger.info("wifi", "Ya conectado a {} IP={}".format(
            _ssid_actual(wlan), wlan.ifconfig()[0]))
        return True

    logger.info("wifi", "Conectando a {}".format(ssid))

    # Limpia cualquier estado 'connecting/joined' heredado de antes:
    # la Pico W a veces ya no responde a connect() si no se desconecta antes.
    try:
        wlan.disconnect()
    except Exception:
        pass
    _esperar(200, feed)

    try:
        wlan.connect(ssid, password)
    except OSError as e:
        logger.warn("wifi", "No se pudo iniciar conexion a {}: {}".format(
            ssid, e))
        return False

    inicio = time.ticks_ms()
    while not wlan.isconnected():
        if feed:
            feed()
        if time.ticks_diff(time.ticks_ms(), inicio) > timeout_s * 1000:
            try:
                estado = wlan.status()
            except Exception:
                estado = "?"
            logger.warn("wifi", "Timeout conectando a {} (status {})".format(
                ssid, estado))
            return False
        _esperar(500, feed)

    logger.info("wifi", "Conectado a {} IP={}".format(
        ssid, wlan.ifconfig()[0]))
    return True


def conectar_redes(redes, timeout_s=15, feed=None):
    """Prueba las redes conocidas y devuelve la que conecto, o None."""
    redes = normalizar_redes(redes)
    if not redes:
        logger.error("wifi", "No hay redes configuradas")
        return None

    wlan = _wlan()
    if wlan.isconnected():
        actual = _ssid_actual(wlan)
        for red in redes:
            if red["ssid"] == actual:
                _recordar(red)
                logger.info("wifi", "Ya conectado a {} IP={}".format(
                    actual, wlan.ifconfig()[0]))
                return red
        red = {"ssid": actual, "password": "", "pc_ip": None}
        _recordar(red)
        logger.warn("wifi", "Conectado a una red no configurada: {}".format(
            actual))
        return red

    visibles = _ssids_visibles(wlan)
    orden = _ordenar_redes(redes, visibles)

    logger.info("wifi", "Redes visibles: {}".format(
        ", ".join(visibles) if visibles else "(ninguna)"))

    for red in orden:
        if feed:
            feed()
        if conectar(red["ssid"], red["password"], timeout_s, feed):
            _recordar(red)
            return red
        _esperar(500, feed)

    logger.error("wifi", "No se pudo conectar a ninguna red conocida")
    return None


def _reciclar_interfaz(feed=None):
    """Apaga y enciende la interfaz STA: desbloquea el estado colgado
    del stack Wi-Fi que se produce tras perder la conexion."""
    wlan = network.WLAN(network.STA_IF)
    wlan.active(False)
    _esperar(500, feed)
    wlan.active(True)
    _esperar(500, feed)
    _sin_ahorro(wlan)


def asegurar_conexion(redes, timeout_s=8, intentos_maximos=2, feed=None):
    """Devuelve la red activa o None; nunca reinicia el Pico."""
    global _red_actual

    redes = normalizar_redes(redes)
    wlan = _wlan()

    if wlan.isconnected():
        actual = _ssid_actual(wlan)
        if _red_actual is None or _red_actual.get("ssid") != actual:
            for red in redes:
                if red["ssid"] == actual:
                    _recordar(red)
                    break
            if _red_actual is None or _red_actual.get("ssid") != actual:
                _recordar({"ssid": actual, "password": "", "pc_ip": None})
        return _red_actual

    logger.warn("wifi", "Conexion perdida. Reconectando...")

    for intento in range(1, intentos_maximos + 1):
        if feed:
            feed()
        if intento > 1:
            _reciclar_interfaz(feed)

        if _red_actual is not None:
            try:
                if conectar(_red_actual["ssid"], _red_actual["password"],
                            timeout_s, feed):
                    logger.info("wifi", "Reconectado a {}".format(
                        _red_actual["ssid"]))
                    return _red_actual
            except OSError as e:
                logger.warn("wifi", "Error reconectando: {}".format(e))

        red = conectar_redes(redes, timeout_s, feed)
        if red is not None:
            return red

        logger.warn("wifi", "Intento {}/{} fallido".format(
            intento, intentos_maximos))
        _esperar(1000, feed)

    logger.warn("wifi", "Sin Wi-Fi; se omite el envio y se reintentara luego")
    return None

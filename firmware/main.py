import sys
import time
import machine
import os

import config
import logger
import descubrimiento
import wifi_manager
import mpu6050
import piezo
import semaforo
import sirena
import api_client
import procesar
import clasificador
import lcd_i2c


def timestamp_iso():
    return logger.timestamp_iso()


def nombre_reset(causa):
    for nombre, valor in (
            ("PWRON_RESET", getattr(machine, "PWRON_RESET", None)),
            ("WDT_RESET", getattr(machine, "WDT_RESET", None)),
            ("SOFT_RESET", getattr(machine, "SOFT_RESET", None)),
            ("DEEPSLEEP_RESET", getattr(machine, "DEEPSLEEP_RESET", None)),
            ("HARD_RESET", getattr(machine, "HARD_RESET", None))):
        if valor is not None and causa == valor:
            return nombre
    return str(causa)


def registrar_arranque(causa):
    """Deja rastro de la causa de reset incluso si el puerto USB se cae."""
    try:
        try:
            grande = os.stat("/arranque.log")[6] > 2000
        except OSError:
            grande = False
        with open("/arranque.log", "w" if grande else "a") as archivo:
            archivo.write("{} {} {}\n".format(
                time.ticks_ms(), causa, timestamp_iso()))
    except OSError:
        pass


def obtener_redes():
    """Devuelve REDES o, por compatibilidad, la config antigua."""
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


def construir_payload(w, base, estado):
    mx, my, mz = w["media"]
    return {
        "dispositivo": config.DISPOSITIVO,
        "timestamp": timestamp_iso(),
        "aceleracionX": mx,
        "aceleracionY": my,
        "aceleracionZ": mz,
        "frecuencia": w["frecuencia"],
        "rms": w["rms"],
        "inclinacion": w["inclinacion"],
        "vibracion": round(w["vib"], 2),
        "umbral": round(base["umbral_normal"], 2),
        "estado": clasificador.NOMBRE_ESTADO[estado],
    }


def enviar(w, base, estado, redes, feed=None):
    try:
        red = wifi_manager.asegurar_conexion(redes, feed=feed)
    except Exception as e:
        logger.excepcion("main", "Error de red", e)
        return False

    if red is None:
        logger.warn("main", "Sin Wi-Fi. Envio omitido")
        return False

    servidor = descubrimiento.resolver(red, feed=feed)
    if servidor is None:
        logger.warn("main", "Sin servidor. Envio omitido")
        return False

    payload = construir_payload(w, base, estado)
    try:
        ok, cuerpo = api_client.enviar_medicion(
            payload, servidor=servidor, feed=feed)
    except Exception as e:
        logger.excepcion("main", "Error enviando medicion", e)
        descubrimiento.invalidar()
        return False

    if not ok:
        descubrimiento.invalidar()

    print("[main] fs envio:", ok, payload)
    return ok


def mostrar_lcd(lcd, estado, w, base):
    """Refresca la pantalla LCD 16x2: linea 1 el estado, linea 2 los datos.

    w es la ultima ventana limpia (o None si aun no hay mediciones)."""
    if lcd is None:
        return
    nombre = clasificador.NOMBRE_ESTADO[estado]
    if w is None:
        lcd.mostrar("EST:" + nombre, "Esperando...")
        return
    linea2 = "V{:.2f} U{:.2f} F{:.0f}".format(
        w["vib"], base["umbral_normal"], w["frecuencia"])
    lcd.mostrar("EST:" + nombre, linea2)


def main():
    try:
        causa = machine.reset_cause()
    except Exception:
        causa = "desconocida"
    registrar_arranque(causa)
    logger.info("main", "Arranque (causa de reset: {})".format(
        nombre_reset(causa)))

    sem = semaforo.Semaforo()
    pzt = piezo.Piezo()
    sir = sirena.Sirena()

    redes = obtener_redes()
    logger.info("main", "Config device={} redes={} pc_ip={} puerto={}".format(
        getattr(config, "DISPOSITIVO", "?"),
        ", ".join([red["ssid"] for red in redes]) if redes else "(ninguna)",
        getattr(config, "PC_IP", "?"),
        getattr(config, "PUERTO", "?")))

    # --- Wi-Fi: reintento indefinido (el sistema espera, nunca aborta) ---
    red = None
    intento = 0
    while red is None:
        red = wifi_manager.conectar_redes(redes)
        if red is None:
            intento += 1
            logger.warn("main", "Sin conexion Wi-Fi (intento {}). "
                                "Reintentando...".format(intento))
            sem.amarillo(intento % 2 == 0)
            time.sleep_ms(2000)
    sem.apagar()
    logger.info("main", "Wi-Fi listo en {}".format(red["ssid"]))
    logger.sincronizar_hora()

    # --- MPU6050: reintento indefinido (cable flojo al arranque) ---
    mpu = None
    intento = 0
    while mpu is None:
        try:
            mpu = mpu6050.MPU6050()
            print("[main] MPU-6050 listo")
        except OSError as e:
            intento += 1
            print("[main] MPU no responde (intento {}): {}".format(
                intento, e))
            sem.amarillo(intento % 2 == 0)
            time.sleep_ms(1000)
    sem.apagar()

    # --- LCD 16x2 I2C (comparte el bus del MPU, direccion 0x27/0x3F) ---
    lcd = None
    for direccion in (0x27, 0x3F):
        try:
            lcd = lcd_i2c.LcdI2C(mpu.i2c, direccion=direccion)
            print("[main] LCD 16x2 listo en 0x{:02X}".format(direccion))
            break
        except Exception as e:
            print("[main] LCD no responde en 0x{:02X}: {}".format(
                direccion, e))
    if lcd is not None:
        lcd.mostrar("Picur", "Iniciando...")
    else:
        print("[main] Sin LCD: se continua solo con LEDs/envios.")

    # --- Autotest de arranque (LEDs + audio) ---
    sem.autotest()
    sir.pitido()

    # --- Aprendizaje de la línea base (~10 s, sin envíos) ---
    def al_avance_calibracion(ventana):
        sem.parpadeo_aprendizaje(ventana)
        if lcd is not None and ventana % 10 == 0:
            pct = min(100, (ventana + 1) * 100 // clasificador.CAL_VENTANAS)
            try:
                lcd.mostrar("Calibrando...", "{}%".format(pct))
            except OSError as e:
                print("[main] LCD error durante calibracion:", e)

    base = None
    intento = 0
    while base is None:
        try:
            base = clasificador.calibrar_linea_base(
                mpu, pzt, al_avance=al_avance_calibracion)
        except OSError as e:
            intento += 1
            print("[main] Calibracion fallida ({}): {}".format(intento, e))
            sys.print_exception(e)
            sem.amarillo(intento % 2 == 0)
            time.sleep_ms(2000)
    sem.apagar()
    sir.pitido(2000, 150)

    if not wifi_manager.esta_conectado():
        logger.warn("main", "Wi-Fi perdido durante la calibracion. "
                            "Reconectando...")
        red = wifi_manager.asegurar_conexion(redes)
        if red is None:
            logger.warn("main", "Sin Wi-Fi tras calibracion")
        else:
            logger.info("main", "Wi-Fi recuperado en {}".format(red["ssid"]))

    servidor = descubrimiento.resolver(
        wifi_manager.red_actual(), forzar=True)
    if servidor is not None:
        logger.info("main", "Servidor API {}:{}".format(
            servidor["ip"], servidor["puerto"]))
    else:
        logger.warn("main", "Servidor API no disponible aun")

    ctx = clasificador.nuevo_contexto()
    estado = clasificador.NORMAL
    sem.mostrar(estado)
    ultima = None
    lcd_fallos = 0
    proximo_intento_wifi = time.ticks_ms()
    envio_en = time.ticks_ms()
    lcd_estado_prev = None
    lcd_ms_prev = time.ticks_ms()
    errores_sensor = 0
    envios_ok = 0
    envios_fallidos = 0
    ultimo_resumen = time.ticks_ms()
    logger.info("main", "Monitoreo activo - estado NORMAL")

    # Watchdog: si el loop se cuelga (p. ej. red sin respuesta), el Pico
    # se reinicia solo en <=8 s y vuelve a arrancar con los reintentos.
    wdt = machine.WDT(timeout=8000)

    while True:
        wdt.feed()
        # --- Lectura de la ventana (I2C/ADC) ---
        try:
            w = procesar.leer_ventana(mpu, pzt, sir)
        except OSError as e:
            # Cable suelto, ruido en el bus, etc.: no detener el sistema
            errores_sensor += 1
            if errores_sensor == 1 or errores_sensor % 50 == 0:
                logger.excepcion(
                    "main",
                    "Error de sensor ({} veces)".format(errores_sensor), e)
            else:
                print("[main] Error de sensor:", e)
            time.sleep_ms(200)
            continue

        if w["limpia"]:
            ultima = w
            nuevo, motivo = clasificador.clasificar(w, base, ctx)

            if estado == clasificador.PELIGRO:
                # La alarma queda enclavada hasta recuperar ventanas normales
                if nuevo == clasificador.NORMAL:
                    ctx["c_recup"] = ctx["c_recup"] + 1
                else:
                    ctx["c_recup"] = 0
                if ctx["c_recup"] >= clasificador.N_RECUP:
                    sir.detener()
                    ctx = clasificador.nuevo_contexto()
                    estado = clasificador.NORMAL
                    sem.mostrar(estado)
                    logger.info("main", "Patron recuperado: estado NORMAL")
            else:
                if nuevo != estado:
                    estado = nuevo
                    sem.mostrar(estado)
                    if estado == clasificador.PELIGRO:
                        sir.iniciar()
                        logger.warn("main", "PELIGRO: " + motivo)
                    elif estado == clasificador.ANOMALIA:
                        logger.warn("main", "ALERTA: " + motivo)
                    else:
                        logger.info("main", "Estado NORMAL: trafico en patron")

                trafico = (estado == clasificador.NORMAL
                           and w["vib"] > base["umbral_trafico"])
                if trafico and not ctx["trafico_prev"]:
                    print("[main] Trafico pesado detectado (patron normal)")
                ctx["trafico_prev"] = trafico

                if clasificador.ADAPTAR_BASE and estado == clasificador.NORMAL:
                    clasificador.adaptar_base(base, w["vib"])

        # --- Pantalla LCD: estado + ultima ventana limpia (max. 1 vez/s) ---
        ahora = time.ticks_ms()
        if (lcd is not None and ultima is not None
                and (estado != lcd_estado_prev
                     or time.ticks_diff(ahora, lcd_ms_prev) >= 1000)):
            try:
                mostrar_lcd(lcd, estado, ultima, base)
                lcd_fallos = 0
            except OSError as e:
                lcd_fallos += 1
                print("[main] LCD error ({}): {}".format(lcd_fallos, e))
                if lcd_fallos >= 3:
                    logger.warn("main", "LCD deshabilitado tras fallos de I2C")
                    lcd = None
            lcd_estado_prev = estado
            lcd_ms_prev = ahora

        # --- Envío periódico a Spring Boot (entre ventanas) ---
        ahora = time.ticks_ms()
        if ultima is not None and time.ticks_diff(ahora, envio_en) >= 0:
            if (not wifi_manager.esta_conectado()
                    and time.ticks_diff(ahora, proximo_intento_wifi) < 0):
                print("[main] Wi-Fi caido. Envio omitido; seguimos midiendo.")
                envio_en = time.ticks_add(ahora, config.PERIODO_ENVIO_S * 1000)
            else:
                ok = enviar(ultima, base, estado, redes, wdt.feed)
                if ok:
                    envios_ok += 1
                    proximo_intento_wifi = 0
                else:
                    envios_fallidos += 1
                    if not wifi_manager.esta_conectado():
                        proximo_intento_wifi = time.ticks_add(
                            time.ticks_ms(), 15000)
                envio_en = time.ticks_add(
                    envio_en, config.PERIODO_ENVIO_S * 1000)
                if time.ticks_diff(time.ticks_ms(), envio_en) >= 0:
                    # Se atrasó (Wi-Fi lenta): re-sincroniza el período
                    envio_en = time.ticks_add(
                        time.ticks_ms(), config.PERIODO_ENVIO_S * 1000)

        # --- Resumen periódico (una escritura por minuto, no por envío) ---
        ahora = time.ticks_ms()
        if time.ticks_diff(ahora, ultimo_resumen) >= 60000:
            red_actual = wifi_manager.red_actual()
            logger.info(
                "main",
                "Resumen estado={} ok={} fallidos={} red={} ip={}".format(
                    clasificador.NOMBRE_ESTADO[estado],
                    envios_ok,
                    envios_fallidos,
                    red_actual["ssid"] if red_actual else "-",
                    wifi_manager.ip_actual() or "-"))
            ultimo_resumen = ahora


if __name__ == "__main__":
    try:
        main()
        # main() no debe retornar (todos sus caminos son bucles)
        logger.warn("main", "main() termino inesperadamente. Reiniciando...")
        time.sleep_ms(2000)
        machine.reset()
    except KeyboardInterrupt:
        from machine import Pin, PWM
        for pin in (13, 14, 15):
            Pin(pin, Pin.OUT).value(0)
        PWM(Pin(16)).duty_u16(0)
        logger.info("main", "Monitoreo detenido")
    except Exception as e:
        logger.excepcion("main", "Error fatal", e)
        logger.warn("main", "Reiniciando en 2 s...")
        time.sleep_ms(2000)
        machine.reset()

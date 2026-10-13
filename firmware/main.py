import sys
import time
import machine

import config
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
    ahora = time.localtime()
    return "{:04d}-{:02d}-{:02d}T{:02d}:{:02d}:{:02d}".format(*ahora[:6])


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


def enviar(w, base, estado):
    try:
        conectado = wifi_manager.asegurar_conexion(
            config.WIFI_SSID, config.WIFI_PASSWORD)
    except OSError as e:
        print("[main] Error de red:", e)
        sys.print_exception(e)
        return False
    if not conectado:
        print("[main] Sin Wi-Fi. Envio omitido.")
        return False
    payload = construir_payload(w, base, estado)
    ok, cuerpo = api_client.enviar_medicion(payload)
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
    print("[main] Arranque (causa de reset: {})".format(causa))

    sem = semaforo.Semaforo()
    pzt = piezo.Piezo()
    sir = sirena.Sirena()

    # --- Wi-Fi: reintento indefinido (el sistema espera, nunca aborta) ---
    intento = 0
    while not wifi_manager.conectar(config.WIFI_SSID, config.WIFI_PASSWORD):
        intento += 1
        print("[main] Sin conexion Wi-Fi (intento {}). Reintentando...".format(
            intento))
        sem.amarillo(intento % 2 == 0)
        time.sleep_ms(2000)
    sem.apagar()

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
            lcd.mostrar("Calibrando...", "{}%".format(pct))

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

    ctx = clasificador.nuevo_contexto()
    estado = clasificador.NORMAL
    sem.mostrar(estado)
    ultima = None
    envio_en = time.ticks_ms()
    lcd_estado_prev = None
    lcd_ms_prev = time.ticks_ms()
    print("[main] Monitoreo activo - estado NORMAL")

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
            print("[main] Error de sensor:", e)
            sys.print_exception(e)
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
                    print("[main] Patron recuperado: estado NORMAL")
            else:
                if nuevo != estado:
                    estado = nuevo
                    sem.mostrar(estado)
                    if estado == clasificador.PELIGRO:
                        sir.iniciar()
                        print("[main] ¡PELIGRO! " + motivo)
                    elif estado == clasificador.ANOMALIA:
                        print("[main] ALERTA: " + motivo)
                    else:
                        print("[main] Estado NORMAL: trafico en patron")

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
            mostrar_lcd(lcd, estado, ultima, base)
            lcd_estado_prev = estado
            lcd_ms_prev = ahora

        # --- Envío periódico a Spring Boot (entre ventanas) ---
        if ultima is not None and time.ticks_diff(
                time.ticks_ms(), envio_en) >= 0:
            enviar(ultima, base, estado)
            envio_en = time.ticks_add(envio_en, config.PERIODO_ENVIO_S * 1000)
            if time.ticks_diff(time.ticks_ms(), envio_en) >= 0:
                # Se atrasó (Wi-Fi lenta): re-sincroniza el período
                envio_en = time.ticks_add(
                    time.ticks_ms(), config.PERIODO_ENVIO_S * 1000)


if __name__ == "__main__":
    try:
        main()
        # main() no debe retornar (todos sus caminos son bucles)
        print("[main] main() termino inesperadamente. Reiniciando...")
        time.sleep_ms(2000)
        machine.reset()
    except KeyboardInterrupt:
        from machine import Pin, PWM
        for pin in (13, 14, 15):
            Pin(pin, Pin.OUT).value(0)
        PWM(Pin(16)).duty_u16(0)
        print("[main] Monitoreo detenido.")
    except Exception as e:
        print("[main] Error fatal:", e)
        sys.print_exception(e)
        print("[main] Reiniciando en 2 s...")
        time.sleep_ms(2000)
        machine.reset()

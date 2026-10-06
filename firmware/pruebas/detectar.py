# =============================================================================
#  detectar.py  -  Autotest de hardware (NO se despliega al dispositivo)
#
#  Uso:  python -m mpremote connect com8 run firmware/pruebas/detectar.py
#
#  Verifica:
#   1. MPU6050 en I2C0 (SDA=GP4, SCL=GP5): presencia, WHO_AM_I, accel y gyro
#   2. Piezo en GP26/ADC0: lectura cruda y variacion
#   3. Semáforo: GP13 (verde), GP14 (amarillo), GP15 (rojo)
#   4. Audio: PWM GP16 (pitido corto)
# =============================================================================

from machine import Pin, SoftI2C, ADC, PWM
from time import sleep_ms

MPU_ADDR = 0x68
PIN_SDA, PIN_SCL = 4, 5
PIN_PIEZO = 26
PIN_VERDE, PIN_AMARILLO, PIN_ROJO = 13, 14, 15
PIN_AUDIO = 16

resultados = []


def _s16(b):
    """Entero con signo de 2 bytes (MicroPython ignora signed en from_bytes)."""
    v = (b[0] << 8) | b[1]
    return v - 0x10000 if v >= 0x8000 else v


def reportar(nombre, ok, detalle):
    resultados.append(ok)
    print("{} {} - {}".format("[PASS]" if ok else "[FAIL]", nombre, detalle))


def probar_mpu():
    try:
        i2c = SoftI2C(sda=Pin(PIN_SDA), scl=Pin(PIN_SCL), freq=400000)
    except Exception as e:
        reportar("MPU6050", False, "no se pudo abrir el bus I2C: {}".format(e))
        return

    encontrados = i2c.scan()
    if MPU_ADDR not in encontrados:
        reportar("MPU6050", False,
                 "0x{:02X} no esta en el bus (encontrados: {}) - revise "
                 "SDA=GP4, SCL=GP5 y 3.3V".format(MPU_ADDR, encontrados))
        return
    reportar("MPU6050", True, "detectado en 0x{:02X} (bus: {})".format(
        MPU_ADDR, encontrados))

    try:
        quien = i2c.readfrom_mem(MPU_ADDR, 0x75, 1)[0]
        # El MPU6050 original devuelve 0x68; los clones pueden devolver
        # otros valores mientras el registro sea legible.
        reportar("MPU6050 WHO_AM_I", quien not in (0x00, 0xFF),
                 "valor 0x{:02X} (original: 0x68)".format(quien))
    except Exception as e:
        reportar("MPU6050 WHO_AM_I", False, "error I2C: {}".format(e))
        return

    try:
        i2c.writeto_mem(MPU_ADDR, 0x6B, b"\x00")   # despertar
        sleep_ms(100)
        crudo = i2c.readfrom_mem(MPU_ADDR, 0x3B, 6)
        ax, ay, az = _s16(crudo[0:2]), _s16(crudo[2:4]), _s16(crudo[4:6])
        # En reposo el vector suma ~1 g (el modulo puede estar inclinado)
        magnitud = (ax * ax + ay * ay + az * az) ** 0.5 / 16384.0
        reposo = 0.8 < magnitud < 1.2
        reportar("MPU6050 accel", reposo,
                 "ax={} ay={} az={} |a|={:.2f} g (esperado ~1 g)".format(
                     ax, ay, az, magnitud))
    except Exception as e:
        reportar("MPU6050 accel", False, "error I2C: {}".format(e))

    try:
        crudo = i2c.readfrom_mem(MPU_ADDR, 0x43, 6)
        gx, gy, gz = _s16(crudo[0:2]), _s16(crudo[2:4]), _s16(crudo[4:6])
        reposo = max(abs(gx), abs(gy), abs(gz)) < 3000
        reportar("MPU6050 gyro", reposo,
                 "gx={} gy={} gz={} (en reposo cerca de 0)".format(gx, gy, gz))
    except Exception as e:
        reportar("MPU6050 gyro", False, "error I2C: {}".format(e))


def probar_piezo():
    try:
        adc = ADC(Pin(PIN_PIEZO))
        muestras = [adc.read_u16() >> 4 for _ in range(50)]
        mn, mx = min(muestras), max(muestras)
        reportar("Piezo GP26", True,
                 "12 bits: min={} max={} (toque el sensor: debe variar)".format(
                     mn, mx))
    except Exception as e:
        reportar("Piezo GP26", False, "error ADC: {}".format(e))


def probar_semaforo():
    pines = ((PIN_VERDE, "verde"), (PIN_AMARILLO, "amarillo"),
             (PIN_ROJO, "rojo"))
    ok = True
    for pin_num, nombre in pines:
        try:
            led = Pin(pin_num, Pin.OUT)
            led.value(1)
            sleep_ms(250)
            led.value(0)
            print("       LED {} (GP{}) encendido 250 ms".format(
                nombre, pin_num))
        except Exception as e:
            ok = False
            reportar("Semaforo GP{}".format(pin_num), False, str(e))
    if ok:
        reportar("Semaforo", True, "GP13/GP14/GP15 parpadearon (verifique "
                                   "visualmente los 3 LEDs)")


def probar_audio():
    try:
        pwm = PWM(Pin(PIN_AUDIO))
        pwm.freq(1500)
        pwm.duty_u16(32768)
        sleep_ms(150)
        pwm.duty_u16(0)
        pwm.deinit()
        reportar("Audio GP16", True, "pitido 1500 Hz 120 ms (verifique "
                                     "PAM8403)")
    except Exception as e:
        reportar("Audio GP16", False, str(e))


print("=== Autotest de hardware ===")
probar_mpu()
probar_piezo()
probar_semaforo()
probar_audio()

fallidos = resultados.count(False)
print("=== Resultado: {} PASS / {} FAIL ===".format(
    resultados.count(True), fallidos))

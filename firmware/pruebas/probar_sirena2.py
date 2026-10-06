# Prueba comparativa de patrones de alarma (no usa sirena.py).
# Correr con: python -m mpremote connect com8 run firmware/pruebas/probar_sirena2.py
#
# Variante 1 - EEW japonés: dos tonos alternando rápido (dobi-dobi urgente)
# Variante 2 - Wail de horno: sube, sostiene agudo, baja (sirena de tornado)
from machine import Pin, PWM
from time import ticks_ms, ticks_diff, sleep_ms

PIN = 16
DUTY = 32768          # 50 % onda cuadrada

pwm = PWM(Pin(PIN))
pwm.duty_u16(0)


def apagar():
    pwm.duty_u16(0)


def variante_eew(rafagas=3):
    """Alterna 880/660 Hz cada 110 ms durante 2.5 s, pausa 0.75 s."""
    for i in range(rafagas):
        t0 = ticks_ms()
        n = 0
        while ticks_diff(ticks_ms(), t0) < 2500:
            pwm.freq(880 if n % 2 == 0 else 660)
            pwm.duty_u16(DUTY)
            sleep_ms(110)
            n += 1
        apagar()
        print("[prueba] eew: ráfaga {} lista".format(i + 1))
        sleep_ms(750)


def variante_horno(rafagas=2):
    """Sube 300->1300 Hz en 1.5 s, sostiene 1.5 s, baja 1.5 s, pausa 0.8 s."""
    for i in range(rafagas):
        t0 = ticks_ms()
        while ticks_diff(ticks_ms(), t0) < 1500:
            f = 300 + int(1000 * ticks_diff(ticks_ms(), t0) / 1500)
            pwm.freq(f)
            pwm.duty_u16(DUTY)
            sleep_ms(20)
        pwm.freq(1300)
        sleep_ms(1500)
        t0 = ticks_ms()
        while ticks_diff(ticks_ms(), t0) < 1500:
            f = 1300 - int(1000 * ticks_diff(ticks_ms(), t0) / 1500)
            pwm.freq(f)
            sleep_ms(20)
        apagar()
        print("[prueba] horno: ráfaga {} lista".format(i + 1))
        sleep_ms(800)


print("[prueba] === Variante 1: EEW japonés ===")
variante_eew()
sleep_ms(1000)
print("[prueba] === Variante 2: wail de horno ===")
variante_horno()
apagar()
print("[prueba] fin - ¿cuál te gusta más?")

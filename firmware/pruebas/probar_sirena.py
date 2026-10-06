# Reproduce el wail de la sirena ~12 s para juzgar el sonido por oído.
# Correr con: python -m mpremote connect com8 run firmware/pruebas/probar_sirena.py
# (importa sirena.py del dispositivo: flashearla primero con mpremote cp)
from time import ticks_ms, ticks_diff, sleep_ms

import sirena


def frecuencia_aprox(pos):
    if pos >= sirena.SIRENA_ON_MS:
        return 0
    fase = (pos % sirena.SIRENA_BARRIDO_MS) / sirena.SIRENA_BARRIDO_MS
    triangular = 1 - abs(2 * fase - 1)
    return int(sirena.SIRENA_F_MIN
               + triangular * (sirena.SIRENA_F_MAX - sirena.SIRENA_F_MIN))


sir = sirena.Sirena()
sir.iniciar()
print("[prueba] wail sísmico: {}->{} Hz, ráfaga {} ms + pausa {} ms".format(
    sirena.SIRENA_F_MIN, sirena.SIRENA_F_MAX,
    sirena.SIRENA_ON_MS, sirena.SIRENA_PAUSA_MS))
print("[prueba] sonando 12 s...")

t0 = ticks_ms()
ultimo = 0
en_pausa = False
while ticks_diff(ticks_ms(), t0) < 12000:
    sir.actualizar()
    pos = sir._posicion_ciclo()
    ahora = ticks_ms()
    if pos >= sirena.SIRENA_ON_MS and not en_pausa:
        en_pausa = True
        print("[prueba] -- pausa --")
    elif pos < sirena.SIRENA_ON_MS and en_pausa:
        en_pausa = False
        print("[prueba] >> ráfaga nueva desde grave")
    if not en_pausa and ticks_diff(ahora, ultimo) > 500:
        ultimo = ahora
        print("[prueba]   ~{} Hz".format(frecuencia_aprox(pos)))
    sleep_ms(20)

sir.detener()
print("[prueba] fin")

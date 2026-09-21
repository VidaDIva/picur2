import math
import time

UMBRAL_FRECUENCIA_G = 0.02


def capturar_burst(mpu, num_muestras):
    """Lee num_muestras seguidas lo mas rapido posible.
    Devuelve (muestras, fs) con la tasa de muestreo real medida."""
    inicio = time.ticks_us()
    muestras = []
    for _ in range(num_muestras):
        muestras.append(mpu.leer_muestra())
    fin = time.ticks_us()
    duracion_s = time.ticks_diff(fin, inicio) / 1000000.0
    fs = (num_muestras - 1) / duracion_s if duracion_s > 0 else 0.0
    return muestras, fs


def media_vector(muestras):
    """Media del burst: vector gravedad limpio (la vibracion se cancela)."""
    n = len(muestras)
    x = sum(m[0] for m in muestras) / n
    y = sum(m[1] for m in muestras) / n
    z = sum(m[2] for m in muestras) / n
    return round(x, 4), round(y, 4), round(z, 4)


def calcular_rms(muestras):
    """RMS de vibracion por eje (restando la gravedad) y combinado."""
    n = len(muestras)
    x = [m[0] for m in muestras]
    y = [m[1] for m in muestras]
    z = [m[2] for m in muestras]
    rms_x = _rms_eje(x, n)
    rms_y = _rms_eje(y, n)
    rms_z = _rms_eje(z, n)
    return {
        "aceleracionX": rms_x,
        "aceleracionY": rms_y,
        "aceleracionZ": rms_z,
        "rms": round(math.sqrt(rms_x * rms_x + rms_y * rms_y + rms_z * rms_z), 4),
    }


def _rms_eje(valores, n):
    media = sum(valores) / n
    suma = 0.0
    for v in valores:
        diferencia = v - media
        suma += diferencia * diferencia
    return round(math.sqrt(suma / n), 4)


def _detrend(valores):
    """Resto un ajuste lineal para eliminar el bamboleo de la gravedad
    dentro del burst y dejar solo la vibracion centrada en cero."""
    n = len(valores)
    sx = sum(range(n))
    sy = sum(valores)
    sxx = sum(i * i for i in range(n))
    sxy = sum(i * valores[i] for i in range(n))
    denominador = n * sxx - sx * sx
    if denominador != 0:
        pendiente = (n * sxy - sx * sy) / denominador
        intercepto = (sy - pendiente * sx) / n
        return [valores[i] - (pendiente * i + intercepto) for i in range(n)]
    media = sy / n
    return [v - media for v in valores]


def calcular_frecuencia(muestras, fs):
    """Frecuencia dominante por conteo de cruces por cero del eje mas vibrante."""
    n = len(muestras)
    if n < 2 or fs <= 0:
        return 0.0

    x = [m[0] for m in muestras]
    y = [m[1] for m in muestras]
    z = [m[2] for m in muestras]

    rms_x = _rms_eje(x, n)
    rms_y = _rms_eje(y, n)
    rms_z = _rms_eje(z, n)

    dominante = x
    max_rms = rms_x
    if rms_y > max_rms:
        dominante = y
        max_rms = rms_y
    if rms_z > max_rms:
        dominante = z
        max_rms = rms_z

    ac = _detrend(dominante)
    pico = 0.0
    for v in ac:
        if abs(v) > pico:
            pico = abs(v)

    if pico < UMBRAL_FRECUENCIA_G:
        return 0.0

    cruces = []
    for i in range(1, n):
        anterior = ac[i - 1]
        actual = ac[i]
        if anterior < 0.0 and actual >= 0.0:
            denominador = anterior - actual
            if denominador != 0:
                fraccion = anterior / denominador
            else:
                fraccion = 0.5
            cruces.append((i - 1) + fraccion)

    if len(cruces) < 2:
        return 0.0

    duracion_s = (cruces[-1] - cruces[0]) / fs
    return round((len(cruces) - 1) / duracion_s, 2)


def calcular_inclinacion(ax, ay, az):
    """Pitch y roll combinados (RSS) en grados respecto al nivel."""
    roll = math.atan2(ay, az)
    pitch = math.atan2(-ax, math.sqrt(ay * ay + az * az))
    grados = math.degrees(math.sqrt(roll * roll + pitch * pitch))
    return round(grados, 2)
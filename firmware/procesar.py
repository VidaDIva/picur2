import math
import time

UMBRAL_FRECUENCIA_G = 0.02

# --- Muestreo de ventana ---
MUESTRA_MS = 5              # período entre muestras (~200 Hz)
N_MUESTRAS = 20             # muestras por ventana -> ventana de ~100 ms

# --- Índice de vibración = W_MPU * (std aceleración en mg) + W_PIEZO * (std piezo ADC 12 bits)
W_MPU = 1.0
W_PIEZO = 0.2


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


def calc_media_std(datos):
    """Media y desviación estándar poblacional de una lista."""
    n = len(datos)
    media = sum(datos) / n
    var = sum((x - media) ** 2 for x in datos) / n
    return media, math.sqrt(var)


def leer_ventana(mpu, piezo, sirena=None):
    """Toma N_MUESTRAS lecturas (~100 ms) y resume la ventana:
      media       : (x, y, z) en g (vector gravedad)
      frecuencia  : frecuencia dominante por cruces por cero (Hz)
      rms         : RMS combinado de vibración (g)
      inclinacion : pitch+roll combinados en grados (RSS)
      vib         : índice de vibración combinado (MPU + piezo)
      pitch/roll  : inclinación media de la ventana (grados, firmes)
      pico_g      : pico de aceleración dentro de la ventana (g)
      giro_max    : velocidad angular máxima (°/s)
      limpia      : False si la sirena contaminó la ventana
    """
    limpia = sirena.ventana_limpia() if sirena else True

    mags, pz, muestras = [], [], []
    sax = say = saz = 0.0
    giro_max = 0.0
    t_sig = time.ticks_ms()

    for _ in range(N_MUESTRAS):
        ax, ay, az, gx, gy, gz = mpu.leer()
        muestras.append((ax, ay, az))
        mags.append(math.sqrt(ax * ax + ay * ay + az * az))
        pz.append(piezo.leer_muestra())
        sax += ax
        say += ay
        saz += az
        g = math.sqrt(gx * gx + gy * gy + gz * gz)
        if g > giro_max:
            giro_max = g

        if sirena:
            sirena.actualizar()                           # mantiene la sirena sonando

        t_sig = time.ticks_add(t_sig, MUESTRA_MS)         # cadencia estable
        espera = time.ticks_diff(t_sig, time.ticks_ms())
        if espera > 0:
            time.sleep_ms(espera)

    if sirena and not sirena.ventana_limpia():
        limpia = False

    mag_media, mag_std = calc_media_std(mags)
    pico_g = max(abs(m - mag_media) for m in mags)
    _pz_media, pz_std = calc_media_std(pz)

    vib = W_MPU * (mag_std * 1000.0) + W_PIEZO * pz_std   # mag_std en mg

    media_x, media_y, media_z = media_vector(muestras)
    rms = calcular_rms(muestras)["rms"]
    frecuencia = calcular_frecuencia(muestras, 1000.0 / MUESTRA_MS)
    inclinacion = calcular_inclinacion(media_x, media_y, media_z)

    ax_m = sax / N_MUESTRAS
    ay_m = say / N_MUESTRAS
    az_m = saz / N_MUESTRAS
    pitch = math.degrees(math.atan2(ax_m, math.sqrt(ay_m * ay_m + az_m * az_m)))
    roll = math.degrees(math.atan2(ay_m, az_m))

    return {
        "media": (media_x, media_y, media_z),
        "frecuencia": frecuencia,
        "rms": rms,
        "inclinacion": inclinacion,
        "vib": vib,
        "pitch": pitch,
        "roll": roll,
        "pico_g": pico_g,
        "giro_max": giro_max,
        "limpia": limpia,
    }
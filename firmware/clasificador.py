# =============================================================================
#  clasificador.py  -  Aprendizaje de la línea base y detección de anomalías
#
#  Portado del main.py de referencia (Thonny): el Pico "aprende" cómo vibra
#  la vía con tráfico normal y luego clasifica cada ventana contra umbrales
#  dinámicos. El estado resultante se envía a Spring Boot como fuente de verdad.
# =============================================================================

from math import sqrt

import procesar

# --- Estados ---
NORMAL, ANOMALIA, PELIGRO = 0, 1, 2
NOMBRE_ESTADO = ("NORMAL", "ANOMALIA", "PELIGRO")

# --- Calibración / aprendizaje ---
CAL_VENTANAS = 100      # 100 ventanas x 100 ms = ~10 s de aprendizaje
STD_MIN = 0.5           # piso de desviación estándar

# --- Umbral dinámico ---
K_TRAFICO = 1.5         # media + 1.5*std -> mensaje "tráfico pesado"
K_NORMAL = 3.0          # media + 3*std   -> límite del tráfico normal
K_CRITICO = 6.0         # media + 6*std   -> vibración muy fuera de lo aprendido

# --- Persistencia (en ventanas de 100 ms) ---
N_ALERTA = 5            # 0.5 s sobre el umbral normal -> ANOMALIA
N_CRITICO = 8           # 0.8 s sobre el umbral crítico -> PELIGRO
N_SOSTENIDO = 40        # 4 s seguidos sobre umbral normal -> PELIGRO
N_INCL = 5              # 0.5 s con inclinación crítica -> PELIGRO
N_RECUP = 10            # ventanas limpias y normales para salir de PELIGRO

# --- Inclinación (grados respecto a la base) y cambios bruscos ---
TILT_ALERTA = 1.5       # grados -> ANOMALIA
TILT_CRITICO = 3.0      # grados -> PELIGRO
PICO_BRUSCO_G = 1.0     # pico de aceleración en la ventana (g) -> PELIGRO
GIRO_BRUSCO_DPS = 80.0  # velocidad angular máxima (°/s) -> PELIGRO

# --- Aprendizaje continuo de la línea base (solo en estado NORMAL) ---
ADAPTAR_BASE = True
ALFA_ADAPT = 0.002


def calcular_umbrales(base):
    """Recalcula los umbrales a partir de media y desviación."""
    base["std"] = max(base["std"], STD_MIN)
    base["umbral_trafico"] = base["media"] + K_TRAFICO * base["std"]
    base["umbral_normal"] = base["media"] + K_NORMAL * base["std"]
    base["umbral_critico"] = base["media"] + K_CRITICO * base["std"]


def calibrar_linea_base(mpu, piezo, al_avance=None):
    """Fase de aprendizaje: ~10 s con tráfico normal.

    al_avance(i) se invoca en cada ventana (para el parpadeo del LED).
    """
    print("[calib] Entrenando patron normal... (mantenga el trafico habitual)")
    vibs, pitches, rolls = [], [], []
    fallos = 0

    while len(vibs) < CAL_VENTANAS:
        try:
            w = procesar.leer_ventana(mpu, piezo)
        except OSError as e:
            # Lectura intermitente del bus: se repite sin contar la ventana
            fallos += 1
            if fallos > 30:
                raise
            print("[calib] Lectura fallida ({}), reintentando".format(e))
            continue
        vibs.append(w["vib"])
        pitches.append(w["pitch"])
        rolls.append(w["roll"])
        if al_avance:
            al_avance(len(vibs) - 1)
        if len(vibs) % 20 == 0:
            print("[calib] {}%".format(len(vibs) * 100 // CAL_VENTANAS))

    media, std = procesar.calc_media_std(vibs)
    base = {
        "media": media,
        "std": std,
        "var": std * std,
        "pitch0": sum(pitches) / len(pitches),
        "roll0": sum(rolls) / len(rolls),
    }
    calcular_umbrales(base)

    print("[calib] Linea base aprendida:")
    print("[calib]   media vibracion  = {:.2f}".format(base["media"]))
    print("[calib]   desviacion std   = {:.2f}".format(base["std"]))
    print("[calib]   umbral normal    = {:.2f}".format(base["umbral_normal"]))
    print("[calib]   umbral critico   = {:.2f}".format(base["umbral_critico"]))
    print("[calib]   inclinacion base = pitch {:.2f}, roll {:.2f}".format(
        base["pitch0"], base["roll0"]))
    return base


def adaptar_base(base, vib):
    """Aprendizaje continuo y lento de la línea base (solo NORMAL)."""
    if vib > base["umbral_normal"]:
        return                                             # nunca aprender anomalías
    delta = vib - base["media"]
    base["media"] += ALFA_ADAPT * delta
    base["var"] = (1 - ALFA_ADAPT) * (base["var"] + ALFA_ADAPT * delta * delta)
    base["std"] = sqrt(base["var"])
    calcular_umbrales(base)


def nuevo_contexto():
    """Contadores de persistencia del detector."""
    return {
        "c_sobre": 0,
        "c_critico": 0,
        "c_incl_alerta": 0,
        "c_incl_critica": 0,
        "c_recup": 0,
        "trafico_prev": False,
    }


def _acumular(contador, condicion):
    """Contador con 'fuga': sube 1 si se cumple, baja 1 si no (tolera ruido)."""
    if condicion:
        return contador + 1
    return contador - 1 if contador > 0 else 0


def clasificar(w, base, ctx):
    """Devuelve (estado, motivo) para una ventana limpia."""
    d_incl = max(abs(w["pitch"] - base["pitch0"]), abs(w["roll"] - base["roll0"]))

    ctx["c_sobre"] = _acumular(ctx["c_sobre"], w["vib"] > base["umbral_normal"])
    ctx["c_critico"] = _acumular(ctx["c_critico"], w["vib"] > base["umbral_critico"])
    ctx["c_incl_alerta"] = _acumular(ctx["c_incl_alerta"], d_incl > TILT_ALERTA)
    ctx["c_incl_critica"] = _acumular(ctx["c_incl_critica"], d_incl > TILT_CRITICO)

    # --- PELIGRO ---
    if w["pico_g"] > PICO_BRUSCO_G or w["giro_max"] > GIRO_BRUSCO_DPS:
        return PELIGRO, "cambio brusco de aceleracion/giro"
    if ctx["c_incl_critica"] >= N_INCL:
        return PELIGRO, "cambio de inclinacion de {:.1f} deg".format(d_incl)
    if ctx["c_critico"] >= N_CRITICO:
        return PELIGRO, "vibracion muy superior al patron aprendido"
    if ctx["c_sobre"] >= N_SOSTENIDO:
        return PELIGRO, "patron de vibracion anomalo sostenido"

    # --- ANOMALIA ---
    if ctx["c_sobre"] >= N_ALERTA:
        return ANOMALIA, "vibracion sobre el umbral normal"
    if ctx["c_incl_alerta"] >= N_ALERTA:
        return ANOMALIA, "inclinacion fuera de lo habitual ({:.1f} deg)".format(
            d_incl)

    return NORMAL, ""

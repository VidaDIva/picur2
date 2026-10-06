from machine import Pin, PWM
from time import ticks_ms, ticks_diff, sleep_ms

# --- Sirena (PWM -> PAM8403 en GP16) ---
# Wail estilo alerta sísmica: sube de grave a agudo en 2 s, baja 0.5 s
# y se corta; tras la pausa vuelve desde grave.
SIRENA_F_MIN = 380          # Hz
SIRENA_F_MAX = 1500         # Hz
SIRENA_BARRIDO_MS = 4000    # ciclo completo del barrido: 2 s subida + 2 s bajada
SIRENA_DUTY = 32768         # 50 % (onda cuadrada)
SIRENA_ON_MS = 2500         # tiempo sonando (cubre la subida completa)
SIRENA_PAUSA_MS = 1000      # pausa silenciosa entre ráfagas
SIRENA_GUARDA_MS = 150      # margen tras apagar antes de volver a evaluar


class Sirena:
    """Sirena oscilante por PWM, NO bloqueante.

    Suena en ráfagas (SIRENA_ON_MS) separadas por una pausa silenciosa
    (SIRENA_PAUSA_MS). Las mediciones tomadas mientras suena, o justo
    después, se descartan (ventana_limpia) para que la propia bocina
    no re-dispare la alarma.
    """

    def __init__(self, pin_num=16):
        self.pwm = PWM(Pin(pin_num))
        self.pwm.freq(SIRENA_F_MIN)
        self.pwm.duty_u16(0)
        self.activa = False
        self.t0 = 0

    def iniciar(self):
        if not self.activa:
            self.activa = True
            self.t0 = ticks_ms()

    def detener(self):
        self.activa = False
        self.pwm.duty_u16(0)

    def _posicion_ciclo(self):
        return ticks_diff(ticks_ms(), self.t0) % (SIRENA_ON_MS + SIRENA_PAUSA_MS)

    def actualizar(self):
        """Llamar con frecuencia (cada muestra): ajusta la frecuencia."""
        if not self.activa:
            return
        pos = self._posicion_ciclo()
        if pos < SIRENA_ON_MS:
            fase = (pos % SIRENA_BARRIDO_MS) / SIRENA_BARRIDO_MS
            triangular = 1 - abs(2 * fase - 1)            # 0 -> 1 -> 0
            f = SIRENA_F_MIN + triangular * (SIRENA_F_MAX - SIRENA_F_MIN)
            self.pwm.freq(int(f))
            self.pwm.duty_u16(SIRENA_DUTY)
        else:
            self.pwm.duty_u16(0)                          # pausa silenciosa

    def ventana_limpia(self):
        """True si no hay sonido ni eco reciente de la bocina."""
        if not self.activa:
            return True
        return self._posicion_ciclo() >= SIRENA_ON_MS + SIRENA_GUARDA_MS

    def pitido(self, freq=1500, ms=120):
        """Pitido corto de confirmación (bloqueante, solo para arranque)."""
        self.pwm.freq(freq)
        self.pwm.duty_u16(SIRENA_DUTY)
        sleep_ms(ms)
        self.pwm.duty_u16(0)

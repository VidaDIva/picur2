from machine import Pin
from time import sleep_ms

from clasificador import NORMAL, ANOMALIA, PELIGRO


class Semaforo:
    """Semáforo de 3 LEDs: verde=GP13, amarillo=GP14, rojo=GP15."""

    def __init__(self, pin_verde=13, pin_amarillo=14, pin_rojo=15):
        self._verde = Pin(pin_verde, Pin.OUT)
        self._amarillo = Pin(pin_amarillo, Pin.OUT)
        self._rojo = Pin(pin_rojo, Pin.OUT)
        self.apagar()

    def apagar(self):
        self._verde.value(0)
        self._amarillo.value(0)
        self._rojo.value(0)

    def mostrar(self, estado):
        """Enciende solo el LED correspondiente al estado."""
        self._verde.value(1 if estado == NORMAL else 0)
        self._amarillo.value(1 if estado == ANOMALIA else 0)
        self._rojo.value(1 if estado == PELIGRO else 0)

    def amarillo(self, encendido):
        self._amarillo.value(1 if encendido else 0)

    def parpadeo_aprendizaje(self, ventana):
        """Patrón de parpadeo durante la calibración (aprende = amarillo)."""
        self._amarillo.value((ventana // 5) % 2)

    def autotest(self):
        """Secuencia de arranque: cada LED 250 ms."""
        for led in (self._verde, self._amarillo, self._rojo):
            led.value(1)
            sleep_ms(250)
            led.value(0)

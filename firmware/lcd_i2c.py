# =============================================================================
#  lcd_i2c.py  -  Pantalla LCD 16x2 con backpack I2C (PCF8574)
#
#  Se conecta al MISMO bus I2C0 que el MPU-6050 (SDA=GP4, SCL=GP5).
#  Direccion tipica: 0x27 (PCF8574) o 0x3F (PCF8574A).
#
#  Mapa de bits del backpack (estandar "LCM1602 IIC"):
#    P0=RS  P1=RW  P2=E  P3=Backlight  P4..P7=D4..D7
#
#  No crea su propio bus: recibe el SoftI2C del MPU para compartirlo.
# =============================================================================

import time


class LcdI2C:
    RS = 0x01
    RW = 0x02
    E = 0x04
    BACKLIGHT = 0x08

    _DIRECCIONES_FILA = (0x00, 0x40, 0x14, 0x54)

    def __init__(self, i2c, direccion=0x27, columnas=16, filas=2):
        self.i2c = i2c
        self.dir = direccion
        self.columnas = columnas
        self.filas = filas
        self._iniciar()

    # ---------- bajo nivel ----------
    def _enviar(self, valor):
        self.i2c.writeto(self.dir, bytes([(valor & 0xFF) | self.BACKLIGHT]))

    def _pulso(self, valor):
        self._enviar(valor | self.E)
        time.sleep_us(40)
        self._enviar(valor & ~self.E)
        time.sleep_us(40)

    def _nibble(self, valor, modo):
        self._pulso(((valor & 0x0F) << 4) | modo)

    def _byte(self, valor, modo):
        self._nibble((valor >> 4) & 0x0F, modo)
        self._nibble(valor & 0x0F, modo)

    # ---------- API ----------
    def comando(self, cmd):
        self._byte(cmd, 0)

    def _dato(self, valor):
        self._byte(valor, self.RS)

    def _iniciar(self):
        time.sleep_ms(50)
        self._nibble(0x03, 0)
        time.sleep_ms(5)
        self._nibble(0x03, 0)
        time.sleep_ms(5)
        self._nibble(0x03, 0)
        time.sleep_ms(5)
        self._nibble(0x02, 0)          # pone el controlador en modo 4 bits
        self.comando(0x28)             # 4 bits, 2 filas, fuente 5x8
        self.comando(0x0C)             # display ON, cursor OFF, sin parpadeo
        self.comando(0x06)             # auto-incremento, sin desplazamiento
        self.limpiar()

    def limpiar(self):
        self.comando(0x01)
        time.sleep_ms(2)

    def mover(self, columna, fila):
        base = self._DIRECCIONES_FILA[fila] if fila < len(
            self._DIRECCIONES_FILA) else self._DIRECCIONES_FILA[0]
        self.comando(0x80 | (base + columna))

    def escribir(self, texto):
        for caracter in texto:
            self._dato(ord(caracter))

    def _pad(self, texto):
        """Recorta/rellena a exactamente `columnas` caracteres (sin ljust,
        que MicroPython no incluye)."""
        texto = str(texto)[:self.columnas]
        return texto + " " * (self.columnas - len(texto))

    def mostrar(self, linea1="", linea2=""):
        self.mover(0, 0)
        self.escribir(self._pad(linea1))
        if self.filas > 1:
            self.mover(0, 1)
            self.escribir(self._pad(linea2))

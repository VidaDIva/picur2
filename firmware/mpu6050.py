import machine


class MPU6050:
    """Driver minimo del acelerometro MPU-6050 (I2C)."""

    def __init__(self, scl_pin=5, sda_pin=4, direccion=0x68):
        self.i2c = machine.I2C(
            0,
            scl=machine.Pin(scl_pin),
            sda=machine.Pin(sda_pin),
            freq=400000,
        )
        self.dir = direccion
        self.factor = 16384.0  # LSB/g en escala +-2g
        self._despertar()

    def _despertar(self):
        self.i2c.writeto_mem(self.dir, 0x6B, b"\x00")

    def leer_muestra(self):
        """Lee una muestra (ax, ay, az) en g."""
        datos = self.i2c.readfrom_mem(self.dir, 0x3B, 6)
        ax = (datos[0] << 8) | datos[1]
        ay = (datos[2] << 8) | datos[3]
        az = (datos[4] << 8) | datos[5]
        return (self._a_g(ax), self._a_g(ay), self._a_g(az))

    def leer_aceleracion(self):
        """Lee X/Y/Z en unidades de g. Ej: {'aceleracionX': 0.02}"""
        ax, ay, az = self.leer_muestra()
        return {
            "aceleracionX": ax,
            "aceleracionY": ay,
            "aceleracionZ": az,
        }

    def _a_g(self, valor):
        if valor >= 0x8000:
            valor -= 0x10000
        return round(valor / self.factor, 4)
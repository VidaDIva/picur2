import time
import machine
from struct import unpack

# Escalas del sensor (configuradas en _configurar)
FACTOR_ACC = 16384.0    # LSB/g en escala +-2g
FACTOR_GYRO = 65.5      # LSB/(deg/s) en escala +-500 deg/s


class MPU6050:
    """Driver del acelerometro/giroscopio MPU-6050 (I2C0, SDA=GP4, SCL=GP5)."""

    def __init__(self, scl_pin=5, sda_pin=4, direccion=0x68):
        # SoftI2C y no I2C hardware: con el cableado del prototipo el
        # controlador por hardware devuelve EIO en cada lectura de datos
        # (aunque el scan ACKea); SoftI2C tolera el timing del bus.
        self.i2c = machine.SoftI2C(
            scl=machine.Pin(scl_pin),
            sda=machine.Pin(sda_pin),
            freq=400000,
        )
        self.dir = direccion
        if self.dir not in self.i2c.scan():
            raise OSError(
                "MPU6050 no encontrado: revise SDA=GP4, SCL=GP5 y 3.3V")
        self.factor = FACTOR_ACC
        self.factor_gyro = FACTOR_GYRO
        self._configurar()

    def _configurar(self):
        self.i2c.writeto_mem(self.dir, 0x6B, b"\x00")  # PWR_MGMT_1: despertar
        time.sleep_ms(100)
        self.i2c.writeto_mem(self.dir, 0x1A, b"\x02")  # DLPF ~94 Hz
        self.i2c.writeto_mem(self.dir, 0x1B, b"\x08")  # Giroscopio +-500 deg/s
        self.i2c.writeto_mem(self.dir, 0x1C, b"\x00")  # Acelerometro +-2g

    def leer(self):
        """Lee 14 bytes: (ax, ay, az) en g y (gx, gy, gz) en deg/s.

        Reintenta ante errores intermitentes del bus (Errno 19 EIO, típicos
        de cableado de prototipo): 3 intentos antes de propagar el error.
        """
        datos = None
        ultimo_error = None
        for _ in range(3):
            try:
                datos = self.i2c.readfrom_mem(self.dir, 0x3B, 14)
                break
            except OSError as e:
                ultimo_error = e
                time.sleep_ms(2)
        if datos is None:
            raise ultimo_error

        ax, ay, az, _temp, gx, gy, gz = unpack(">hhhhhhh", datos)
        return (
            self._a_g(ax),
            self._a_g(ay),
            self._a_g(az),
            self._a_dps(gx),
            self._a_dps(gy),
            self._a_dps(gz),
        )

    def leer_muestra(self):
        """Lee una muestra (ax, ay, az) en g."""
        ax, ay, az, _gx, _gy, _gz = self.leer()
        return (ax, ay, az)

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

    def _a_dps(self, valor):
        if valor >= 0x8000:
            valor -= 0x10000
        return round(valor / self.factor_gyro, 2)
